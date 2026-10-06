package org.knowm.xchange.bitfinex.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.bitfinex.BitfinexExchange;
import org.knowm.xchange.bitfinex.v1.BitfinexOrderType;
import org.knowm.xchange.client.ratelimit.RateLimitContext;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.exceptions.RateLimitTerminatedException;

/**
 * Service-level proof, against a loopback server, that the universal rate limiter is the only
 * admission owner of the Bitfinex services: one admission per wire attempt, a rejected read is
 * replayed with a fresh admission, a rejected order placement is surfaced and never replayed, and
 * no resilience4j rate limiter is involved.
 */
class BitfinexRateLimitServiceTest {

  private static final String WALLETS = "POST /v2/auth/r/wallets";
  private static final String NEW_ORDER = "POST /v1/order/new";
  private static final String TICKER = "GET /v1/pubticker/btcusd";

  private Stub stub;
  private ExchangeSpecification specification;
  private BitfinexExchange exchange;

  @BeforeEach
  void setUp() throws IOException {
    stub = new Stub();
    specification = new BitfinexExchange().getDefaultExchangeSpecification();
    specification.setSslUri("http://127.0.0.1:" + stub.port());
    specification.setHost("127.0.0.1");
    specification.setApiKey("key");
    specification.setSecretKey("secret");
    specification.setShouldLoadRemoteMetaData(false);
    exchange = new BitfinexExchange();
    exchange.applySpecification(specification);
  }

  @AfterEach
  void tearDown() {
    RateLimitContext context = specification.getResilience().getRateLimitContext();
    if (context != null) {
      context.close();
    }
    stub.close();
  }

  @Test
  void everyWireAttemptIsAdmittedOnceAndNoResilience4jLimiterIsUsed() throws IOException {
    stub.respond(TICKER, 200, "{\"last_price\":\"1\"}", 0);
    stub.respond(WALLETS, 200, "[]", 0);
    BitfinexMarketDataServiceRaw market =
        (BitfinexMarketDataServiceRaw) exchange.getMarketDataService();
    BitfinexAccountServiceRaw account = (BitfinexAccountServiceRaw) exchange.getAccountService();

    market.getBitfinexTicker("btcusd");
    assertThat(account.getWallets()).isEmpty();

    RateLimitContext context = specification.getResilience().getRateLimitContext();
    assertThat(context.diagnostics().getAdmissions()).isEqualTo(stub.totalArrivals()).isEqualTo(2);
    assertThat(exchange.getResilienceRegistries().rateLimiters().getAllRateLimiters()).isEmpty();
  }

  @Test
  void rejectedReadIsReplayedWithAFreshAdmission() throws IOException {
    stub.respond(WALLETS, 429, "{\"error\":\"ERR_RATE_LIMIT\"}", 1);
    stub.respond(WALLETS, 200, "[]", 0);
    BitfinexAccountServiceRaw account = (BitfinexAccountServiceRaw) exchange.getAccountService();

    assertThat(account.getWallets()).isEmpty();

    assertThat(stub.arrivals(WALLETS))
        .as("one rejected and one replayed wire attempt")
        .isEqualTo(2);
    RateLimitContext context = specification.getResilience().getRateLimitContext();
    assertThat(context.diagnostics().getAdmissions()).isEqualTo(stub.totalArrivals());
    assertThat(context.diagnostics().getRatePressureEvents()).isEqualTo(1);
  }

  @Test
  void rejectedOrderPlacementIsSurfacedAndNeverReplayed() {
    stub.respond(NEW_ORDER, 429, "{\"error\":\"ERR_RATE_LIMIT\"}", 1);
    BitfinexTradeServiceRaw trade = (BitfinexTradeServiceRaw) exchange.getTradeService();
    LimitOrder order =
        new LimitOrder.Builder(OrderType.BID, CurrencyPair.BTC_USD)
            .originalAmount(BigDecimal.ONE)
            .limitPrice(BigDecimal.TEN)
            .build();

    assertThatThrownBy(() -> trade.placeBitfinexLimitOrder(order, BitfinexOrderType.LIMIT))
        .isInstanceOfSatisfying(
            RateLimitTerminatedException.class,
            terminated -> {
              assertThat(terminated.getDispatch())
                  .isEqualTo(RateLimitTerminatedException.Dispatch.REJECTED);
              assertThat(terminated.getReason())
                  .isEqualTo(RateLimitTerminatedException.Reason.REMOTE_PRESSURE_EXHAUSTED);
            });

    assertThat(stub.arrivals(NEW_ORDER)).as("the order was sent exactly once").isEqualTo(1);
  }

  /** Loopback server answering scripted responses per method and path. */
  private static final class Stub implements AutoCloseable {

    private record Scripted(int status, String body, int count) {}

    private final HttpServer server;
    private final ExecutorService handlers = Executors.newFixedThreadPool(4);
    private final Object lock = new Object();
    private final List<String> arrivalLog = new ArrayList<>();
    private final Map<String, List<Scripted>> script = new HashMap<>();

    Stub() throws IOException {
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.setExecutor(handlers);
      server.createContext("/", this::handle);
      server.start();
    }

    int port() {
      return server.getAddress().getPort();
    }

    /**
     * Scripts a response; a {@code count} of 0 repeats it forever, otherwise it answers that many
     * requests before the next scripted response takes over.
     */
    void respond(String methodAndPath, int status, String body, int count) {
      synchronized (lock) {
        script
            .computeIfAbsent(methodAndPath, key -> new ArrayList<>())
            .add(new Scripted(status, body, count));
      }
    }

    int arrivals(String methodAndPath) {
      synchronized (lock) {
        return (int) arrivalLog.stream().filter(methodAndPath::equals).count();
      }
    }

    int totalArrivals() {
      synchronized (lock) {
        return arrivalLog.size();
      }
    }

    private void handle(HttpExchange http) throws IOException {
      String key = http.getRequestMethod() + " " + http.getRequestURI().getPath();
      http.getRequestBody().readAllBytes();
      Scripted answer;
      synchronized (lock) {
        arrivalLog.add(key);
        List<Scripted> queue = script.get(key);
        if (queue == null || queue.isEmpty()) {
          answer = new Scripted(404, "{}", 0);
        } else {
          answer = queue.get(0);
          if (answer.count() == 1) {
            queue.remove(0);
          } else if (answer.count() > 1) {
            queue.set(0, new Scripted(answer.status(), answer.body(), answer.count() - 1));
          }
        }
      }
      if (answer.status() == 429) {
        http.getResponseHeaders().add("Retry-After", "1");
      }
      byte[] bytes = answer.body().getBytes(StandardCharsets.UTF_8);
      http.getResponseHeaders().add("Content-Type", "application/json");
      http.sendResponseHeaders(answer.status(), bytes.length);
      try (OutputStream out = http.getResponseBody()) {
        out.write(bytes);
      }
    }

    @Override
    public void close() {
      server.stop(0);
      handlers.shutdownNow();
    }
  }
}
