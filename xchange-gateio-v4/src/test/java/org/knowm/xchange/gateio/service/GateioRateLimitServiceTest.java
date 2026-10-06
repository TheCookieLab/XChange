package org.knowm.xchange.gateio.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
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
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.exceptions.RateLimitTerminatedException;
import org.knowm.xchange.gateio.GateioExchange;
import org.knowm.xchange.gateio.dto.trade.GateioSpotOrderRequest;

/**
 * Service-level proof of the universal rate limiter on the Gate.io path: the exchange builds both
 * rescu proxies with rate limiting enabled (no custom proxy factory, no Apache connection type),
 * one admission per wire attempt, no resilience4j rate limiter anywhere, PATCH amendment works on
 * the core transport, rejected reads are replayed after the cooldown, and a rejected order
 * placement is never blind-replayed.
 *
 * <p>Runs against a dynamic-port loopback server; nothing leaves the machine.
 */
class GateioRateLimitServiceTest {

  private static final String TIME = "GET /api/v4/spot/time";
  private static final String ACCOUNTS = "GET /api/v4/spot/accounts";
  private static final String ORDERS = "POST /api/v4/spot/orders";
  private static final String AMEND = "PATCH /api/v4/spot/orders/42";
  private static final String TOO_MANY =
      "{\"label\":\"TOO_MANY_REQUESTS\",\"message\":\"Request Rate Limit Exceeded\"}";

  private Stub stub;
  private ExchangeSpecification specification;
  private GateioExchange exchange;

  @BeforeEach
  void setUp() throws IOException {
    stub = new Stub();
    specification = new GateioExchange().getDefaultExchangeSpecification();
    specification.setShouldLoadRemoteMetaData(false);
    specification.setApiKey("test-api-key");
    specification.setSecretKey("test-secret-key");
    specification.setSslUri("http://127.0.0.1:" + stub.port());
    exchange = new GateioExchange();
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

  /** The default specification enables rate limiting and construction sends nothing. */
  @Test
  void defaultSpecificationEnablesRateLimitingAndBuildsServices() {
    assertTrue(specification.getResilience().isRateLimiterEnabled());
    assertTrue(specification.getResilience().getRateLimitPolicy() != null);
    assertTrue(exchange.getAccountService() instanceof GateioAccountService);
    assertTrue(exchange.getTradeService() instanceof GateioTradeService);
    assertTrue(exchange.getMarketDataService() instanceof GateioMarketDataService);
    assertEquals(0L, admissions(), "construction sends nothing");
  }

  /** Every wire attempt is admitted exactly once and no resilience4j limiter is created. */
  @Test
  void oneAdmissionPerWireAttemptAndNoResilience4jRateLimiter() throws Exception {
    GateioMarketDataServiceRaw marketData = (GateioMarketDataServiceRaw) exchange.getMarketDataService();
    GateioAccountServiceRaw account = (GateioAccountServiceRaw) exchange.getAccountService();

    marketData.getGateioServerTime();
    assertEquals(0, account.getSpotBalances(Currency.USDT).size());

    assertEquals(1, stub.arrivals(TIME));
    assertEquals(1, stub.arrivals(ACCOUNTS));
    assertEquals((long) stub.totalArrivals(), admissions(), "admissions equal wire attempts");
    assertTrue(
        exchange.getResilienceRegistries().rateLimiters().getAllRateLimiters().isEmpty(),
        "no resilience4j rate limiter was created on the service path");
  }

  /** The PATCH amend endpoint works on the core transport that replaced the Apache client. */
  @Test
  void patchAmendWorksOnCoreTransportWithOneAdmission() throws Exception {
    GateioTradeServiceRaw trade = (GateioTradeServiceRaw) exchange.getTradeService();
    stub.script(AMEND, new Reply(200, "{\"id\":\"42\",\"status\":\"open\"}"));

    trade.amendSpotOrder("42", CurrencyPair.BTC_USDT, Map.of("price", "1"));

    assertEquals(1, stub.arrivals(AMEND));
    assertEquals(1L, admissions());
  }

  /** A 429 on a read is cooled down and replayed with a fresh admission. */
  @Test
  void rejectedReadIsReplayedWithFreshAdmission() throws Exception {
    GateioAccountServiceRaw account = (GateioAccountServiceRaw) exchange.getAccountService();
    stub.script(ACCOUNTS, new Reply(429, TOO_MANY));

    assertEquals(0, account.getSpotBalances(Currency.USDT).size());

    assertEquals(2, stub.arrivals(ACCOUNTS), "the rejected read was replayed once");
    assertEquals(
        2L, admissions(), "the rejected attempt and the replay were each admitted exactly once");
    assertTrue(
        exchange.getResilienceRegistries().rateLimiters().getAllRateLimiters().isEmpty(),
        "no resilience4j rate limiter on the 429 path");
  }

  /** A rejected order placement is surfaced after one wire attempt, never blind-replayed. */
  @Test
  void rejectedOrderPlacementIsNotReplayed() {
    GateioTradeServiceRaw trade = (GateioTradeServiceRaw) exchange.getTradeService();
    stub.script(ORDERS, new Reply(429, TOO_MANY));
    GateioSpotOrderRequest order =
        GateioSpotOrderRequest.builder()
            .currencyPair(CurrencyPair.BTC_USDT)
            .type("limit")
            .side(OrderType.BID)
            .amount("1")
            .price("1")
            .build();

    RateLimitTerminatedException terminated =
        assertThrows(RateLimitTerminatedException.class, () -> trade.createOrder(order));

    assertEquals(RateLimitTerminatedException.Dispatch.REJECTED, terminated.getDispatch());
    assertEquals(
        RateLimitTerminatedException.Reason.REMOTE_PRESSURE_EXHAUSTED, terminated.getReason());
    assertEquals(1, stub.arrivals(ORDERS), "the mutation was sent exactly once");
    assertEquals(1L, admissions());
  }

  private long admissions() {
    return specification.getResilience().getRateLimitContext().diagnostics().getAdmissions();
  }

  private static final class Reply {
    final int status;
    final String body;

    Reply(int status, String body) {
      this.status = status;
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
        respond(http, scripted.status, scripted.body);
        return;
      }
      respond(http, 200, key.equals(TIME) ? "{\"server_time\":1700000000}" : "[]");
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
