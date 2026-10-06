package org.knowm.xchange.cryptocom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
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

/** Offline checks of the Crypto.com rate-limit policy: classification, scope, replay. */
class CryptoComRateLimitPolicyTest {

  private static final RateLimitPolicy POLICY = CryptoComRateLimitPolicy.defaultPolicy();

  /** Every reachable method of the rescu interface classifies, one budget per method. */
  @Test
  void everyRescuInterfaceMethodIsClassified() {
    int checked = 0;
    Set<String> budgetIds = new HashSet<>();
    for (Method method : CryptoCom.class.getMethods()) {
      if (method.isSynthetic() || httpMethod(method) == null) {
        continue;
      }
      String key = operationKey(method);
      RateLimitOperation operation =
          POLICY.classify(new RateLimitRequest(key, isAuthenticated(method)));
      assertNotNull(operation, "unclassified " + method.getName() + ": " + key);
      assertEquals(1, operation.getRequirements().size(), key);
      assertEquals(1L, operation.getRequirements().values().iterator().next(), key);
      assertTrue(
          budgetIds.add(operation.getRequirements().keySet().iterator().next()),
          "each method owns its budget: " + key);
      checked++;
    }
    assertEquals(27, checked, "7 public + 20 private rescu methods");
    assertEquals(27, POLICY.getBudgets().size(), "one budget per classified method");
  }

  @Test
  void unknownOperationIsUnclassified() {
    assertNull(POLICY.classify(new RateLimitRequest("GET exchange/v1/public/unknown", false)));
    assertNull(POLICY.classify(new RateLimitRequest("POST exchange/v1/private/unknown", false)));
    assertNull(
        POLICY.classify(new RateLimitRequest("GET exchange/v1/private/user-balance", false)));
    assertNull(POLICY.classify(new RateLimitRequest("POST exchange/v1/public/get-book", false)));
  }

  @Test
  void publicMethodsAreEgressMarketDataAndReplaySafe() {
    for (Method method : CryptoCom.class.getMethods()) {
      if (!"GET".equals(httpMethod(method))) {
        continue;
      }
      RateLimitOperation operation = POLICY.classify(new RateLimitRequest(operationKey(method), false));
      assertEquals(RateLimitPriority.MARKET_DATA, operation.getPriority(), method.getName());
      assertTrue(operation.isReplayOnRateLimit(), method.getName());
      RateLimitBudget budget = budget(operation);
      assertEquals(ScopeKind.EGRESS, budget.getScopeKind(), method.getName());
      assertEquals(100, budget.getCapacity(), method.getName());
    }
  }

  @Test
  void privateMethodsAreUserScopedExecution() {
    for (Method method : CryptoCom.class.getMethods()) {
      if (!"POST".equals(httpMethod(method))) {
        continue;
      }
      RateLimitOperation operation = POLICY.classify(new RateLimitRequest(operationKey(method), false));
      assertEquals(RateLimitPriority.EXECUTION, operation.getPriority(), method.getName());
      assertEquals(ScopeKind.USER, budget(operation).getScopeKind(), method.getName());
    }
  }

  /** Documented per-method private figures: 15/100ms, 30/100ms, 1/s, and 3/100ms for the rest. */
  @Test
  void privateBudgetsFollowTheDocumentedPerMethodLimits() {
    assertEquals(15, privateBudget("private/create-order").getCapacity());
    assertEquals(15, privateBudget("private/cancel-order").getCapacity());
    assertEquals(15, privateBudget("private/cancel-all-orders").getCapacity());
    assertEquals(30, privateBudget("private/get-order-detail").getCapacity());
    assertEquals(1, privateBudget("private/get-trades").getCapacity());
    assertEquals(1, privateBudget("private/get-order-history").getCapacity());
    assertEquals(3, privateBudget("private/user-balance").getCapacity());
    assertEquals(3, privateBudget("private/get-open-orders").getCapacity());
    assertEquals(3, privateBudget("private/advanced/create-order").getCapacity());
  }

  @Test
  void publicAndPrivateTradesAreSeparateBudgets() {
    String publicBudget =
        budget(POLICY.classify(new RateLimitRequest("GET exchange/v1/public/get-trades", false)))
            .getId();
    String privateBudget = privateBudget("private/get-trades").getId();
    assertFalse(publicBudget.equals(privateBudget));
  }

  /** Order placement, cancellation, withdrawal and position close are never blind-replayed. */
  @Test
  void mutationsAreNotReplayedAndQueriesAre() {
    for (String path :
        new String[] {
          "private/create-order",
          "private/advanced/create-order",
          "private/cancel-order",
          "private/cancel-all-orders",
          "private/create-withdrawal",
          "private/close-position"
        }) {
      assertFalse(
          POLICY.classify(new RateLimitRequest("POST exchange/v1/" + path, false))
              .isReplayOnRateLimit(),
          "mutation must not be replayed: " + path);
    }
    for (String path :
        new String[] {
          "private/user-balance",
          "private/get-open-orders",
          "private/get-order-detail",
          "private/get-order-history",
          "private/get-trades",
          "private/get-deposit-address",
          "private/get-deposit-history",
          "private/get-withdrawal-history",
          "private/get-positions",
          "private/get-accounts",
          "private/get-fee-rate",
          "private/get-fee-credit-balances",
          "private/user-balance-history",
          "private/get-transactions"
        }) {
      assertTrue(
          POLICY.classify(new RateLimitRequest("POST exchange/v1/" + path, false))
              .isReplayOnRateLimit(),
          "query must be replay-safe: " + path);
    }
  }

  @Test
  void defaultSpecificationEnablesThePolicy() {
    ExchangeSpecification specification =
        new CryptoComExchange().getDefaultExchangeSpecification();
    assertTrue(specification.getResilience().isRateLimiterEnabled());
    assertSame(POLICY, specification.getResilience().getRateLimitPolicy());
    assertEquals(CryptoComRateLimitPolicy.NAMESPACE, POLICY.getNamespace());
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

  private static RateLimitBudget privateBudget(String path) {
    return budget(POLICY.classify(new RateLimitRequest("POST exchange/v1/" + path, false)));
  }

  // ---- reflection helpers --------------------------------------------------------------------

  private static String httpMethod(Method method) {
    if (method.isAnnotationPresent(GET.class)) {
      return "GET";
    }
    if (method.isAnnotationPresent(POST.class)) {
      return "POST";
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
  private static String operationKey(Method method) {
    List<String> parts = new ArrayList<>();
    parts.add(CryptoCom.class.getAnnotation(Path.class).value());
    Path methodPath = method.getAnnotation(Path.class);
    if (methodPath != null) {
      parts.add(methodPath.value());
    }
    String path = String.join("/", parts).replaceAll("/+", "/").replaceAll("^/", "");
    return httpMethod(method) + " " + path;
  }
}
