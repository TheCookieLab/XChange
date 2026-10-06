package org.knowm.xchange.coinbase.v3;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.client.ratelimit.RateLimitBudget;
import org.knowm.xchange.client.ratelimit.RateLimitBudget.ScopeKind;
import org.knowm.xchange.client.ratelimit.RateLimitContext;
import org.knowm.xchange.client.ratelimit.RateLimitOperation;
import org.knowm.xchange.client.ratelimit.RateLimitPolicy;
import org.knowm.xchange.client.ratelimit.RateLimitPriority;
import org.knowm.xchange.client.ratelimit.RateLimitRequest;
import org.knowm.xchange.coinbase.v2.CoinbaseV2Authenticated;
import si.mazi.rescu.ParamsDigest;

/** Offline checks of the Advanced Trade rate-limit policy: classification, bounds, enablement. */
public class CoinbaseRateLimitPolicyTest {

  private static final Class<?>[] INTERFACES = {
    Coinbase.class, CoinbaseAuthenticated.class, CoinbaseV2Authenticated.class
  };

  private static final RateLimitPolicy POLICY = CoinbaseRateLimitPolicy.defaultPolicy();

  // ---- classification ------------------------------------------------------------------------

  /** Every declared method of every raw interface is classified, with consistent costs. */
  @Test
  public void everyRawInterfaceMethodIsClassified() {
    int checked = 0;
    for (Class<?> iface : INTERFACES) {
      for (Method method : iface.getDeclaredMethods()) {
        if (method.isSynthetic() || httpMethod(method) == null) {
          continue;
        }
        boolean authenticated = isAuthenticated(method);
        String key = operationKey(iface, method);
        RateLimitOperation operation = POLICY.classify(new RateLimitRequest(key, authenticated));
        assertNotNull("unclassified " + iface.getSimpleName() + "." + method.getName() + ": " + key, operation);
        assertEquals(
            "replay only for reads: " + key,
            "GET".equals(httpMethod(method)),
            operation.isReplayOnRateLimit());
        if (authenticated) {
          assertEquals(
              key,
              Map.of(
                  CoinbaseRateLimitPolicy.USER_HOURLY, 1L, CoinbaseRateLimitPolicy.BROKERAGE_CLIENT, 1L),
              operation.getRequirements());
        } else {
          assertEquals(
              key, Map.of(CoinbaseRateLimitPolicy.BROKERAGE_PUBLIC, 1L), operation.getRequirements());
        }
        checked++;
      }
    }
    assertTrue("reflection found the interface methods: " + checked, checked >= 60);
  }

  @Test
  public void publicInterfaceNeverConsumesTheUserBudgets() {
    for (Method method : Coinbase.class.getDeclaredMethods()) {
      if (httpMethod(method) == null) {
        continue;
      }
      assertFalse("Coinbase public methods carry no digest", isAuthenticated(method));
      RateLimitOperation operation =
          POLICY.classify(new RateLimitRequest(operationKey(Coinbase.class, method), false));
      assertEquals(RateLimitPriority.MARKET_DATA, operation.getPriority());
      assertFalse(
          operation.getRequirements().containsKey(CoinbaseRateLimitPolicy.USER_HOURLY));
    }
  }

  @Test
  public void executionOperationsAreClassifiedExecution() {
    String[] keys = {
      "POST api/v3/brokerage/orders",
      "POST api/v3/brokerage/orders/edit",
      "POST api/v3/brokerage/orders/batch_cancel",
      "POST api/v3/brokerage/orders/close_position",
      "POST api/v3/brokerage/orders/preview",
      "POST api/v3/brokerage/convert/quote",
      "POST api/v3/brokerage/convert/trade/{trade_id}",
      "GET api/v3/brokerage/orders/historical/batch",
      "GET api/v3/brokerage/orders/historical/fills",
      "GET api/v3/brokerage/orders/historical/{order_id}",
      "GET api/v3/brokerage/accounts",
      "GET api/v3/brokerage/accounts/{account_id}",
      "GET api/v3/brokerage/cfm/positions",
      "GET api/v3/brokerage/intx/positions/{portfolio_uuid}",
      "GET api/v3/brokerage/cfm/balance_summary",
      "GET api/v3/brokerage/portfolios",
      "GET v2/accounts/{account_id}/transactions"
    };
    for (String key : keys) {
      RateLimitOperation operation = POLICY.classify(new RateLimitRequest(key, true));
      assertNotNull(key, operation);
      assertEquals(key, RateLimitPriority.EXECUTION, operation.getPriority());
    }
  }

  @Test
  public void marketDataOperationsAreClassifiedMarketData() {
    String[] keys = {
      "GET api/v3/brokerage/time",
      "GET api/v3/brokerage/market/products",
      "GET api/v3/brokerage/market/products/{product_id}",
      "GET api/v3/brokerage/market/products/{product_id}/candles",
      "GET api/v3/brokerage/market/products/{product_id}/ticker",
      "GET api/v3/brokerage/market/product_book",
      "GET api/v3/brokerage/products",
      "GET api/v3/brokerage/products/{product_id}/candles",
      "GET api/v3/brokerage/products/{product_id}/ticker",
      "GET api/v3/brokerage/product_book",
      "GET api/v3/brokerage/best_bid_ask"
    };
    for (String key : keys) {
      for (boolean authenticated : new boolean[] {true, false}) {
        RateLimitOperation operation = POLICY.classify(new RateLimitRequest(key, authenticated));
        assertNotNull(key, operation);
        assertEquals(key, RateLimitPriority.MARKET_DATA, operation.getPriority());
        assertTrue(key, operation.isReplayOnRateLimit());
      }
    }
  }

  @Test
  public void newMethodUnderKnownFamilyIsClassifiedWithoutCodeChange() {
    RateLimitOperation order =
        POLICY.classify(new RateLimitRequest("POST api/v3/brokerage/orders/some_new_action", true));
    assertEquals(RateLimitPriority.EXECUTION, order.getPriority());
    assertFalse("a new mutation is never replayed", order.isReplayOnRateLimit());

    RateLimitOperation read =
        POLICY.classify(
            new RateLimitRequest("GET api/v3/brokerage/products/{product_id}/new_view", true));
    assertEquals(RateLimitPriority.MARKET_DATA, read.getPriority());
    assertTrue(read.isReplayOnRateLimit());
  }

  @Test
  public void concreteAndTemplatedPathsClassifyAlike() {
    RateLimitOperation templated =
        POLICY.classify(
            new RateLimitRequest("GET api/v3/brokerage/market/products/{product_id}/ticker", false));
    RateLimitOperation concrete =
        POLICY.classify(
            new RateLimitRequest("GET /api/v3/brokerage/market/products/BTC-USD/ticker", false));
    assertEquals(templated.getPriority(), concrete.getPriority());
    assertEquals(templated.getRequirements(), concrete.getRequirements());
  }

  @Test
  public void unknownOperationsAreUnclassified() {
    String[] keys = {
      "GET api/v3/brokerage/unknown/thing",
      "GET",
      "",
      "GET ",
      "TRACE api/v3/brokerage/accounts",
      "POST api/v3/brokerage/products/{product_id}",
      "POST api/v3/brokerage/market/products",
      "DELETE api/v3/brokerage/time",
      "GET api/v3/other/accounts"
    };
    for (String key : keys) {
      assertNull(key, POLICY.classify(new RateLimitRequest(key, true)));
    }
  }

  // ---- bounds and budgets ------------------------------------------------------------------

  @Test
  public void budgetsAndBoundsMatchTheDeclaredPolicy() {
    RateLimitBudget hourly = POLICY.getBudget(CoinbaseRateLimitPolicy.USER_HOURLY);
    assertEquals(
        RateLimitBudget.rollingWindow(
            "coinbase.user.hourly", ScopeKind.USER, 10_000, Duration.ofHours(1)),
        hourly);
    assertEquals(
        RateLimitBudget.tokenBucket(
            "coinbase.brokerage.client", ScopeKind.USER, 10, 10, Duration.ofSeconds(1)),
        POLICY.getBudget(CoinbaseRateLimitPolicy.BROKERAGE_CLIENT));
    assertEquals(
        RateLimitBudget.tokenBucket(
            "coinbase.brokerage.public", ScopeKind.EGRESS, 10, 10, Duration.ofSeconds(1)),
        POLICY.getBudget(CoinbaseRateLimitPolicy.BROKERAGE_PUBLIC));
    assertEquals(3, POLICY.getBudgets().size());
    assertTrue(POLICY.pendingLimit(RateLimitPriority.MARKET_DATA) >= 256);
    assertTrue(POLICY.pendingLimit(RateLimitPriority.MARKET_DATA) > 82);
    assertTrue(POLICY.pendingLimit(RateLimitPriority.EXECUTION) >= 1);
    assertEquals(Duration.ofSeconds(1), POLICY.getFallbackBackoffBase());
    assertEquals(Duration.ofSeconds(30), POLICY.getFallbackBackoffCap());
    assertEquals(0.25, POLICY.getFallbackBackoffJitter(), 0.0);
    assertTrue(POLICY.getSource().contains("2026-10-05"));
    assertTrue(POLICY.getSource().contains("client pacing"));
  }

  // ---- AC20: enablement and overrides ------------------------------------------------------

  @Test
  public void defaultSpecificationEnablesTheLimiterWithThePolicy() {
    ExchangeSpecification specification = new CoinbaseExchange().getDefaultExchangeSpecification();
    assertTrue(specification.getResilience().isRateLimiterEnabled());
    assertSame(POLICY, specification.getResilience().getRateLimitPolicy());

    CoinbaseExchange exchange = new CoinbaseExchange();
    exchange.applySpecification(specification);
    RateLimitContext context = specification.getResilience().getRateLimitContext();
    try {
      assertNotNull("an enabled policy gets an owned context", context);
      assertEquals(
          CoinbaseRateLimitPolicy.VERSION,
          context.diagnostics().getPolicyVersions().get(CoinbaseRateLimitPolicy.NAMESPACE));
    } finally {
      context.close();
    }
  }

  @Test
  public void explicitDisableIsHonoured() {
    ExchangeSpecification specification = new CoinbaseExchange().getDefaultExchangeSpecification();
    specification.getResilience().setRateLimiterEnabled(false);

    CoinbaseExchange exchange = new CoinbaseExchange();
    exchange.applySpecification(specification);

    assertFalse(specification.getResilience().isRateLimiterEnabled());
    assertNull(
        "a disabled policy creates no enforcing context",
        specification.getResilience().getRateLimitContext());
  }

  @Test
  public void derivedPolicyOverridesBudgetsWithoutMutatingTheDefault() {
    RateLimitBudget lower =
        RateLimitBudget.tokenBucket(
            CoinbaseRateLimitPolicy.BROKERAGE_CLIENT, ScopeKind.USER, 5, 5, Duration.ofSeconds(1));
    RateLimitBudget raised =
        RateLimitBudget.rollingWindow(
            CoinbaseRateLimitPolicy.USER_HOURLY, ScopeKind.USER, 20_000, Duration.ofHours(1));
    RateLimitPolicy derived =
        POLICY
            .withBudget(lower)
            .withBudget(raised)
            .withPendingLimit(RateLimitPriority.MARKET_DATA, 1_024);

    assertEquals(lower, derived.getBudget(CoinbaseRateLimitPolicy.BROKERAGE_CLIENT));
    assertEquals(raised, derived.getBudget(CoinbaseRateLimitPolicy.USER_HOURLY));
    assertEquals(1_024, derived.pendingLimit(RateLimitPriority.MARKET_DATA));
    assertEquals(
        POLICY.getBudget(CoinbaseRateLimitPolicy.BROKERAGE_PUBLIC),
        derived.getBudget(CoinbaseRateLimitPolicy.BROKERAGE_PUBLIC));
    assertEquals(10, POLICY.getBudget(CoinbaseRateLimitPolicy.BROKERAGE_CLIENT).getCapacity());
    assertEquals(10_000, POLICY.getBudget(CoinbaseRateLimitPolicy.USER_HOURLY).getCapacity());

    String key = "POST api/v3/brokerage/orders";
    assertEquals(
        POLICY.classify(new RateLimitRequest(key, true)),
        derived.classify(new RateLimitRequest(key, true)));

    ExchangeSpecification specification = new CoinbaseExchange().getDefaultExchangeSpecification();
    specification.getResilience().setRateLimitPolicy(derived);
    CoinbaseExchange exchange = new CoinbaseExchange();
    exchange.applySpecification(specification);
    RateLimitContext context = specification.getResilience().getRateLimitContext();
    try {
      assertTrue(specification.getResilience().isRateLimiterEnabled());
      assertNotNull(context);
    } finally {
      context.close();
    }
  }

  // ---- reflection helpers --------------------------------------------------------------------

  private static String httpMethod(Method method) {
    if (method.isAnnotationPresent(GET.class)) {
      return "GET";
    }
    if (method.isAnnotationPresent(POST.class)) {
      return "POST";
    }
    if (method.isAnnotationPresent(PUT.class)) {
      return "PUT";
    }
    if (method.isAnnotationPresent(DELETE.class)) {
      return "DELETE";
    }
    return null;
  }

  private static boolean isAuthenticated(Method method) {
    for (Class<?> type : method.getParameterTypes()) {
      if (ParamsDigest.class.isAssignableFrom(type)) {
        return true;
      }
    }
    return false;
  }

  /** {@code "<METHOD> <interface path>/<method path>"}, no leading slash, slashes collapsed. */
  private static String operationKey(Class<?> iface, Method method) {
    List<String> parts = new ArrayList<>();
    parts.add(iface.getAnnotation(Path.class).value());
    Path methodPath = method.getAnnotation(Path.class);
    if (methodPath != null) {
      parts.add(methodPath.value());
    }
    String path = String.join("/", parts).replaceAll("/+", "/").replaceAll("^/", "");
    return httpMethod(method) + " " + path;
  }
}
