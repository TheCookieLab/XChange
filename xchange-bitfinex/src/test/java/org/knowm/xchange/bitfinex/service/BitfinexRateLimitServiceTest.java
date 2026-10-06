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
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.bitfinex.BitfinexExchange;
import org.knowm.xchange.bitfinex.BitfinexRateLimitPolicy;
import org.knowm.xchange.bitfinex.v1.BitfinexOrderType;
import org.knowm.xchange.client.ratelimit.RateLimitBudget;
import org.knowm.xchange.client.ratelimit.RateLimitBudget.ScopeKind;
import org.knowm.xchange.client.ratelimit.RateLimitContext;
import org.knowm.xchange.client.ratelimit.RateLimitPolicy;
import org.knowm.xchange.client.ratelimit.RateLimitPriority;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.MarketOrder;
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
  private static final String BALANCES = "POST /v1/balances";
  private static final String TICKER = "GET /v1/pubticker/btcusd";
  private static final Pattern NONCE = Pattern.compile("\"nonce\":\"(\\d+)\"");

  private Stub stub;
  private ExchangeSpecification specification;
  private BitfinexExchange exchange;

  @BeforeEach
  void setUp() throws IOException {
    stub = new Stub();
    start(null);
  }

  /** (Re)creates the exchange against the stub, optionally with a replacement rate policy. */
  private void start(RateLimitPolicy policy) {
    closeContext();
    specification = new BitfinexExchange().getDefaultExchangeSpecification();
    specification.setSslUri("http://127.0.0.1:" + stub.port());
    specification.setHost("127.0.0.1");
    specification.setApiKey("key");
    specification.setSecretKey("secret");
    specification.setShouldLoadRemoteMetaData(false);
    if (policy != null) {
      specification.getResilience().setRateLimitPolicy(policy);
    }
    exchange = new BitfinexExchange();
    exchange.applySpecification(specification);
  }

  private void closeContext() {
    if (specification == null) {
      return;
    }
    RateLimitContext context = specification.getResilience().getRateLimitContext();
    if (context != null) {
      context.close();
    }
  }

  @AfterEach
  void tearDown() {
    closeContext();
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

  @Test
  void queuedV1ReadSendsANonceCreatedAfterAdmission() throws Exception {
    stub.respond(BALANCES, 200, "[]", 0);
    startWithSingleV1Token();
    BitfinexAccountServiceRaw account = (BitfinexAccountServiceRaw) exchange.getAccountService();

    assertQueuedV1RequestIsStampedAfterAdmission(BALANCES, account::getBitfinexAccountInfo);
  }

  @Test
  void queuedV1OrderPlacementSendsANonceCreatedAfterAdmission() throws Exception {
    stub.respond(NEW_ORDER, 200, "{}", 0);
    startWithSingleV1Token();
    BitfinexTradeServiceRaw trade = (BitfinexTradeServiceRaw) exchange.getTradeService();
    MarketOrder order =
        new MarketOrder.Builder(OrderType.BID, CurrencyPair.BTC_USD)
            .originalAmount(BigDecimal.ONE)
            .build();

    assertQueuedV1RequestIsStampedAfterAdmission(
        NEW_ORDER, () -> trade.placeBitfinexMarketOrder(order, BitfinexOrderType.MARKET));
  }

  /**
   * Restarts the exchange with a v1 authenticated budget of one request per two seconds, so that a
   * second v1 call parks in admission.
   */
  private void startWithSingleV1Token() {
    start(
        BitfinexRateLimitPolicy.defaultPolicy()
            .withBudget(
                RateLimitBudget.rollingWindow(
                    "bitfinex.v1.auth", ScopeKind.EGRESS, 1, Duration.ofSeconds(2))));
  }

  /**
   * Sends one v1 call that takes the only token, parks a second identical call in admission,
   * consumes a newer nonce from the exchange nonce factory (what a concurrent v2 call does) while
   * the second call waits, and proves that the second call still sends a nonce newer than that one,
   * i.e. that the nonce is created when the attempt is admitted, and that the payload and signature
   * headers cover exactly the body that was sent.
   */
  private void assertQueuedV1RequestIsStampedAfterAdmission(String key, Callable<?> call)
      throws Exception {
    RateLimitContext context = specification.getResilience().getRateLimitContext();
    ExecutorService caller = Executors.newSingleThreadExecutor();
    try {
      call.call();
      Future<?> queued = caller.submit(call);
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
      while (context.diagnostics().getPending().getOrDefault(RateLimitPriority.EXECUTION, 0) < 1) {
        assertThat(System.nanoTime())
            .as("second call never parked in admission")
            .isLessThan(deadline);
        Thread.sleep(5);
      }
      long consumedMeanwhile = exchange.getNonceFactory().createValue();

      queued.get(10, TimeUnit.SECONDS);

      List<Stub.Captured> sent = stub.requests(key);
      assertThat(sent).hasSize(2);
      long first = nonceOf(sent.get(0));
      long second = nonceOf(sent.get(1));
      assertThat(second).as("queued request nonce").isGreaterThan(consumedMeanwhile);
      assertThat(consumedMeanwhile).isGreaterThan(first);
      for (Stub.Captured request : sent) {
        assertThat(request.payload())
            .isEqualTo(
                Base64.getEncoder()
                    .encodeToString(request.body().getBytes(StandardCharsets.UTF_8)));
        assertThat(request.signature()).isEqualTo(hmacSha384Hex("secret", request.payload()));
      }
    } finally {
      caller.shutdownNow();
    }
  }

  private static long nonceOf(Stub.Captured request) {
    Matcher matcher = NONCE.matcher(request.body());
    assertThat(matcher.find()).as("nonce in %s", request.body()).isTrue();
    return Long.parseLong(matcher.group(1));
  }

  private static String hmacSha384Hex(String secret, String message) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA384");
    mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA384"));
    return HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
  }

  /** Loopback server answering scripted responses per method and path. */
  private static final class Stub implements AutoCloseable {

    private record Scripted(int status, String body, int count) {}

    private record Captured(String body, String payload, String signature) {}

    private final HttpServer server;
    private final ExecutorService handlers = Executors.newFixedThreadPool(4);
    private final Object lock = new Object();
    private final List<String> arrivalLog = new ArrayList<>();
    private final Map<String, List<Captured>> captured = new HashMap<>();
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

    List<Captured> requests(String methodAndPath) {
      synchronized (lock) {
        return new ArrayList<>(captured.getOrDefault(methodAndPath, List.of()));
      }
    }

    private void handle(HttpExchange http) throws IOException {
      String key = http.getRequestMethod() + " " + http.getRequestURI().getPath();
      String body = new String(http.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
      Captured request =
          new Captured(
              body,
              http.getRequestHeaders().getFirst("X-BFX-PAYLOAD"),
              http.getRequestHeaders().getFirst("X-BFX-SIGNATURE"));
      Scripted answer;
      synchronized (lock) {
        arrivalLog.add(key);
        captured.computeIfAbsent(key, k -> new ArrayList<>()).add(request);
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
