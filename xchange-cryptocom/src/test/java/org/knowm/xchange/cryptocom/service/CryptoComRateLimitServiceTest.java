package org.knowm.xchange.cryptocom.service;

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
import org.knowm.xchange.cryptocom.CryptoComExchange;
import org.knowm.xchange.cryptocom.dto.trade.CryptoComOrderSide;
import org.knowm.xchange.cryptocom.dto.trade.CryptoComOrderType;
import org.knowm.xchange.exceptions.RateLimitTerminatedException;

/**
 * Service-level proof of the universal rate limiter on the Crypto.com path: one admission per wire
 * attempt, no resilience4j rate limiter anywhere, replay of rejected reads after the cooldown, and
 * no blind replay of a rejected order placement.
 *
 * <p>Runs against a dynamic-port loopback server; nothing leaves the machine.
 */
class CryptoComRateLimitServiceTest {

  private static final String TRADES = "GET /exchange/v1/public/get-trades";
  private static final String BALANCE = "POST /exchange/v1/private/user-balance";
  private static final String ORDER = "POST /exchange/v1/private/create-order";
  private static final String TOO_MANY =
      "{\"id\":1,\"method\":\"private\",\"code\":42901,\"message\":\"TOO_MANY_REQUESTS\"}";

  private Stub stub;
  private ExchangeSpecification specification;
  private CryptoComExchange exchange;

  /** Same exchange, but the loopback server replaces the production host. */
  private static final class LoopbackCryptoComExchange extends CryptoComExchange {
    private final String uri;

    LoopbackCryptoComExchange(String uri) {
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
    specification = new CryptoComExchange().getDefaultExchangeSpecification();
    specification.setShouldLoadRemoteMetaData(false);
    specification.setApiKey("test-api-key");
    specification.setSecretKey("test-secret-key");
    exchange = new LoopbackCryptoComExchange("http://127.0.0.1:" + stub.port());
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

  /** Every wire attempt is admitted exactly once and no resilience4j limiter is created. */
  @Test
  void oneAdmissionPerWireAttemptAndNoResilience4jRateLimiter() throws Exception {
    CryptoComMarketDataServiceRaw marketData =
        (CryptoComMarketDataServiceRaw) exchange.getMarketDataService();
    CryptoComAccountServiceRaw account = (CryptoComAccountServiceRaw) exchange.getAccountService();
    assertEquals(0L, admissions(), "construction sends nothing");

    assertEquals(0, marketData.getCryptoComTrades("BTC_USDT", 5).size());
    assertEquals(0, account.getCryptoComBalances().size());

    assertEquals(1, stub.arrivals(TRADES));
    assertEquals(1, stub.arrivals(BALANCE));
    assertEquals((long) stub.totalArrivals(), admissions(), "admissions equal wire attempts");
    assertTrue(
        exchange.getResilienceRegistries().rateLimiters().getAllRateLimiters().isEmpty(),
        "no resilience4j rate limiter was created on the service path");
  }

  /** A 429 on a read is cooled down and replayed with a fresh admission. */
  @Test
  void rejectedReadIsReplayedWithFreshAdmission() throws Exception {
    CryptoComAccountServiceRaw account = (CryptoComAccountServiceRaw) exchange.getAccountService();
    stub.script(BALANCE, new Reply(429, TOO_MANY));

    assertEquals(0, account.getCryptoComBalances().size());

    assertEquals(2, stub.arrivals(BALANCE), "the rejected read was replayed once");
    assertEquals(
        2L, admissions(), "the rejected attempt and the replay were each admitted exactly once");
    assertTrue(
        exchange.getResilienceRegistries().rateLimiters().getAllRateLimiters().isEmpty(),
        "no resilience4j rate limiter on the 429 path");
  }

  /** A rejected order placement is surfaced after one wire attempt, never blind-replayed. */
  @Test
  void rejectedOrderPlacementIsNotReplayed() {
    CryptoComTradeServiceRaw trade = (CryptoComTradeServiceRaw) exchange.getTradeService();
    stub.script(ORDER, new Reply(429, TOO_MANY));

    RateLimitTerminatedException terminated =
        assertThrows(
            RateLimitTerminatedException.class,
            () ->
                trade.createCryptoComOrder(
                    "BTC_USDT",
                    CryptoComOrderSide.BUY,
                    CryptoComOrderType.LIMIT,
                    "50000",
                    "0.5",
                    null,
                    "client-order-1"));

    assertEquals(RateLimitTerminatedException.Dispatch.REJECTED, terminated.getDispatch());
    assertEquals(
        RateLimitTerminatedException.Reason.REMOTE_PRESSURE_EXHAUSTED, terminated.getReason());
    assertEquals(1, stub.arrivals(ORDER), "the mutation was sent exactly once");
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
      respond(http, 200, "{\"id\":1,\"method\":\"m\",\"code\":0,\"result\":{\"data\":[]}}");
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
