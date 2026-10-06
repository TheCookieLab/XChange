package org.knowm.xchange.blockchain;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.Test;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.client.ratelimit.RateLimitBudget;
import org.knowm.xchange.client.ratelimit.RateLimitBudget.ScopeKind;
import org.knowm.xchange.client.ratelimit.RateLimitFeedback;
import org.knowm.xchange.client.ratelimit.RateLimitOperation;
import org.knowm.xchange.client.ratelimit.RateLimitPolicy;
import org.knowm.xchange.client.ratelimit.RateLimitPriority;
import org.knowm.xchange.client.ratelimit.RateLimitRequest;

/** Offline checks of the Blockchain.com rate-limit policy: classification, budgets, enablement. */
public class BlockchainRateLimitPolicyTest {

  private static final RateLimitPolicy POLICY = BlockchainRateLimitPolicy.defaultPolicy();

  private static final String REST = BlockchainRateLimitPolicy.REST_PACING;

  /** Every operation key of the module's rescu interfaces with its expected priority. */
  private static final Map<String, RateLimitPriority> EXPECTED =
      Map.ofEntries(
          Map.entry("GET v3/exchange/symbols", RateLimitPriority.MARKET_DATA),
          Map.entry("GET v3/exchange/l3/{symbol}", RateLimitPriority.MARKET_DATA),
          Map.entry("GET v3/exchange/accounts", RateLimitPriority.EXECUTION),
          Map.entry("GET v3/exchange/fees", RateLimitPriority.EXECUTION),
          Map.entry("GET v3/exchange/deposits", RateLimitPriority.EXECUTION),
          Map.entry("POST v3/exchange/deposits/{symbol}", RateLimitPriority.EXECUTION),
          Map.entry("GET v3/exchange/withdrawals", RateLimitPriority.EXECUTION),
          Map.entry("POST v3/exchange/withdrawals", RateLimitPriority.EXECUTION),
          Map.entry("GET v3/exchange/orders", RateLimitPriority.EXECUTION),
          Map.entry("GET v3/exchange/orders/{orderId}", RateLimitPriority.EXECUTION),
          Map.entry("POST v3/exchange/orders", RateLimitPriority.EXECUTION),
          Map.entry("DELETE v3/exchange/orders", RateLimitPriority.EXECUTION),
          Map.entry("DELETE v3/exchange/orders/{orderId}", RateLimitPriority.EXECUTION),
          Map.entry("GET v3/exchange/trades", RateLimitPriority.EXECUTION));

  /** Every method reachable through the authenticated proxy, inherited ones included. */
  @Test
  public void everyRescuMethodIsClassified() {
    Set<String> keys = new TreeSet<>();
    int methods = 0;
    for (Method method : BlockchainAuthenticated.class.getMethods()) {
      if (method.isSynthetic() || httpMethod(method) == null) {
        continue;
      }
      methods++;
      String key = operationKey(method);
      keys.add(key);
      RateLimitOperation operation = POLICY.classify(new RateLimitRequest(key, false));
      assertThat(operation).as("unclassified %s (%s)", method, key).isNotNull();
      assertThat(operation.getPriority()).as(key).isEqualTo(EXPECTED.get(key));
      assertThat(operation.getRequirements()).as(key).isEqualTo(Map.of(REST, 1L));
      assertThat(operation.isReplayOnRateLimit())
          .as("replay only for reads and cancellations: " + key)
          .isEqualTo(!"POST".equals(httpMethod(method)));
    }
    assertThat(methods).as("rescu methods of the module").isEqualTo(15);
    assertThat(keys).as("operation keys").containsExactlyInAnyOrderElementsOf(EXPECTED.keySet());
    for (Method method : Blockchain.class.getMethods()) {
      if (httpMethod(method) != null) {
        assertThat(keys).contains(operationKey(method));
      }
    }
  }

  @Test
  public void economicMutationsAreNeverReplayed() {
    for (String key :
        List.of(
            "POST v3/exchange/orders",
            "POST v3/exchange/withdrawals",
            "POST v3/exchange/deposits/{symbol}")) {
      assertThat(POLICY.classify(new RateLimitRequest(key, false)).isReplayOnRateLimit())
          .as(key)
          .isFalse();
    }
  }

  @Test
  public void authenticationFlagDoesNotChangeClassification() {
    // The API token travels as a default header, so rescu never reports a ParamsDigest.
    for (String key : EXPECTED.keySet()) {
      assertThat(POLICY.classify(new RateLimitRequest(key, true)))
          .isEqualTo(POLICY.classify(new RateLimitRequest(key, false)));
    }
  }

  @Test
  public void unknownOperationsAreUnclassified() {
    assertThat(POLICY.classify(new RateLimitRequest("GET v3/exchange/unknown", false))).isNull();
    assertThat(POLICY.classify(new RateLimitRequest("PUT v3/exchange/orders", false))).isNull();
    assertThat(POLICY.classify(new RateLimitRequest("POST v3/exchange/symbols", false))).isNull();
    assertThat(POLICY.classify(new RateLimitRequest("GET /v3/exchange/orders", false))).isNull();
  }

  @Test
  public void singleSharedBudgetPreservesLegacyPacing() {
    assertThat(POLICY.getBudgets()).hasSize(1);
    RateLimitBudget budget = POLICY.getBudget(REST);
    assertThat(budget).isNotNull();
    assertThat(budget.getScopeKind()).isEqualTo(ScopeKind.USER);
    assertThat(budget.getLaw()).isEqualTo(RateLimitBudget.Law.TOKEN_BUCKET);
    assertThat(budget.getCapacity()).isEqualTo(10);
    assertThat(budget.getRefillAmount()).isEqualTo(10);
    assertThat(budget.getPeriod()).isEqualTo(Duration.ofSeconds(1));
  }

  @Test
  public void policyDocumentsItsProvenance() {
    assertThat(POLICY.getNamespace()).isEqualTo("blockchain.exchange");
    assertThat(POLICY.getSource())
        .contains("https://api.blockchain.com/v3/")
        .contains("https://exchange.blockchain.com/api/")
        .contains("2026-10-06")
        .contains("pre-CF-683");
    assertThat(
            POLICY.getFeedbackInterpreter().interpret(429, name -> null, Instant.EPOCH).getKind())
        .isEqualTo(RateLimitFeedback.Kind.RATE_REJECTED);
    assertThat(
            POLICY.getFeedbackInterpreter().interpret(418, name -> null, Instant.EPOCH).getKind())
        .isEqualTo(RateLimitFeedback.Kind.NONE);
  }

  @Test
  public void defaultSpecificationEnablesThePolicy() {
    ExchangeSpecification specification = new BlockchainExchange().getDefaultExchangeSpecification();
    assertThat(specification.getResilience().isRateLimiterEnabled()).isTrue();
    assertThat(specification.getResilience().getRateLimitPolicy())
        .isSameAs(BlockchainRateLimitPolicy.defaultPolicy());
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

  /** {@code "<METHOD> <interface path>/<method path>"}, no leading slash, slashes collapsed. */
  private static String operationKey(Method method) {
    List<String> parts = new ArrayList<>();
    parts.add(method.getDeclaringClass().getAnnotation(Path.class).value());
    Path methodPath = method.getAnnotation(Path.class);
    if (methodPath != null) {
      parts.add(methodPath.value());
    }
    String path = String.join("/", parts).replaceAll("/+", "/").replaceAll("^/", "");
    return httpMethod(method) + " " + path;
  }
}
