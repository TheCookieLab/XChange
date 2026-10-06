package org.knowm.xchange.kucoin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.client.ratelimit.RateLimitBudget;
import org.knowm.xchange.client.ratelimit.RateLimitBudget.ScopeKind;
import org.knowm.xchange.client.ratelimit.RateLimitFeedback;
import org.knowm.xchange.client.ratelimit.RateLimitOperation;
import org.knowm.xchange.client.ratelimit.RateLimitPolicy;
import org.knowm.xchange.client.ratelimit.RateLimitPriority;
import org.knowm.xchange.client.ratelimit.RateLimitRequest;
import org.knowm.xchange.kucoin.service.AccountAPI;
import org.knowm.xchange.kucoin.service.DepositAPI;
import org.knowm.xchange.kucoin.service.FillAPI;
import org.knowm.xchange.kucoin.service.HistOrdersAPI;
import org.knowm.xchange.kucoin.service.HistoryAPI;
import org.knowm.xchange.kucoin.service.LimitOrderAPI;
import org.knowm.xchange.kucoin.service.OrderAPI;
import org.knowm.xchange.kucoin.service.OrderBookAPI;
import org.knowm.xchange.kucoin.service.SymbolAPI;
import org.knowm.xchange.kucoin.service.TradingFeeAPI;
import org.knowm.xchange.kucoin.service.WebsocketAPI;
import org.knowm.xchange.kucoin.service.WithdrawalAPI;
import org.knowm.xchange.kucoin.uta.service.UtaAccountAPI;
import org.knowm.xchange.kucoin.uta.service.UtaCommonAPI;
import org.knowm.xchange.kucoin.uta.service.UtaMarketAPI;
import org.knowm.xchange.kucoin.uta.service.UtaPositionAPI;
import org.knowm.xchange.kucoin.uta.service.UtaTradeAPI;
import si.mazi.rescu.ParamsDigest;

/** Offline checks of the KuCoin rate-limit policy: classification, weights, scopes, enablement. */
class KucoinRateLimitPolicyTest {

  private static final Class<?>[] INTERFACES = {
    AccountAPI.class,
    DepositAPI.class,
    FillAPI.class,
    HistOrdersAPI.class,
    HistoryAPI.class,
    LimitOrderAPI.class,
    OrderAPI.class,
    OrderBookAPI.class,
    SymbolAPI.class,
    TradingFeeAPI.class,
    WebsocketAPI.class,
    WithdrawalAPI.class,
    UtaAccountAPI.class,
    UtaCommonAPI.class,
    UtaMarketAPI.class,
    UtaPositionAPI.class,
    UtaTradeAPI.class
  };

  /** Provider pools documented as USER keyed although the call is not signed. */
  private static final Set<String> UNSIGNED_USER_POOL = Set.of("GET api/ua/v1/market/orderbook");

  /** Token operations: the only mutations that create no economic state and are replay-safe. */
  private static final Set<String> TOKEN_OPERATIONS =
      Set.of("POST api/v1/bullet-public", "POST api/v1/bullet-private", "POST api/v2/bullet-private");

  private static final RateLimitPolicy POLICY = KucoinRateLimitPolicy.defaultPolicy();

  /** Every reachable rescu method of every interface classifies, so none fails terminally. */
  @Test
  void everyRescuInterfaceMethodIsClassified() {
    Set<String> seen = new HashSet<>();
    int checked = 0;
    for (Class<?> iface : INTERFACES) {
      for (Method method : iface.getDeclaredMethods()) {
        if (method.isSynthetic() || httpMethod(method) == null) {
          continue;
        }
        String key = operationKey(iface, method);
        seen.add(key);
        boolean authenticated = isAuthenticated(method);
        RateLimitOperation operation = POLICY.classify(new RateLimitRequest(key, authenticated));
        assertNotNull(operation, "unclassified " + iface.getSimpleName() + "." + method.getName() + ": " + key);
        assertEquals(key, operation.getName());
        assertEquals(1, operation.getRequirements().size(), key);
        String budgetId = operation.getRequirements().keySet().iterator().next();
        long cost = operation.getRequirements().get(budgetId);
        RateLimitBudget budget = POLICY.getBudget(budgetId);
        assertNotNull(budget, "unknown budget " + budgetId + " for " + key);
        assertTrue(cost >= 1 && cost <= 30, key + " weight " + cost);

        boolean userScope = budget.getScopeKind() == ScopeKind.USER;
        assertEquals(
            authenticated || UNSIGNED_USER_POOL.contains(key),
            userScope,
            "scope of " + key + " follows the signed/public split");

        boolean replaySafe = "GET".equals(httpMethod(method)) || TOKEN_OPERATIONS.contains(key);
        assertEquals(replaySafe, operation.isReplayOnRateLimit(), "replay safety of " + key);
        if (!authenticated && !UNSIGNED_USER_POOL.contains(key)) {
          assertEquals(RateLimitPriority.MARKET_DATA, operation.getPriority(), key);
        }
        if (authenticated) {
          assertEquals(RateLimitPriority.EXECUTION, operation.getPriority(), key);
        }
        checked++;
      }
    }
    assertTrue(checked >= 60, "reflection found the interface methods: " + checked);
    assertEquals(checked, seen.size(), "operation keys are unique per method");
  }

  /** Every rescu interface of the module is part of the enumeration above. */
  @Test
  void interfaceListCoversEveryRescuInterface() {
    Set<String> declared = new HashSet<>();
    for (Class<?> iface : INTERFACES) {
      declared.add(iface.getName());
    }
    for (String name :
        List.of(
            "org.knowm.xchange.kucoin.service.AccountAPI",
            "org.knowm.xchange.kucoin.uta.service.UtaTradeAPI")) {
      assertTrue(declared.contains(name), name);
    }
    assertEquals(17, INTERFACES.length);
  }

  @Test
  void documentedWeightsAreCharged() {
    assertCost("GET api/v1/accounts", KucoinRateLimitPolicy.CLASSIC_MANAGEMENT, 5);
    assertCost("POST api/v2/accounts/inner-transfer", KucoinRateLimitPolicy.CLASSIC_MANAGEMENT, 10);
    assertCost("GET api/v1/withdrawals", KucoinRateLimitPolicy.CLASSIC_MANAGEMENT, 20);
    assertCost("POST api/v1/hf/orders", KucoinRateLimitPolicy.CLASSIC_SPOT, 1);
    assertCost("DELETE api/v1/orders/{orderId}", KucoinRateLimitPolicy.CLASSIC_SPOT, 3);
    assertCost("DELETE api/v1/orders", KucoinRateLimitPolicy.CLASSIC_SPOT, 20);
    assertCost("GET api/v1/fills", KucoinRateLimitPolicy.CLASSIC_SPOT, 10);
    assertCost("GET api/v1/market/allTickers", KucoinRateLimitPolicy.CLASSIC_PUBLIC, 15);
    assertCost("GET api/v1/market/orderbook/level2_20", KucoinRateLimitPolicy.CLASSIC_PUBLIC, 2);
    assertCost("GET api/v1/earn/hold-assets", KucoinRateLimitPolicy.CLASSIC_EARN, 5);
    assertCost("POST api/ua/v1/unified/order/place", KucoinRateLimitPolicy.UTA_TRADING, 1);
    assertCost("POST api/ua/v1/unified/order/cancel-batch", KucoinRateLimitPolicy.UTA_TRADING, 4);
    assertCost("GET api/ua/v1/account/mode", KucoinRateLimitPolicy.UTA_MANAGEMENT, 30);
    assertCost("GET api/ua/v1/market/ticker", KucoinRateLimitPolicy.UTA_PUBLIC, 15);
  }

  @Test
  void mutationsAreNeverReplayed() {
    for (String key :
        List.of(
            "POST api/v1/hf/orders",
            "POST api/v1/stop-order",
            "DELETE api/v1/orders/{orderId}",
            "DELETE api/v1/orders",
            "POST api/v1/withdrawals",
            "POST api/v2/accounts/inner-transfer",
            "POST api/ua/v1/unified/order/place",
            "POST api/ua/v1/unified/order/cancel",
            "POST api/ua/v1/unified/order/amend",
            "POST api/ua/v1/unified/order/cancel-batch",
            "POST api/ua/v1/account/transfer")) {
      assertFalse(operation(key).isReplayOnRateLimit(), key);
    }
    assertTrue(operation("GET api/v1/orders").isReplayOnRateLimit());
    assertTrue(operation("POST api/v1/bullet-public").isReplayOnRateLimit());
  }

  @Test
  void unknownOperationsAreUnclassified() {
    assertNull(POLICY.classify(new RateLimitRequest("GET api/v1/not-declared", false)));
    assertNull(POLICY.classify(new RateLimitRequest("POST api/v1/accounts/{accountId}", true)));
  }

  @Test
  void budgetsMatchTheDocumentedPools() {
    assertBudget(KucoinRateLimitPolicy.CLASSIC_SPOT, ScopeKind.USER, 4000, Duration.ofSeconds(30));
    assertBudget(
        KucoinRateLimitPolicy.CLASSIC_MANAGEMENT, ScopeKind.USER, 2000, Duration.ofSeconds(30));
    assertBudget(KucoinRateLimitPolicy.CLASSIC_EARN, ScopeKind.USER, 2000, Duration.ofSeconds(30));
    assertBudget(
        KucoinRateLimitPolicy.CLASSIC_PUBLIC, ScopeKind.EGRESS, 2000, Duration.ofSeconds(30));
    assertBudget(KucoinRateLimitPolicy.UTA_TRADING, ScopeKind.USER, 300, Duration.ofSeconds(1));
    assertBudget(KucoinRateLimitPolicy.UTA_MANAGEMENT, ScopeKind.USER, 300, Duration.ofSeconds(1));
    assertBudget(KucoinRateLimitPolicy.UTA_PUBLIC, ScopeKind.EGRESS, 2000, Duration.ofSeconds(30));
    assertEquals(7, POLICY.getBudgets().size());
  }

  @Test
  void rejectionFeedbackHonoursResetHeaderThenRetryAfter() {
    Instant now = Instant.parse("2026-10-06T12:00:00Z");
    RateLimitFeedback reset =
        POLICY
            .getFeedbackInterpreter()
            .interpret(429, header("gw-ratelimit-reset", "1500"), now);
    assertEquals(RateLimitFeedback.Kind.RATE_REJECTED, reset.getKind());
    assertEquals(Duration.ofMillis(1500), reset.getRetryAfter());

    RateLimitFeedback retryAfter =
        POLICY.getFeedbackInterpreter().interpret(429, header("Retry-After", "2"), now);
    assertEquals(Duration.ofSeconds(2), retryAfter.getRetryAfter());

    RateLimitFeedback bare = POLICY.getFeedbackInterpreter().interpret(429, name -> null, now);
    assertEquals(RateLimitFeedback.Kind.RATE_REJECTED, bare.getKind());
    assertNull(bare.getRetryAfter());

    assertEquals(
        RateLimitFeedback.Kind.NONE,
        POLICY.getFeedbackInterpreter().interpret(418, name -> null, now).getKind());
    assertEquals(
        RateLimitFeedback.Kind.NONE,
        POLICY.getFeedbackInterpreter().interpret(200, header("gw-ratelimit-reset", "5"), now).getKind());
    assertNull(
        POLICY
            .getFeedbackInterpreter()
            .interpret(429, header("gw-ratelimit-reset", "garbage"), now)
            .getRetryAfter());
  }

  @Test
  void boundsAreExplicit() {
    assertEquals(3, POLICY.getMaxAttempts());
    assertEquals(Duration.ofSeconds(5), POLICY.maxWait(RateLimitPriority.EXECUTION));
    assertEquals(Duration.ofSeconds(60), POLICY.maxWait(RateLimitPriority.MARKET_DATA));
    assertEquals(64, POLICY.pendingLimit(RateLimitPriority.EXECUTION));
    assertEquals(512, POLICY.pendingLimit(RateLimitPriority.MARKET_DATA));
    assertTrue(POLICY.getSource().contains("retrieved 2026-10-06"));
    assertTrue(POLICY.getSource().contains("https://www.kucoin.com/docs-new/rate-limit-rule-classic"));
    assertTrue(POLICY.getSource().contains("https://www.kucoin.com/docs-new/rate-limit-rule-uta"));
  }

  @Test
  void defaultSpecificationEnablesTheCorePolicy() {
    ExchangeSpecification specification = new KucoinExchange().getDefaultExchangeSpecification();
    assertTrue(specification.getResilience().isRateLimiterEnabled());
    assertSame(KucoinRateLimitPolicy.defaultPolicy(), specification.getResilience().getRateLimitPolicy());
  }

  private static void assertCost(String key, String budget, long weight) {
    assertEquals(Map.of(budget, weight), operation(key).getRequirements(), key);
  }

  private static void assertBudget(String id, ScopeKind scope, long limit, Duration window) {
    RateLimitBudget budget = POLICY.getBudget(id);
    assertNotNull(budget, id);
    assertEquals(scope, budget.getScopeKind(), id);
    assertEquals(limit, budget.getCapacity(), id);
    assertEquals(window, budget.getPeriod(), id);
    assertEquals(RateLimitBudget.Law.ROLLING_WINDOW, budget.getLaw(), id);
  }

  private static RateLimitOperation operation(String key) {
    RateLimitOperation operation =
        POLICY.classify(new RateLimitRequest(key, !key.contains("/market/")));
    assertNotNull(operation, key);
    return operation;
  }

  private static java.util.function.Function<String, String> header(String name, String value) {
    return requested -> requested.equalsIgnoreCase(name) ? value : null;
  }

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
