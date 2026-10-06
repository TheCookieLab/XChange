package org.knowm.xchange.coinsph.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
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
import org.knowm.xchange.client.ratelimit.RateLimitContext;
import org.knowm.xchange.coinsph.CoinsphExchange;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.exceptions.RateLimitTerminatedException;

/**
 * Service-level proof of the universal rate limiter on the Coins.ph path: one admission per wire
 * attempt, no resilience4j rate limiter anywhere, replay of rejected reads after {@code
 * Retry-After}, and no blind replay of a rejected order placement.
 *
 * <p>Runs against a dynamic-port loopback server; nothing leaves the machine.
 */
class CoinsphRateLimitServiceTest {

  private static final String TIME = "GET /openapi/v1/time";
  private static final String ACCOUNT = "GET /openapi/v1/account";
  private static final String TRADES = "GET /openapi/v1/trades";
  private static final String ORDER = "POST /openapi/v1/order";

  private Stub stub;
  private ExchangeSpecification specification;
  private CoinsphExchange exchange;

  /** Same exchange, but the loopback server replaces the production host. */
  private static final class LoopbackCoinsphExchange extends CoinsphExchange {
    private final String uri;

    LoopbackCoinsphExchange(String uri) {
      this.uri = uri;
    }

    @Override
    protected void concludeHostParams(ExchangeSpecification exchangeSpecification) {
      exchangeSpecification.setSslUri(uri);
    }
  }

  @BeforeEach
  void setUp() throws IOException {
    stub = new Stub();
    specification = new CoinsphExchange().getDefaultExchangeSpecification();
    specification.setShouldLoadRemoteMetaData(false);
    specification.setApiKey("test-api-key");
    specification.setSecretKey("test-secret-key");
    exchange = new LoopbackCoinsphExchange("http://127.0.0.1:" + stub.port());
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

  /** Every wire attempt, including the startup time sync, is admitted exactly once. */
  @Test
  void oneAdmissionPerWireAttemptAndNoResilience4jRateLimiter() throws Exception {
    CoinsphMarketDataServiceRaw marketData =
        (CoinsphMarketDataServiceRaw) exchange.getMarketDataService();
    CoinsphAccountServiceRaw account = (CoinsphAccountServiceRaw) exchange.getAccountService();

    assertEquals(1L, admissions(), "startup time sync is one admitted wire attempt");
    assertEquals(1, stub.arrivals(TIME));

    assertEquals(0, marketData.getCoinsphTrades(CurrencyPair.BTC_USDT, 5).size());
    assertEquals(0, account.getCoinsphAccount().getBalances().size());

    assertEquals(1, stub.arrivals(TRADES));
    assertEquals(1, stub.arrivals(ACCOUNT));
    assertEquals((long) stub.totalArrivals(), admissions(), "admissions equal wire attempts");
    assertTrue(
        exchange.getResilienceRegistries().rateLimiters().getAllRateLimiters().isEmpty(),
        "no resilience4j rate limiter was created on the service path");
  }

  /** A 429 on a read is cooled down per Retry-After and replayed with a fresh admission. */
  @Test
  void rejectedReadIsReplayedWithFreshAdmissionAfterRetryAfter() throws Exception {
    CoinsphAccountServiceRaw account = (CoinsphAccountServiceRaw) exchange.getAccountService();
    long before = admissions();
    stub.script(ACCOUNT, new Reply(429, "1", "{\"code\":-1003,\"msg\":\"Too many requests\"}"));

    long start = System.nanoTime();
    assertEquals(0, account.getCoinsphAccount().getBalances().size());
    Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

    assertEquals(2, stub.arrivals(ACCOUNT), "the rejected read was replayed once");
    assertEquals(
        before + 2,
        admissions(),
        "the rejected attempt and the replay were each admitted exactly once");
    assertTrue(elapsed.compareTo(Duration.ofMillis(900)) >= 0, "Retry-After honoured: " + elapsed);
    assertTrue(
        exchange.getResilienceRegistries().rateLimiters().getAllRateLimiters().isEmpty(),
        "no resilience4j rate limiter on the 429 path");
  }

  /** A rejected order placement is surfaced after one wire attempt, never blind-replayed. */
  @Test
  void rejectedOrderPlacementIsNotReplayed() {
    CoinsphTradeServiceRaw trade = (CoinsphTradeServiceRaw) exchange.getTradeService();
    stub.script(ORDER, new Reply(429, "1", "{\"code\":-1003,\"msg\":\"Too many requests\"}"));
    LimitOrder order =
        new LimitOrder.Builder(Order.OrderType.BID, CurrencyPair.BTC_USDT)
            .originalAmount(new BigDecimal("0.01"))
            .limitPrice(new BigDecimal("50000"))
            .userReference("client-order-1")
            .build();

    RateLimitTerminatedException terminated =
        assertThrows(RateLimitTerminatedException.class, () -> trade.placeCoinsphLimitOrder(order));

    assertEquals(RateLimitTerminatedException.Dispatch.REJECTED, terminated.getDispatch());
    assertEquals(
        RateLimitTerminatedException.Reason.REMOTE_PRESSURE_EXHAUSTED, terminated.getReason());
    assertEquals(1, stub.arrivals(ORDER), "the mutation was sent exactly once");
  }

  private long admissions() {
    return specification.getResilience().getRateLimitContext().diagnostics().getAdmissions();
  }

  private static final class Reply {
    final int status;
    final String retryAfter;
    final String body;

    Reply(int status, String retryAfter, String body) {
      this.status = status;
      this.retryAfter = retryAfter;
      this.body = body;
    }
  }

  /** Loopback server with scripted replies per {@code METHOD path}; unscripted calls get 200. */
  private static final class Stub implements AutoCloseable {

    private final HttpServer server;
    private final ExecutorService handlers = Executors.newFixedThreadPool(4);
    private final Object lock = new Object();
    private final List<String> arrivalLog = new ArrayList<>();
    private final Map<String, ArrayDeque<Reply>> scripts = new HashMap<>();

    Stub() throws IOException {
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.setExecutor(handlers);
      server.createContext("/", this::handle);
      server.start();
    }

    int port() {
      return server.getAddress().getPort();
    }

    void script(String key, Reply reply) {
      synchronized (lock) {
        scripts.computeIfAbsent(key, k -> new ArrayDeque<>()).add(reply);
      }
    }

    int arrivals(String key) {
      synchronized (lock) {
        int count = 0;
        for (String entry : arrivalLog) {
          if (entry.equals(key)) {
            count++;
          }
        }
        return count;
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
      Reply scripted;
      synchronized (lock) {
        arrivalLog.add(key);
        ArrayDeque<Reply> queue = scripts.get(key);
        scripted = queue == null ? null : queue.poll();
      }
      if (scripted != null) {
        if (scripted.retryAfter != null) {
          http.getResponseHeaders().add("Retry-After", scripted.retryAfter);
        }
        respond(http, scripted.status, scripted.body);
        return;
      }
      respond(http, 200, body(key));
    }

    private static String body(String key) {
      if (key.equals(TIME)) {
        return "{\"serverTime\":" + System.currentTimeMillis() + "}";
      }
      if (key.equals(ACCOUNT)) {
        return "{\"balances\":[],\"permissions\":[]}";
      }
      if (key.equals(TRADES)) {
        return "[]";
      }
      return "{}";
    }

    private static void respond(HttpExchange http, int status, String body) throws IOException {
      byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
      http.getResponseHeaders().add("Content-Type", "application/json");
      http.sendResponseHeaders(status, bytes.length);
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
