package org.knowm.xchange.coinbase.v3.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.client.ExchangeRestProxyBuilder;
import org.knowm.xchange.client.ratelimit.RateLimitContext;
import org.knowm.xchange.coinbase.v2.CoinbaseV2Authenticated;
import org.knowm.xchange.coinbase.v3.CoinbaseAuthenticated;
import org.knowm.xchange.coinbase.v3.CoinbaseExchange;
import org.knowm.xchange.coinbase.v3.CoinbaseProductIdentity;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseOrderRequest;
import org.knowm.xchange.exceptions.RateLimitTerminatedException;
import si.mazi.rescu.ParamsDigest;

/**
 * Protocol-level proof that the default Advanced Trade policy keeps a startup burst below an
 * independent server-side quota, and that a rejection is replayed only for reads.
 *
 * <p>The stub meters requests with its own token bucket on the real clock, entirely independent of
 * the production limiter, and answers {@code 429 rate_limit_exceeded} when it is exhausted. It runs
 * on a loopback dynamic port; nothing leaves the machine.
 */
public class CoinbaseRateLimitQuotaTest {

  private static final String PRODUCTS = "/api/v3/brokerage/products";
  private static final String ACCOUNTS = "/api/v3/brokerage/accounts";
  private static final String ORDERS = "/api/v3/brokerage/orders";
  private static final int BURST = 82;
  private static final int TIMEOUT_SECONDS = 90;

  private QuotaStub stub;
  private ExecutorService callers;
  private ExchangeSpecification specification;
  private CoinbaseExchange exchange;
  private ParamsDigest digest;

  @Before
  public void setUp() throws IOException {
    stub = new QuotaStub();
    callers = Executors.newFixedThreadPool(BURST + 8);
    specification = new CoinbaseExchange().getDefaultExchangeSpecification();
    specification.setSslUri("http://127.0.0.1:" + stub.port());
    specification.setHost("127.0.0.1");
    exchange = new CoinbaseExchange();
    exchange.applySpecification(specification);
    digest = invocation -> "Bearer deterministic-test-token";
  }

  @After
  public void tearDown() {
    callers.shutdownNow();
    RateLimitContext context = specification.getResilience().getRateLimitContext();
    if (context != null) {
      context.close();
    }
    stub.close();
  }

  /**
   * AC21: an 82-request startup burst plus catalog discovery completes with no 429 from a server
   * quota far below the burst, and an execution request issued mid-burst overtakes queued market
   * data.
   */
  @Test(timeout = TIMEOUT_SECONDS * 1000L)
  public void startupBurstStaysWithinQuotaAndExecutionOvertakesMarketData() throws Exception {
    stub.configureBucket(20, 10.0);
    CoinbaseMarketDataServiceRaw market =
        new CoinbaseMarketDataServiceRaw(exchange, authenticatedProxy(), digest);
    CoinbaseAccountServiceRaw account =
        new CoinbaseAccountServiceRaw(exchange, authenticatedProxy(), digest, v2Proxy());

    List<Future<?>> marketCalls = new ArrayList<>();
    for (int i = 0; i < BURST; i++) {
      marketCalls.add(callers.submit(() -> market.getProduct("BTC-USD")));
    }
    Future<CoinbaseProductIdentity> discovery =
        callers.submit(() -> CoinbaseProductIdentity.discover(market));

    assertTrue("first burst reached the server", stub.awaitArrivals(10, TIMEOUT_SECONDS));
    Future<?> execution = callers.submit(account::getCoinbaseAccounts);

    assertNotNull(discovery.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
    for (Future<?> call : marketCalls) {
      assertNotNull(call.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
    }
    execution.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

    assertEquals("the server quota was never exceeded", 0, stub.rejections());
    int executionArrival = stub.firstArrivalIndex("GET " + ACCOUNTS);
    assertTrue("execution request reached the server", executionArrival >= 0);
    assertTrue(
        "execution was admitted ahead of the queued market data, arrival #" + executionArrival,
        executionArrival < 25);
    assertTrue(
        "all market data calls and the discovery pages were sent",
        stub.arrivalsWithPrefix("GET " + PRODUCTS) >= BURST + 2);
  }

  /**
   * An external consumer drained the user's quota: the server answers 429 with Retry-After. The
   * read is replayed after the cooldown and succeeds; the mutation is sent exactly once and the
   * rejection is surfaced, never replayed.
   */
  @Test(timeout = TIMEOUT_SECONDS * 1000L)
  public void externalConsumptionRecoversReadsAndNeverDuplicatesMutations() throws Exception {
    stub.configureBucket(1_000, 1_000.0);
    stub.forceRejections("GET " + ACCOUNTS, 1, "1");
    stub.forceRejections("POST " + ORDERS, 1, "1");
    CoinbaseAccountServiceRaw account =
        new CoinbaseAccountServiceRaw(exchange, authenticatedProxy(), digest, v2Proxy());
    CoinbaseTradeServiceRaw trade =
        new CoinbaseTradeServiceRaw(exchange, authenticatedProxy(), digest);

    long start = System.nanoTime();
    assertEquals(0, account.getCoinbaseAccounts().size());
    Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

    assertEquals("the rejected read was replayed once", 2, stub.arrivals("GET " + ACCOUNTS));
    assertTrue("Retry-After 1s was honoured: " + elapsed, elapsed.compareTo(Duration.ofMillis(900)) >= 0);
    assertTrue("recovered within the execution deadline: " + elapsed, elapsed.compareTo(Duration.ofSeconds(5)) < 0);

    CoinbaseOrderRequest order =
        new CoinbaseOrderRequest(
            "client-order-1", "BTC-USD", null, null, null, null, null, null, null, null, null);
    try {
      trade.createOrder(order);
      fail("a rejected mutation must surface");
    } catch (RateLimitTerminatedException terminated) {
      assertEquals(RateLimitTerminatedException.Dispatch.REJECTED, terminated.getDispatch());
      assertEquals(
          RateLimitTerminatedException.Reason.REMOTE_PRESSURE_EXHAUSTED, terminated.getReason());
    }
    assertEquals("the mutation was sent exactly once", 1, stub.arrivals("POST " + ORDERS));
  }

  private CoinbaseAuthenticated authenticatedProxy() {
    return ExchangeRestProxyBuilder.forInterface(
            CoinbaseAuthenticated.class, exchange.getExchangeSpecification())
        .build();
  }

  private CoinbaseV2Authenticated v2Proxy() {
    return ExchangeRestProxyBuilder.forInterface(
            CoinbaseV2Authenticated.class, exchange.getExchangeSpecification())
        .build();
  }

  /** Loopback server with its own token bucket on the real clock. */
  private static final class QuotaStub implements AutoCloseable {

    private final HttpServer server;
    private final ExecutorService handlers = Executors.newFixedThreadPool(BURST + 8);
    private final Object lock = new Object();
    private final List<String> arrivalLog = new ArrayList<>();
    private final Map<String, Integer> forcedRejections = new HashMap<>();
    private final Map<String, String> forcedRetryAfter = new HashMap<>();
    private volatile CountDownLatch arrivalLatch = new CountDownLatch(0);
    private double capacity = 1_000;
    private double refillPerSecond = 1_000;
    private double tokens = 1_000;
    private long lastRefill = System.nanoTime();
    private int rejected;

    QuotaStub() throws IOException {
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.setExecutor(handlers);
      server.createContext("/", this::handle);
      server.start();
    }

    int port() {
      return server.getAddress().getPort();
    }

    void configureBucket(double bucketCapacity, double refill) {
      synchronized (lock) {
        capacity = bucketCapacity;
        refillPerSecond = refill;
        tokens = bucketCapacity;
        lastRefill = System.nanoTime();
      }
      arrivalLatch = new CountDownLatch(10);
    }

    void forceRejections(String methodAndPath, int count, String retryAfter) {
      synchronized (lock) {
        forcedRejections.put(methodAndPath, count);
        forcedRetryAfter.put(methodAndPath, retryAfter);
      }
    }

    boolean awaitArrivals(int count, int seconds) throws InterruptedException {
      if (count != 10) {
        throw new IllegalArgumentException("latch is armed for 10 arrivals");
      }
      return arrivalLatch.await(seconds, TimeUnit.SECONDS);
    }

    int rejections() {
      synchronized (lock) {
        return rejected;
      }
    }

    int arrivals(String methodAndPath) {
      synchronized (lock) {
        int count = 0;
        for (String entry : arrivalLog) {
          if (entry.equals(methodAndPath)) {
            count++;
          }
        }
        return count;
      }
    }

    int arrivalsWithPrefix(String prefix) {
      synchronized (lock) {
        int count = 0;
        for (String entry : arrivalLog) {
          if (entry.startsWith(prefix)) {
            count++;
          }
        }
        return count;
      }
    }

    int firstArrivalIndex(String methodAndPath) {
      synchronized (lock) {
        return arrivalLog.indexOf(methodAndPath);
      }
    }

    private void handle(HttpExchange http) throws IOException {
      String key = http.getRequestMethod() + " " + http.getRequestURI().getPath();
      byte[] ignored = http.getRequestBody().readAllBytes();
      boolean reject;
      String retryAfter = null;
      synchronized (lock) {
        arrivalLog.add(key);
        Integer forced = forcedRejections.get(key);
        if (forced != null && forced > 0) {
          forcedRejections.put(key, forced - 1);
          retryAfter = forcedRetryAfter.get(key);
          reject = true;
        } else {
          long now = System.nanoTime();
          tokens =
              Math.min(capacity, tokens + (now - lastRefill) / 1_000_000_000.0 * refillPerSecond);
          lastRefill = now;
          reject = tokens < 1.0;
          if (!reject) {
            tokens -= 1.0;
          }
        }
        if (reject) {
          rejected++;
        }
      }
      arrivalLatch.countDown();
      if (reject) {
        if (retryAfter != null) {
          http.getResponseHeaders().add("Retry-After", retryAfter);
        }
        respond(
            http,
            429,
            "{\"errors\":[{\"id\":\"rate_limit_exceeded\",\"message\":\"too many requests\"}]}");
        return;
      }
      respond(http, 200, body(key));
    }

    private static String body(String key) {
      if (key.equals("GET " + ACCOUNTS)) {
        return "{\"accounts\":[],\"has_next\":false,\"cursor\":\"\",\"size\":0}";
      }
      if (key.equals("GET " + PRODUCTS)) {
        return "{\"products\":[],\"num_products\":0}";
      }
      if (key.startsWith("GET " + PRODUCTS + "/")) {
        return "{\"product_id\":\"BTC-USD\"}";
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
