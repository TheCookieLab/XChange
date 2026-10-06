package org.knowm.xchange.gateio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.client.ratelimit.RateLimitBudget;
import org.knowm.xchange.client.ratelimit.RateLimitBudget.ScopeKind;
import org.knowm.xchange.client.ratelimit.RateLimitOperation;
import org.knowm.xchange.client.ratelimit.RateLimitPolicy;
import org.knowm.xchange.client.ratelimit.RateLimitPriority;
import org.knowm.xchange.client.ratelimit.RateLimitRequest;
import si.mazi.rescu.ParamsDigest;

/** Offline checks of the Gate.io rate-limit policy: classification, scope, weights, replay. */
class GateioRateLimitPolicyTest {

  private static final RateLimitPolicy POLICY = GateioRateLimitPolicy.defaultPolicy();

  private static final Set<String> MUTATIONS =
      Set.of(
          "POST api/v4/spot/orders",
          "POST api/v4/spot/batch_orders",
          "PATCH api/v4/spot/orders/{order_id}",
          "DELETE api/v4/spot/orders/{order_id}",
          "DELETE api/v4/spot/orders",
          "POST api/v4/spot/cancel_batch_orders",
          "POST api/v4/spot/countdown_cancel_all",
          "POST api/v4/futures/{settle}/orders",
          "PUT api/v4/futures/{settle}/orders/{order_id}",
          "DELETE api/v4/futures/{settle}/orders/{order_id}",
          "POST api/v4/futures/{settle}/positions/{contract}/leverage",
          "POST api/v4/withdrawals");

  /** Every reachable method of both rescu interfaces classifies. */
  @Test
  void everyRescuInterfaceMethodIsClassified() {
    int checked = 0;
    for (Class<?> api : new Class<?>[] {Gateio.class, GateioV4Authenticated.class}) {
      for (Method method : api.getMethods()) {
        if (method.isSynthetic() || httpMethod(method) == null) {
          continue;
        }
        String key = operationKey(api, method);
        RateLimitOperation operation =
            POLICY.classify(new RateLimitRequest(key, isAuthenticated(method)));
        assertNotNull(operation, "unclassified " + method.getName() + ": " + key);
        assertEquals(1, operation.getRequirements().size(), key);
        assertEquals(1L, operation.getRequirements().values().iterator().next(), key);
        checked++;
      }
    }
    assertEquals(43, checked, "rescu methods of Gateio and GateioV4Authenticated");
  }

  @Test
  void unknownOperationIsUnclassified() {
    assertNull(POLICY.classify(new RateLimitRequest("GET api/v4/spot/unknown", false)));
    assertNull(POLICY.classify(new RateLimitRequest("POST api/v4/spot/time", false)));
    assertNull(POLICY.classify(new RateLimitRequest("GET api/v4/withdrawals", true)));
  }

  @Test
  void publicMethodsAreEgressMarketDataAndReplaySafe() {
    for (Method method : Gateio.class.getMethods()) {
      if (httpMethod(method) == null) {
        continue;
      }
      RateLimitOperation operation =
          POLICY.classify(new RateLimitRequest(operationKey(Gateio.class, method), false));
      assertEquals(RateLimitPriority.MARKET_DATA, operation.getPriority(), method.getName());
      assertTrue(operation.isReplayOnRateLimit(), method.getName());
      RateLimitBudget budget = budget(operation);
      assertEquals(ScopeKind.EGRESS, budget.getScopeKind(), method.getName());
      assertEquals(200, budget.getCapacity(), method.getName());
    }
  }

  @Test
  void authenticatedMethodsAreUserScopedExecution() {
    for (Method method : GateioV4Authenticated.class.getMethods()) {
      if (httpMethod(method) == null) {
        continue;
      }
      assertTrue(isAuthenticated(method), method.getName());
      RateLimitOperation operation =
          POLICY.classify(
              new RateLimitRequest(operationKey(GateioV4Authenticated.class, method), true));
      assertEquals(RateLimitPriority.EXECUTION, operation.getPriority(), method.getName());
      assertEquals(ScopeKind.USER, budget(operation).getScopeKind(), method.getName());
    }
  }

  /** Order placement, amend, cancel, batch, countdown, leverage and withdrawal are not replayed. */
  @Test
  void mutationsAreNotReplayedAndReadsAre() {
    List<String> seen = new ArrayList<>();
    for (Method method : GateioV4Authenticated.class.getMethods()) {
      if (httpMethod(method) == null) {
        continue;
      }
      String key = operationKey(GateioV4Authenticated.class, method);
      boolean replay = POLICY.classify(new RateLimitRequest(key, true)).isReplayOnRateLimit();
      if (MUTATIONS.contains(key)) {
        assertFalse(replay, "mutation must not be replayed: " + key);
        seen.add(key);
      } else {
        assertEquals("GET", httpMethod(method), "unexpected non-GET " + key);
        assertTrue(replay, "read must be replay-safe: " + key);
      }
    }
    assertEquals(MUTATIONS.size(), new java.util.HashSet<>(seen).size(), "all mutations present");
  }

  /** Documented weights: shared order budgets and per-endpoint 200 per 10 s for the rest. */
  @Test
  void budgetsFollowTheDocumentedLimits() {
    assertBudget("POST api/v4/spot/orders", GateioRateLimitPolicy.SPOT_ORDER_WRITE, 10, 1);
    assertBudget("POST api/v4/spot/batch_orders", GateioRateLimitPolicy.SPOT_ORDER_WRITE, 10, 1);
    assertBudget(
        "PATCH api/v4/spot/orders/{order_id}", GateioRateLimitPolicy.SPOT_ORDER_WRITE, 10, 1);
    assertBudget(
        "DELETE api/v4/spot/orders/{order_id}", GateioRateLimitPolicy.SPOT_ORDER_CANCEL, 200, 1);
    assertBudget("DELETE api/v4/spot/orders", GateioRateLimitPolicy.SPOT_ORDER_CANCEL, 200, 1);
    assertBudget(
        "POST api/v4/spot/cancel_batch_orders", GateioRateLimitPolicy.SPOT_ORDER_CANCEL, 200, 1);
    assertBudget(
        "POST api/v4/futures/{settle}/orders", GateioRateLimitPolicy.FUTURES_ORDER_WRITE, 100, 1);
    assertBudget(
        "PUT api/v4/futures/{settle}/orders/{order_id}",
        GateioRateLimitPolicy.FUTURES_ORDER_WRITE,
        100,
        1);
    assertBudget(
        "DELETE api/v4/futures/{settle}/orders/{order_id}",
        GateioRateLimitPolicy.FUTURES_ORDER_CANCEL,
        200,
        1);
    assertBudget("POST api/v4/withdrawals", GateioRateLimitPolicy.WITHDRAW, 1, 3);
    RateLimitBudget balances =
        budget(POLICY.classify(new RateLimitRequest("GET api/v4/spot/accounts", true)));
    assertEquals(200, balances.getCapacity());
    assertEquals(ScopeKind.USER, balances.getScopeKind());
  }

  /** Spot and futures single GET order and its DELETE are separate endpoints and budgets. */
  @Test
  void sameKeyDifferentMethodsAreSeparateBudgets() {
    String get =
        budget(POLICY.classify(new RateLimitRequest("GET api/v4/spot/orders/{order_id}", true)))
            .getId();
    String cancel =
        budget(POLICY.classify(new RateLimitRequest("DELETE api/v4/spot/orders/{order_id}", true)))
            .getId();
    assertFalse(get.equals(cancel));
  }

  @Test
  void defaultSpecificationEnablesThePolicy() {
    ExchangeSpecification specification = new GateioExchange().getDefaultExchangeSpecification();
    assertTrue(specification.getResilience().isRateLimiterEnabled());
    assertSame(POLICY, specification.getResilience().getRateLimitPolicy());
    assertEquals(GateioRateLimitPolicy.NAMESPACE, POLICY.getNamespace());
  }

  private static void assertBudget(String key, String id, long capacity, long seconds) {
    RateLimitBudget budget = budget(POLICY.classify(new RateLimitRequest(key, true)));
    assertEquals(id, budget.getId(), key);
    assertEquals(capacity, budget.getCapacity(), key);
    assertEquals(Duration.ofSeconds(seconds), budget.getPeriod(), key);
    assertEquals(ScopeKind.USER, budget.getScopeKind(), key);
  }

  private static RateLimitBudget budget(RateLimitOperation operation) {
    String id = operation.getRequirements().keySet().iterator().next();
    for (RateLimitBudget budget : POLICY.getBudgets()) {
      if (budget.getId().equals(id)) {
        return budget;
      }
    }
    throw new AssertionError("no budget " + id);
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
    if (method.isAnnotationPresent(PATCH.class)) {
      return "PATCH";
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
  private static String operationKey(Class<?> api, Method method) {
    List<String> parts = new ArrayList<>();
    parts.add(api.getAnnotation(Path.class).value());
    Path methodPath = method.getAnnotation(Path.class);
    if (methodPath != null) {
      parts.add(methodPath.value());
    }
    String path = String.join("/", parts).replaceAll("/+", "/").replaceAll("^/", "");
    return httpMethod(method) + " " + path;
  }
}
