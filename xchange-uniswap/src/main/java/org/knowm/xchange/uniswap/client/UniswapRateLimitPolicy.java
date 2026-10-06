package org.knowm.xchange.uniswap.client;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.knowm.xchange.client.ratelimit.RateLimitBudget;
import org.knowm.xchange.client.ratelimit.RateLimitBudget.ScopeKind;
import org.knowm.xchange.client.ratelimit.RateLimitFeedbackInterpreter;
import org.knowm.xchange.client.ratelimit.RateLimitOperation;
import org.knowm.xchange.client.ratelimit.RateLimitPolicy;
import org.knowm.xchange.client.ratelimit.RateLimitPriority;
import org.knowm.xchange.client.ratelimit.RateLimitRequest;

/**
 * Default rate-limit policy of the Ethereum JSON-RPC node behind {@link UniswapNodeClient}.
 *
 * <p>The node endpoint is operator-chosen, so the provider's real quota is unknown to this module.
 * The policy therefore combines two clearly separated parts:
 *
 * <ul>
 *   <li><b>Client-side pacing (this module's choice, not a provider quota).</b> One token bucket of
 *       {@value #CAPACITY} compute units refilling {@value #REFILL} compute units per second per
 *       user scope ({@value #COMPUTE_UNITS}). It is deliberately half of the Alchemy free-tier
 *       throughput so that the default stays inside the smallest commonly documented plan; it is
 *       sized so that the startup verification (chain id, block number and four code lookups, 95
 *       compute units) always fits in the initial burst. Operators with a larger plan or a private
 *       node replace it through {@code ExchangeSpecification.getResilience().setRateLimitPolicy}.
 *   <li><b>Provider feedback.</b> HTTP {@code 429} (honouring {@code Retry-After}) and the JSON-RPC
 *       error code {@value #LIMIT_EXCEEDED_CODE} ({@code Limit exceeded}, EIP-1474) are rate
 *       rejections owned by the core: cooldown, bounded replay of replay-safe reads with fresh
 *       admission, then a terminal {@code RateLimitTerminatedException}.
 * </ul>
 *
 * <p>Compute-unit weights of the JSON-RPC methods used by {@link UniswapNodeClient} are the
 * documented Alchemy costs (the only provider that publishes a per-method table), taken from the
 * <i>throughput</i> column where the table lists one, because the budget paces throughput: {@code
 * eth_chainId} is 0 compute units but 5 throughput compute units, and {@code
 * eth_sendRawTransaction} is 40 compute units (billing) but 50 throughput compute units. Every
 * other method has no separate throughput figure, so its listed compute-unit cost applies.
 *
 * <p>Sources (retrieved 2026-10-06):
 *
 * <ul>
 *   <li>Alchemy throughput: https://www.alchemy.com/docs/reference/throughput (429 with {@code
 *       Retry-After}; free tier 300 compute units per second)
 *   <li>Alchemy compute-unit costs: https://www.alchemy.com/docs/reference/compute-unit-costs
 *   <li>EIP-1474 error codes: https://eips.ethereum.org/EIPS/eip-1474 ({@code -32005} limit
 *       exceeded)
 * </ul>
 *
 * <p>Replay: every read may be replayed after a confirmed rate rejection. {@code
 * eth_sendRawTransaction} is an economic mutation and is never replayed by the rate limiter; the
 * trade service reconciles an ambiguous broadcast by transaction hash instead.
 *
 * <p>Methods outside the allow list are rejected before anything is sent (their weight is unknown);
 * the allow list is pinned against the methods of {@link UniswapNodeClient} by a test.
 *
 * @since 1.0.3
 */
public final class UniswapRateLimitPolicy {

  /** Policy namespace. */
  public static final String NAMESPACE = "uniswap.rpc";

  /** Policy version, the retrieval date of its sources. */
  public static final String VERSION = "2026-10-06";

  /** Budget id of the node's compute-unit allowance (client-side pacing). */
  public static final String COMPUTE_UNITS = "uniswap.rpc.compute-units";

  /** JSON-RPC error code {@code Limit exceeded} (EIP-1474). */
  public static final int LIMIT_EXCEEDED_CODE = -32005;

  /** Burst capacity of the client-side pacing bucket, in compute units. */
  static final long CAPACITY = 600L;

  /** Refill of the client-side pacing bucket, in compute units per second. */
  static final long REFILL = 150L;

  private static final Map<String, Rule> RULES = rules();

  private UniswapRateLimitPolicy() {}

  /**
   * Creates the default policy.
   *
   * @return the immutable policy
   */
  public static RateLimitPolicy defaultPolicy() {
    return new RateLimitPolicy(
        NAMESPACE,
        VERSION,
        "Client-side pacing (half of the Alchemy free tier, not a provider quota) plus provider"
            + " feedback; compute-unit weights from"
            + " https://www.alchemy.com/docs/reference/compute-unit-costs and"
            + " https://www.alchemy.com/docs/reference/throughput, error code -32005 from"
            + " https://eips.ethereum.org/EIPS/eip-1474, all retrieved 2026-10-06",
        List.of(
            RateLimitBudget.tokenBucket(
                COMPUTE_UNITS, ScopeKind.USER, CAPACITY, REFILL, Duration.ofSeconds(1))),
        UniswapRateLimitPolicy::classify,
        RateLimitFeedbackInterpreter.statuses(Set.of(429), Set.of()));
  }

  /**
   * Names of the JSON-RPC methods this policy classifies.
   *
   * @return the immutable method names
   */
  static Set<String> methods() {
    return RULES.keySet();
  }

  private static RateLimitOperation classify(RateLimitRequest request) {
    Rule rule = RULES.get(request.getOperationKey());
    if (rule == null) {
      return null;
    }
    return new RateLimitOperation(
        request.getOperationKey(),
        rule.priority,
        Map.of(COMPUTE_UNITS, rule.computeUnits),
        rule.replayable);
  }

  private static Map<String, Rule> rules() {
    Map<String, Rule> rules = new LinkedHashMap<>();
    rules.put("eth_chainId", new Rule(RateLimitPriority.MARKET_DATA, 5L, true));
    rules.put("eth_blockNumber", new Rule(RateLimitPriority.MARKET_DATA, 10L, true));
    rules.put("eth_getCode", new Rule(RateLimitPriority.MARKET_DATA, 20L, true));
    rules.put("eth_getBalance", new Rule(RateLimitPriority.MARKET_DATA, 20L, true));
    rules.put("eth_call", new Rule(RateLimitPriority.MARKET_DATA, 26L, true));
    rules.put("eth_getTransactionCount", new Rule(RateLimitPriority.EXECUTION, 20L, true));
    rules.put("eth_estimateGas", new Rule(RateLimitPriority.EXECUTION, 20L, true));
    rules.put("eth_feeHistory", new Rule(RateLimitPriority.EXECUTION, 10L, true));
    rules.put("eth_getBlockByNumber", new Rule(RateLimitPriority.EXECUTION, 20L, true));
    rules.put("eth_maxPriorityFeePerGas", new Rule(RateLimitPriority.EXECUTION, 10L, true));
    rules.put("eth_getTransactionByHash", new Rule(RateLimitPriority.EXECUTION, 20L, true));
    rules.put("eth_getTransactionReceipt", new Rule(RateLimitPriority.EXECUTION, 20L, true));
    rules.put("eth_sendRawTransaction", new Rule(RateLimitPriority.EXECUTION, 50L, false));
    return Map.copyOf(rules);
  }

  private static final class Rule {
    private final RateLimitPriority priority;
    private final long computeUnits;
    private final boolean replayable;

    private Rule(RateLimitPriority priority, long computeUnits, boolean replayable) {
      this.priority = priority;
      this.computeUnits = computeUnits;
      this.replayable = replayable;
    }
  }
}
