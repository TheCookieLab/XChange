package org.knowm.xchange.uniswap.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.client.ratelimit.RateLimitBudget;
import org.knowm.xchange.client.ratelimit.RateLimitOperation;
import org.knowm.xchange.client.ratelimit.RateLimitPolicy;
import org.knowm.xchange.client.ratelimit.RateLimitPriority;
import org.knowm.xchange.client.ratelimit.RateLimitRequest;

/** Classification, weights and replay safety of the JSON-RPC rate-limit policy. */
class UniswapRateLimitPolicyTest {

  private static final Map<String, Long> COMPUTE_UNITS =
      Map.ofEntries(
          Map.entry("eth_chainId", 5L),
          Map.entry("eth_blockNumber", 10L),
          Map.entry("eth_getCode", 20L),
          Map.entry("eth_getBalance", 20L),
          Map.entry("eth_call", 26L),
          Map.entry("eth_getTransactionCount", 20L),
          Map.entry("eth_estimateGas", 20L),
          Map.entry("eth_feeHistory", 10L),
          Map.entry("eth_getBlockByNumber", 20L),
          Map.entry("eth_maxPriorityFeePerGas", 10L),
          Map.entry("eth_getTransactionByHash", 20L),
          Map.entry("eth_getTransactionReceipt", 20L),
          Map.entry("eth_sendRawTransaction", 50L));

  private final RateLimitPolicy policy = UniswapRateLimitPolicy.defaultPolicy();

  @Test
  void classifiesExactlyTheDocumentedMethods() {
    assertThat(UniswapRateLimitPolicy.methods())
        .containsExactlyInAnyOrderElementsOf(COMPUTE_UNITS.keySet());
    assertThat(UniswapRateLimitPolicy.methods()).hasSize(13);
  }

  @Test
  void everyMethodCarriesItsComputeUnitWeightOnTheSingleBudget() {
    for (Map.Entry<String, Long> expected : COMPUTE_UNITS.entrySet()) {
      RateLimitOperation operation =
          policy.classify(new RateLimitRequest(expected.getKey(), false));
      assertThat(operation).as(expected.getKey()).isNotNull();
      assertThat(operation.getRequirements())
          .as(expected.getKey())
          .containsExactly(Map.entry(UniswapRateLimitPolicy.COMPUTE_UNITS, expected.getValue()));
    }
  }

  @Test
  void onlyTheBroadcastIsNeverReplayedAfterARateRejection() {
    for (String method : UniswapRateLimitPolicy.methods()) {
      RateLimitOperation operation = policy.classify(new RateLimitRequest(method, false));
      assertThat(operation.isReplayOnRateLimit())
          .as(method)
          .isEqualTo(!"eth_sendRawTransaction".equals(method));
    }
  }

  @Test
  void broadcastAndNonceAndGasReadsRunAtExecutionPriority() {
    Set<String> execution =
        Set.of(
            "eth_sendRawTransaction",
            "eth_getTransactionCount",
            "eth_estimateGas",
            "eth_feeHistory",
            "eth_getBlockByNumber",
            "eth_maxPriorityFeePerGas",
            "eth_getTransactionByHash",
            "eth_getTransactionReceipt");
    for (String method : UniswapRateLimitPolicy.methods()) {
      assertThat(policy.classify(new RateLimitRequest(method, false)).getPriority())
          .as(method)
          .isEqualTo(
              execution.contains(method)
                  ? RateLimitPriority.EXECUTION
                  : RateLimitPriority.MARKET_DATA);
    }
  }

  @Test
  void unknownMethodsAreNotClassified() {
    assertThat(policy.classify(new RateLimitRequest("eth_gasPrice", false))).isNull();
    assertThat(policy.classify(new RateLimitRequest("personal_unlockAccount", false))).isNull();
    assertThat(policy.classify(new RateLimitRequest("eth_getBalance ", false))).isNull();
  }

  @Test
  void startupVerificationFitsTheInitialBurst() {
    RateLimitBudget budget = policy.getBudget(UniswapRateLimitPolicy.COMPUTE_UNITS);
    long startup =
        COMPUTE_UNITS.get("eth_chainId")
            + COMPUTE_UNITS.get("eth_blockNumber")
            + 4 * COMPUTE_UNITS.get("eth_getCode");
    assertThat(budget.getScopeKind()).isEqualTo(RateLimitBudget.ScopeKind.USER);
    assertThat(budget.getCapacity())
        .isEqualTo(UniswapRateLimitPolicy.CAPACITY)
        .isGreaterThanOrEqualTo(startup);
    assertThat(policy.getBudgets()).hasSize(1);
  }

  @Test
  void sourceNamesTheProviderDocumentationAndLabelsClientSidePacing() {
    assertThat(policy.getNamespace()).isEqualTo(UniswapRateLimitPolicy.NAMESPACE);
    assertThat(policy.getVersion()).isEqualTo("2026-10-06");
    assertThat(policy.getSource())
        .contains("https://www.alchemy.com/docs/reference/compute-unit-costs")
        .contains("https://www.alchemy.com/docs/reference/throughput")
        .contains("https://eips.ethereum.org/EIPS/eip-1474")
        .contains("Client-side pacing")
        .contains("2026-10-06");
  }
}
