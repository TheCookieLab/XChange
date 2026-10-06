package org.knowm.xchange.okx.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.ratelimiter.RateLimiter;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.client.ratelimit.RateLimitContext;
import org.knowm.xchange.exceptions.RateLimitTerminatedException;
import org.knowm.xchange.okx.OkxAuthenticated;
import org.knowm.xchange.okx.OkxExchange;
import org.knowm.xchange.okx.dto.OkxResponse;
import org.knowm.xchange.okx.dto.marketdata.OkxTicker;
import org.knowm.xchange.okx.dto.trade.OkxOrderRequest;
import org.knowm.xchange.okx.dto.trade.OkxOrderResponse;

/**
 * Service-level proof, over a loopback server on a dynamic port, that the OKX REST services are
 * rate limited exclusively by the core limiter: one admission per wire attempt, replay of a
 * rejected read with fresh admission, no blind replay of order placement, and no resilience4j rate
 * limiter on the REST path. Nothing leaves the machine.
 */
public class OkxRateLimitQuotaTest {

  private static final String TICKER = "/api/v5/market/ticker";
  private static final String ORDER = "/api/v5/trade/order";
  private static final int TIMEOUT_MILLIS = 60_000;

  private static final String RATE_LIMITED_BODY =
      "{\"code\":\"50011\",\"msg\":\"Requests too frequent\",\"data\":[]}";
  private static final String EMPTY_SUCCESS = "{\"code\":\"0\",\"msg\":\"\",\"data\":[]}";

  private Stub stub;
  private ExchangeSpecification specification;
  private OkxExchange exchange;

  @Before
  public void setUp() throws IOException {
    stub = new Stub();
    exchange = new OkxExchange();
    specification = exchange.getDefaultExchangeSpecification();
    specification.setSslUri("http://127.0.0.1:" + stub.port());
    specification.setHost("127.0.0.1");
    specification.setApiKey("test-api-key");
    specification.setSecretKey("test-secret-key");
    specification.setExchangeSpecificParametersItem(OkxExchange.PARAM_PASSPHRASE, "test-pass");
    specification.setShouldLoadRemoteMetaData(false);
    exchange.applySpecification(specification);
  }

  @After
  public void tearDown() {
    RateLimitContext context = specification.getResilience().getRateLimitContext();
    if (context != null) {
      context.close();
    }
    stub.close();
  }

  @Test(timeout = TIMEOUT_MILLIS)
  public void eachWireAttemptIsAdmittedExactlyOnce() throws Exception {
    OkxMarketDataServiceRaw market =
        new OkxMarketDataServiceRaw(exchange, exchange.getResilienceRegistries());

    market.getOkxTicker("BTC-USDT");
    market.getOkxTicker("ETH-USDT");

    assertThat(stub.arrivals("GET " + TICKER)).isEqualTo(2);
    assertThat(admissions()).as("one admission per wire attempt").isEqualTo(2);
  }

  @Test(timeout = TIMEOUT_MILLIS)
  public void rejectedReadIsReplayedAfterCooldownWithFreshAdmission() throws Exception {
    stub.forceRejections("GET " + TICKER, 1, "1");
    OkxMarketDataServiceRaw market =
        new OkxMarketDataServiceRaw(exchange, exchange.getResilienceRegistries());

    long start = System.nanoTime();
    OkxResponse<List<OkxTicker>> response = market.getOkxTicker("BTC-USDT");
    Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

    assertThat(response.isSuccess()).isTrue();
    assertThat(stub.arrivals("GET " + TICKER)).as("rejected read replayed once").isEqualTo(2);
    assertThat(admissions()).as("the replay was admitted afresh").isEqualTo(2);
    assertThat(elapsed)
        .as("Retry-After 1s honoured")
        .isGreaterThanOrEqualTo(Duration.ofMillis(900));
  }

  @Test(timeout = TIMEOUT_MILLIS)
  public void rejectedOrderPlacementIsSentOnceAndSurfaced() {
    stub.forceRejections("POST " + ORDER, 1, "1");
    OkxTradeServiceRaw trade = new OkxTradeServiceRaw(exchange, exchange.getResilienceRegistries());

    assertThatThrownBy(() -> trade.doPlaceOkxOrder(order(null)))
        .isInstanceOfSatisfying(
            RateLimitTerminatedException.class,
            terminated ->
                assertThat(terminated.getDispatch())
                    .isEqualTo(RateLimitTerminatedException.Dispatch.REJECTED));

    assertThat(stub.arrivals("POST " + ORDER)).as("never blindly replayed").isEqualTo(1);
    assertThat(admissions()).isEqualTo(1);
  }

  @Test(timeout = TIMEOUT_MILLIS)
  public void placementWithReconciliationAdmitsLookupAndPlacementOnce() throws Exception {
    OkxTradeServiceRaw trade = new OkxTradeServiceRaw(exchange, exchange.getResilienceRegistries());

    OkxResponse<List<OkxOrderResponse>> response = trade.placeOkxOrder(order("client-order-1"));

    assertThat(response.isSuccess()).isTrue();
    assertThat(stub.arrivals("GET " + ORDER)).isEqualTo(1);
    assertThat(stub.arrivals("POST " + ORDER)).isEqualTo(1);
    assertThat(admissions()).as("one admission per wire attempt").isEqualTo(2);
  }

  @Test(timeout = TIMEOUT_MILLIS)
  public void noResilience4jRateLimiterGuardsTheRestPath() throws Exception {
    OkxMarketDataServiceRaw market =
        new OkxMarketDataServiceRaw(exchange, exchange.getResilienceRegistries());
    OkxTradeServiceRaw trade = new OkxTradeServiceRaw(exchange, exchange.getResilienceRegistries());

    market.getOkxTicker("BTC-USDT");
    trade.doPlaceOkxOrder(order(null));

    Set<String> limiterNames =
        exchange.getResilienceRegistries().rateLimiters().getAllRateLimiters().stream()
            .map(RateLimiter::getName)
            .collect(Collectors.toSet());
    assertThat(limiterNames)
        .as("only the WebSocket order limiters remain registered")
        .containsExactlyInAnyOrder(
            OkxAuthenticated.placeOrderPath,
            OkxAuthenticated.amendOrderPath,
            OkxAuthenticated.cancelOrderPath);
    for (String name : limiterNames) {
      assertThat(
              exchange
                  .getResilienceRegistries()
                  .rateLimiters()
                  .rateLimiter(name)
                  .getMetrics()
                  .getNumberOfWaitingThreads())
          .as(name)
          .isZero();
    }
    assertThat(admissions()).isEqualTo(2);
  }

  private long admissions() {
    return specification.getResilience().getRateLimitContext().diagnostics().getAdmissions();
  }

  private static OkxOrderRequest order(String clientOrderId) {
    return OkxOrderRequest.builder()
        .instrumentId("BTC-USDT")
        .tradeMode("cash")
        .side("buy")
        .orderType("limit")
        .amount("1")
        .price("1")
        .clientOrderId(clientOrderId)
        .build();
  }

  /** Loopback server that records arrivals and answers 429 on demand. */
  private static final class Stub implements AutoCloseable {

    private final HttpServer server;
    private final ExecutorService handlers = Executors.newFixedThreadPool(4);
    private final Object lock = new Object();
    private final List<String> arrivalLog = new ArrayList<>();
    private final Map<String, Integer> forcedRejections = new HashMap<>();
    private final Map<String, String> forcedRetryAfter = new HashMap<>();

    Stub() throws IOException {
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.setExecutor(handlers);
      server.createContext("/", this::handle);
      server.start();
    }

    int port() {
      return server.getAddress().getPort();
    }

    void forceRejections(String methodAndPath, int count, String retryAfter) {
      synchronized (lock) {
        forcedRejections.put(methodAndPath, count);
        forcedRetryAfter.put(methodAndPath, retryAfter);
      }
    }

    int arrivals(String methodAndPath) {
      synchronized (lock) {
        return (int) arrivalLog.stream().filter(methodAndPath::equals).count();
      }
    }

    private void handle(HttpExchange http) throws IOException {
      String key = http.getRequestMethod() + " " + http.getRequestURI().getPath();
      http.getRequestBody().readAllBytes();
      boolean reject = false;
      String retryAfter = null;
      synchronized (lock) {
        arrivalLog.add(key);
        Integer forced = forcedRejections.get(key);
        if (forced != null && forced > 0) {
          forcedRejections.put(key, forced - 1);
          retryAfter = forcedRetryAfter.get(key);
          reject = true;
        }
      }
      if (reject) {
        if (retryAfter != null) {
          http.getResponseHeaders().add("Retry-After", retryAfter);
        }
        respond(http, 429, RATE_LIMITED_BODY);
        return;
      }
      respond(http, 200, EMPTY_SUCCESS);
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
