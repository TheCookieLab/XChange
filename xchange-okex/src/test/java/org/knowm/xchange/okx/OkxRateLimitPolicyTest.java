package org.knowm.xchange.okx;

import static org.assertj.core.api.Assertions.assertThat;

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
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.client.ratelimit.RateLimitBudget;
import org.knowm.xchange.client.ratelimit.RateLimitBudget.ScopeKind;
import org.knowm.xchange.client.ratelimit.RateLimitOperation;
import org.knowm.xchange.client.ratelimit.RateLimitPolicy;
import org.knowm.xchange.client.ratelimit.RateLimitPriority;
import org.knowm.xchange.client.ratelimit.RateLimitRequest;
import org.knowm.xchange.okex.Okex;
import org.knowm.xchange.okex.OkexAuthenticated;
import org.knowm.xchange.okex.OkexExchange;
import si.mazi.rescu.ParamsDigest;

/** Offline checks of the OKX v5 rate-limit policy: classification, scopes, weights, enablement. */
public class OkxRateLimitPolicyTest {

  private static final Class<?>[] INTERFACES = {
    Okx.class, OkxAuthenticated.class, Okex.class, OkexAuthenticated.class
  };

  private static final RateLimitPolicy POLICY = OkxRateLimitPolicy.defaultPolicy();

  /** Methods whose economic effect is idempotent by request identity (README): cancellations. */
  private static final Set<String> REPLAY_SAFE_MUTATIONS =
      Set.of(
          "POST api/v5/trade/cancel-order",
          "POST api/v5/trade/cancel-batch-orders",
          "POST api/v5/trade/cancel-algos");

  // ---- classification ------------------------------------------------------------------------

  /** Every rescu method of every OKX interface classifies, so no new method can slip through. */
  @Test
  public void everyRescuInterfaceMethodIsClassified() {
    int checked = 0;
    for (Class<?> iface : INTERFACES) {
      for (Method method : iface.getDeclaredMethods()) {
        String httpMethod = httpMethod(method);
        if (method.isSynthetic() || httpMethod == null) {
          continue;
        }
        boolean authenticated = isAuthenticated(method);
        String key = operationKey(iface, method);
        RateLimitOperation operation = POLICY.classify(new RateLimitRequest(key, authenticated));
        assertThat(operation)
            .as("%s.%s classified as %s", iface.getSimpleName(), method.getName(), key)
            .isNotNull();
        assertThat(operation.getRequirements()).isNotEmpty();
        for (Map.Entry<String, Long> requirement : operation.getRequirements().entrySet()) {
          assertThat(POLICY.getBudget(requirement.getKey()))
              .as("budget %s of %s", requirement.getKey(), key)
              .isNotNull();
          assertThat(requirement.getValue()).isPositive();
        }
        boolean mutation = !"GET".equals(httpMethod);
        assertThat(operation.isReplayOnRateLimit())
            .as("replay of %s", key)
            .isEqualTo(!mutation || REPLAY_SAFE_MUTATIONS.contains(key));
        checked++;
      }
    }
    assertThat(checked).as("reflection found the interface methods").isGreaterThanOrEqualTo(78);
  }

  @Test
  public void publicMethodsAreMarketDataOnEgressBudgets() {
    int checked = 0;
    for (Method method : Okx.class.getDeclaredMethods()) {
      if (httpMethod(method) == null) {
        continue;
      }
      String key = operationKey(Okx.class, method);
      RateLimitOperation operation = POLICY.classify(new RateLimitRequest(key, false));
      assertThat(operation.getPriority()).as(key).isEqualTo(RateLimitPriority.MARKET_DATA);
      for (String budgetId : operation.getRequirements().keySet()) {
        assertThat(POLICY.getBudget(budgetId).getScopeKind())
            .as("scope of %s for %s", budgetId, key)
            .isEqualTo(ScopeKind.EGRESS);
      }
      assertThat(operation.getRequirements().values()).containsOnly(1L);
      checked++;
    }
    assertThat(checked).isGreaterThanOrEqualTo(8);
  }

  @Test
  public void authenticatedOnlyMethodsAreExecutionOnUserBudgets() {
    int checked = 0;
    for (Method method : OkxAuthenticated.class.getDeclaredMethods()) {
      if (httpMethod(method) == null) {
        continue;
      }
      String key = operationKey(OkxAuthenticated.class, method);
      RateLimitOperation operation = POLICY.classify(new RateLimitRequest(key, true));
      assertThat(operation.getPriority()).as(key).isEqualTo(RateLimitPriority.EXECUTION);
      for (String budgetId : operation.getRequirements().keySet()) {
        assertThat(POLICY.getBudget(budgetId).getScopeKind())
            .as("scope of %s for %s", budgetId, key)
            .isEqualTo(ScopeKind.USER);
      }
      checked++;
    }
    assertThat(checked).isGreaterThanOrEqualTo(35);
  }

  @Test
  public void batchAndOrderPlacementCarryTheDocumentedWeights() {
    RateLimitOperation ticker = classify("GET api/v5/market/ticker", false);
    assertThat(ticker.getRequirements()).containsExactly(Map.entry("okx.public.ticker", 1L));

    RateLimitOperation place = classify("POST api/v5/trade/order", true);
    assertThat(place.getRequirements())
        .containsEntry("okx.user.order-place", 1L)
        .containsEntry(OkxRateLimitPolicy.SUBACCOUNT_ORDERS, 1L)
        .hasSize(2);

    RateLimitOperation batch = classify("POST api/v5/trade/batch-orders", true);
    assertThat(batch.getRequirements())
        .containsEntry("okx.user.order-place-batch", 20L)
        .containsEntry(OkxRateLimitPolicy.SUBACCOUNT_ORDERS, 20L);

    RateLimitOperation amendBatch = classify("POST api/v5/trade/amend-batch-orders", true);
    assertThat(amendBatch.getRequirements())
        .containsEntry("okx.user.order-amend-batch", 20L)
        .containsEntry(OkxRateLimitPolicy.SUBACCOUNT_ORDERS, 20L);

    RateLimitOperation cancelBatch = classify("POST api/v5/trade/cancel-batch-orders", true);
    assertThat(cancelBatch.getRequirements())
        .containsExactly(Map.entry("okx.user.order-cancel-batch", 20L));

    RateLimitOperation cancelAlgos = classify("POST api/v5/trade/cancel-algos", true);
    assertThat(cancelAlgos.getRequirements())
        .containsExactly(Map.entry("okx.user.algo-cancel", 10L));
  }

  @Test
  public void documentedBudgetsAreDeclared() {
    assertBudget("okx.public.ticker", ScopeKind.EGRESS, 20, Duration.ofSeconds(2));
    assertBudget("okx.public.books", ScopeKind.EGRESS, 40, Duration.ofSeconds(2));
    assertBudget("okx.user.account-balance", ScopeKind.USER, 10, Duration.ofSeconds(2));
    assertBudget("okx.user.order-place", ScopeKind.USER, 60, Duration.ofSeconds(2));
    assertBudget("okx.user.order-place-batch", ScopeKind.USER, 300, Duration.ofSeconds(2));
    assertBudget("okx.user.order-cancel", ScopeKind.USER, 60, Duration.ofSeconds(2));
    assertBudget("okx.user.fills-history", ScopeKind.USER, 10, Duration.ofSeconds(2));
    assertBudget("okx.user.asset-currencies", ScopeKind.USER, 6, Duration.ofSeconds(1));
    assertBudget(
        OkxRateLimitPolicy.SUBACCOUNT_ORDERS, ScopeKind.USER, 1000, Duration.ofSeconds(2));
  }

  @Test
  public void getAndPostOnTheSameOrderPathAreIndependentBudgets() {
    RateLimitOperation details = classify("GET api/v5/trade/order", true);
    RateLimitOperation place = classify("POST api/v5/trade/order", true);
    assertThat(details.getRequirements()).containsOnlyKeys("okx.user.order-details");
    assertThat(place.getRequirements().keySet()).doesNotContain("okx.user.order-details");
    assertThat(details.isReplayOnRateLimit()).isTrue();
    assertThat(place.isReplayOnRateLimit()).isFalse();
  }

  @Test
  public void everyEconomicMutationIsNeverReplayed() {
    for (String key :
        List.of(
            "POST api/v5/trade/order",
            "POST api/v5/trade/batch-orders",
            "POST api/v5/trade/amend-order",
            "POST api/v5/trade/amend-batch-orders",
            "POST api/v5/trade/order-algo",
            "POST api/v5/trade/amend-algos",
            "POST api/v5/asset/withdrawal",
            "POST api/v5/asset/transfer",
            "POST api/v5/account/set-leverage",
            "POST api/v5/account/set-position-mode",
            "POST api/v5/account/position/margin-balance")) {
      assertThat(classify(key, true).isReplayOnRateLimit()).as(key).isFalse();
    }
  }

  @Test
  public void unknownOperationsStayUnclassified() {
    assertThat(POLICY.classify(new RateLimitRequest("GET api/v5/trade/new-endpoint", true)))
        .isNull();
    assertThat(POLICY.classify(new RateLimitRequest("GET api/v3/depth", false))).isNull();
    assertThat(POLICY.classify(new RateLimitRequest("PUT api/v5/trade/order", true))).isNull();
  }

  // ---- policy shape and enablement -----------------------------------------------------------

  @Test
  public void policyIsBoundedAndDocumented() {
    assertThat(POLICY.getNamespace()).isEqualTo(OkxRateLimitPolicy.NAMESPACE);
    assertThat(POLICY.getSource())
        .contains("https://www.okx.com/docs-v5/en/#overview-rate-limits")
        .contains("retrieved 2026-10-06");
    assertThat(POLICY.getMaxAttempts()).isEqualTo(3);
    assertThat(POLICY.maxWait(RateLimitPriority.EXECUTION)).isEqualTo(Duration.ofSeconds(5));
    assertThat(POLICY.pendingLimit(RateLimitPriority.EXECUTION))
        .isEqualTo(OkxRateLimitPolicy.EXECUTION_PENDING_LIMIT);
    assertThat(POLICY.pendingLimit(RateLimitPriority.MARKET_DATA))
        .isEqualTo(OkxRateLimitPolicy.MARKET_DATA_PENDING_LIMIT);
    assertThat(POLICY.getBudgets().stream().map(RateLimitBudget::getId).distinct().count())
        .isEqualTo(POLICY.getBudgets().size());
    assertThat(OkxRateLimitPolicy.create().getBudgets()).isEqualTo(POLICY.getBudgets());
  }

  @Test
  public void defaultSpecificationsEnableTheCoreLimiter() {
    for (ExchangeSpecification specification :
        List.of(
            new OkxExchange().getDefaultExchangeSpecification(),
            new OkexExchange().getDefaultExchangeSpecification())) {
      assertThat(specification.getResilience().isRateLimiterEnabled()).isTrue();
      assertThat(specification.getResilience().getRateLimitPolicy()).isSameAs(POLICY);
    }
  }

  // ---- helpers -------------------------------------------------------------------------------

  private static RateLimitOperation classify(String key, boolean authenticated) {
    RateLimitOperation operation = POLICY.classify(new RateLimitRequest(key, authenticated));
    assertThat(operation).as(key).isNotNull();
    return operation;
  }

  private static void assertBudget(String id, ScopeKind scope, long capacity, Duration period) {
    RateLimitBudget budget = POLICY.getBudget(id);
    assertThat(budget).as(id).isNotNull();
    assertThat(budget.getScopeKind()).isEqualTo(scope);
    assertThat(budget.getCapacity()).isEqualTo(capacity);
    assertThat(budget.getPeriod()).isEqualTo(period);
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

  /** Mirrors the core operation key: {@code METHOD <interface path>/<method path>} normalized. */
  static String operationKey(Class<?> iface, Method method) {
    List<String> segments = new ArrayList<>();
    for (String path : new String[] {pathOf(iface), pathOf(method)}) {
      for (String segment : path.split("/")) {
        if (!segment.isEmpty()) {
          segments.add(segment);
        }
      }
    }
    return httpMethod(method) + " " + segments.stream().collect(Collectors.joining("/"));
  }

  private static String pathOf(java.lang.reflect.AnnotatedElement element) {
    Path path = element.getAnnotation(Path.class);
    return path == null ? "" : path.value();
  }
}
