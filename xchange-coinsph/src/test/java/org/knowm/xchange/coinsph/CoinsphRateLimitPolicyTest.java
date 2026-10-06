package org.knowm.xchange.coinsph;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.client.ratelimit.RateLimitOperation;
import org.knowm.xchange.client.ratelimit.RateLimitPolicy;
import org.knowm.xchange.client.ratelimit.RateLimitPriority;
import org.knowm.xchange.client.ratelimit.RateLimitRequest;
import si.mazi.rescu.ParamsDigest;

/** Offline checks of the Coins.ph rate-limit policy: classification, weights, scope, replay. */
class CoinsphRateLimitPolicyTest {

  private static final RateLimitPolicy POLICY = CoinsphRateLimitPolicy.defaultPolicy();
  private static final String IP = CoinsphRateLimitPolicy.IP_MINUTE;
  private static final String UID = CoinsphRateLimitPolicy.UID_MINUTE;

  /** Every reachable method of both rescu interfaces classifies, including inherited ones. */
  @Test
  void everyRescuInterfaceMethodIsClassified() {
    int checked = 0;
    for (Class<?> iface : new Class<?>[] {Coinsph.class, CoinsphAuthenticated.class}) {
      for (Method method : iface.getMethods()) {
        if (method.isSynthetic() || httpMethod(method) == null) {
          continue;
        }
        String key = operationKey(iface, method);
        RateLimitOperation operation =
            POLICY.classify(new RateLimitRequest(key, isAuthenticated(method)));
        assertNotNull(operation, "unclassified " + iface.getSimpleName() + "." + method.getName() + ": " + key);
        checked++;
      }
    }
    // 8 public methods + 8 inherited (authenticated interface) + 19 authenticated own methods
    assertTrue(checked >= 30, "reflection found the interface methods: " + checked);
  }

  @Test
  void unknownOperationIsUnclassified() {
    assertNull(POLICY.classify(new RateLimitRequest("GET openapi/v1/unknown", false)));
    assertNull(POLICY.classify(new RateLimitRequest("GET openapi/v1/order/extra", true)));
  }

  @Test
  void publicMarketDataChargesTheIpBudgetOnly() {
    for (String key :
        new String[] {
          "GET openapi/v1/ping",
          "GET openapi/v1/time",
          "GET openapi/v1/exchangeInfo",
          "GET openapi/v1/trades",
          "GET openapi/v1/depth",
          "GET openapi/v1/ticker/24hr"
        }) {
      RateLimitOperation operation = POLICY.classify(new RateLimitRequest(key, false));
      assertEquals(RateLimitPriority.MARKET_DATA, operation.getPriority(), key);
      assertTrue(operation.isReplayOnRateLimit(), key);
      assertFalse(operation.getRequirements().containsKey(UID), key);
      assertTrue(operation.getRequirements().containsKey(IP), key);
    }
  }

  @Test
  void inheritedPublicMethodsClassifyLikeThePublicInterface() {
    RateLimitOperation viaPublic =
        POLICY.classify(new RateLimitRequest("GET openapi/v1/trades", false));
    RateLimitOperation viaAuthenticated =
        POLICY.classify(new RateLimitRequest("GET openapi/trades", false));
    assertEquals(viaPublic.getRequirements(), viaAuthenticated.getRequirements());
    assertEquals(viaPublic.getName(), viaAuthenticated.getName());
  }

  @Test
  void tickerWeightDependsOnSymbol() {
    assertEquals(
        Map.of(IP, 1L),
        POLICY
            .classify(
                new RateLimitRequest(
                    "GET openapi/v1/ticker/24hr", false, Map.of("symbol", "BTCPHP")))
            .getRequirements());
    assertEquals(
        Map.of(IP, 40L),
        POLICY
            .classify(new RateLimitRequest("GET openapi/v1/ticker/24hr", false))
            .getRequirements());
  }

  @Test
  void depthWeightDependsOnLimit() {
    assertEquals(Map.of(IP, 1L), depthCost(null));
    assertEquals(Map.of(IP, 1L), depthCost("5"));
    assertEquals(Map.of(IP, 1L), depthCost("100"));
    assertEquals(Map.of(IP, 5L), depthCost("200"));
    assertEquals(Map.of(IP, 5L), depthCost("500"));
  }

  @Test
  void accountEndpointsChargeBothBudgetsWithDocumentedWeights() {
    assertEquals(Map.of(IP, 10L, UID, 10L), authenticatedCost("GET openapi/v1/account"));
    assertEquals(Map.of(IP, 10L, UID, 10L), authenticatedCost("GET openapi/v1/openOrders"));
    assertEquals(Map.of(IP, 10L, UID, 10L), authenticatedCost("GET openapi/v1/myTrades"));
    assertEquals(Map.of(IP, 2L, UID, 2L), authenticatedCost("GET openapi/v1/order"));
    assertEquals(Map.of(IP, 1L, UID, 1L), authenticatedCost("POST openapi/v1/order"));
    assertEquals(Map.of(IP, 1L, UID, 1L), authenticatedCost("DELETE openapi/v1/order"));
    assertEquals(Map.of(IP, 1L, UID, 1L), authenticatedCost("GET openapi/v1/asset/tradeFee"));
  }

  @Test
  void historyOrdersWeightDependsOnSymbol() {
    assertEquals(
        Map.of(IP, 10L, UID, 10L),
        POLICY
            .classify(
                new RateLimitRequest(
                    "GET openapi/v1/historyOrders", true, Map.of("symbol", "BTCPHP")))
            .getRequirements());
    assertEquals(Map.of(IP, 40L, UID, 40L), authenticatedCost("GET openapi/v1/historyOrders"));
  }

  @Test
  void walletEndpointsChargeOnlyTheBudgetTheProviderNames() {
    assertEquals(Map.of(UID, 100L), authenticatedCost("POST openapi/wallet/v1/withdraw/apply"));
    assertEquals(Map.of(IP, 10L), authenticatedCost("GET openapi/wallet/v1/deposit/address"));
    assertEquals(Map.of(IP, 2L), authenticatedCost("GET openapi/wallet/v1/deposit/history"));
    assertEquals(Map.of(IP, 2L), authenticatedCost("GET openapi/wallet/v1/withdraw/history"));
  }

  @Test
  void accountBoundOperationsAreExecutionPriority() {
    for (String key :
        new String[] {
          "GET openapi/v1/account",
          "POST openapi/v1/order",
          "DELETE openapi/v1/order",
          "POST openapi/wallet/v1/withdraw/apply",
          "POST openapi/fiat/v1/cash-out"
        }) {
      assertEquals(
          RateLimitPriority.EXECUTION,
          POLICY.classify(new RateLimitRequest(key, true)).getPriority(),
          key);
    }
  }

  /** Mutations are never replayed after a rate rejection; queries are. */
  @Test
  void onlyReadsAndFiatQueriesAreReplaySafe() {
    for (String key :
        new String[] {
          "POST openapi/v1/order",
          "DELETE openapi/v1/order",
          "POST openapi/v1/userDataStream",
          "PUT openapi/v1/userDataStream",
          "DELETE openapi/v1/userDataStream",
          "POST openapi/wallet/v1/withdraw/apply",
          "POST openapi/fiat/v1/cash-out"
        }) {
      assertFalse(
          POLICY.classify(new RateLimitRequest(key, true)).isReplayOnRateLimit(),
          "mutation must not be replayed: " + key);
    }
    for (String key :
        new String[] {
          "GET openapi/v1/account",
          "GET openapi/v1/order",
          "GET openapi/v1/openOrders",
          "GET openapi/wallet/v1/deposit/history",
          "POST openapi/fiat/v1/support-channel",
          "POST openapi/fiat/v2/history"
        }) {
      assertTrue(
          POLICY.classify(new RateLimitRequest(key, true)).isReplayOnRateLimit(),
          "read must be replay-safe: " + key);
    }
  }

  /** Reflection check: no reachable POST/PUT/DELETE other than the fiat queries replays. */
  @Test
  void reflectedMutationsNeverReplayExceptFiatQueries() {
    for (Method method : CoinsphAuthenticated.class.getMethods()) {
      String http = httpMethod(method);
      if (http == null || "GET".equals(http)) {
        continue;
      }
      String key = operationKey(CoinsphAuthenticated.class, method);
      boolean fiatQuery = key.endsWith("fiat/v1/support-channel") || key.endsWith("fiat/v2/history");
      assertEquals(
          fiatQuery,
          POLICY.classify(new RateLimitRequest(key, true)).isReplayOnRateLimit(),
          key);
    }
  }

  @Test
  void defaultSpecificationEnablesThePolicy() {
    ExchangeSpecification specification = new CoinsphExchange().getDefaultExchangeSpecification();
    assertTrue(specification.getResilience().isRateLimiterEnabled());
    assertSame(POLICY, specification.getResilience().getRateLimitPolicy());
    assertEquals(CoinsphRateLimitPolicy.NAMESPACE, POLICY.getNamespace());
  }

  private static Map<String, Long> depthCost(String limit) {
    Map<String, String> parameters =
        limit == null ? Map.of("symbol", "BTCPHP") : Map.of("symbol", "BTCPHP", "limit", limit);
    return POLICY
        .classify(new RateLimitRequest("GET openapi/v1/depth", false, parameters))
        .getRequirements();
  }

  private static Map<String, Long> authenticatedCost(String key) {
    return POLICY.classify(new RateLimitRequest(key, true)).getRequirements();
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
