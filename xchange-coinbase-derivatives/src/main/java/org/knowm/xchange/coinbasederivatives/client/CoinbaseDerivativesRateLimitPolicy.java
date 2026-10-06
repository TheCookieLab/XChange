package org.knowm.xchange.coinbasederivatives.client;

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
 * Default rate-limit policy of the Coinbase Global Derivatives (Starbase/Deribit) JSON-RPC gateway.
 *
 * <p>Every request is a {@code POST} to one URL, so the policy classifies by JSON-RPC method name
 * (the operation key) and not by HTTP route. Methods are an explicit allow list: a method that is
 * not declared here is rejected before anything is sent, because its credit cost and priority are
 * not known.
 *
 * <p>Sources (retrieved 2026-10-05):
 *
 * <ul>
 *   <li>Coinbase Global Derivatives OpenAPI spec
 *       (https://docs.cdp.coinbase.com/api-reference/coinbase-deribit-app-api/adv-starbase-openapi.json):
 *       {@code public/get_instruments} "Sustained rate: 1 request/second"; method descriptions link
 *       to the Deribit rate-limit article below.
 *   <li>Deribit rate limits (https://docs.deribit.com/articles/rate-limits), linked from the
 *       Coinbase spec: credits are charged per sub-account; non-matching requests cost 500
 *       credits, the pool holds 50,000 credits and refills 10,000 credits/second; matching-engine
 *       requests have separate request-rate tiers (Tier 4: 5 requests/second, burst 20);
 *       {@code public/get_instruments} has burst 50 at 1 request/second; unauthenticated public
 *       requests are limited per IP; exhaustion is reported as {@code too_many_requests} (code
 *       10028). <b>[INFERENCE]</b> Coinbase does not state that its gateway applies these Deribit
 *       numbers; they are adopted as the documented conservative bound.
 *   <li>Coinbase App rate limiting (https://docs.cdp.coinbase.com/coinbase-app/api-architecture/rate-limiting):
 *       10,000 requests per hour per API key / OAuth user. Whether the gateway and the Advanced
 *       Trade API share that quota is not established upstream, so {@value #USER_HOURLY} is an
 *       explicit <em>client</em> policy budget, identical in both Coinbase modules, that makes both
 *       API families draw on one user allocation.
 * </ul>
 *
 * <p>Client-policy choices that are not upstream numbers: the per-IP credit pool of unauthenticated
 * public methods ({@value #PUBLIC_CREDITS}) reuses the documented per-sub-account credit shape, as
 * no public-IP allowance is published; {@code public/auth} is classified as an {@code EXECUTION}
 * operation because every private call, including cancellations, depends on it.
 *
 * <p>Replay: reads and idempotent cancellations may be replayed after a confirmed rate rejection;
 * placement-class methods never are (see {@link ReplaySafety}). Maximum wait, attempts and
 * fallback backoff (used when a rejection carries no reset, the norm for the JSON-RPC error {@code
 * 10028}) are the core defaults.
 *
 * @since 1.0.3
 */
public final class CoinbaseDerivativesRateLimitPolicy {

  /** Policy namespace and prefix of the gateway-specific budgets. */
  public static final String NAMESPACE = "coinbase.derivatives";

  /** Policy version, the retrieval date of its sources. */
  public static final String VERSION = "2026-10-05";

  /**
   * Aggregate user budget shared with the Coinbase Advanced Trade policy (client policy; cost 1 per
   * HTTP attempt).
   */
  public static final String USER_HOURLY = "coinbase.user.hourly";

  /** Per-sub-account credit pool of non-matching private requests. */
  public static final String CREDITS = "coinbase.derivatives.credits";

  /** Per-IP credit pool of unauthenticated public requests (client policy). */
  public static final String PUBLIC_CREDITS = "coinbase.derivatives.public.credits";

  /** Per-sub-account matching-engine request rate. */
  public static final String MATCHING = "coinbase.derivatives.matching";

  /** Method-specific limit of {@code public/get_instruments}. */
  public static final String INSTRUMENTS = "coinbase.derivatives.instruments";

  /** JSON-RPC error code {@code too_many_requests}. */
  public static final int TOO_MANY_REQUESTS_CODE = 10028;

  private static final long NON_MATCHING_COST = 500L;
  private static final Map<String, Rule> RULES = rules();

  private CoinbaseDerivativesRateLimitPolicy() {}

  /**
   * Creates the default policy.
   *
   * @return the immutable policy
   */
  public static RateLimitPolicy create() {
    return new RateLimitPolicy(
            NAMESPACE,
            VERSION,
            "Coinbase Global Derivatives OpenAPI (adv-starbase-openapi.json) and Deribit rate-limit"
                + " article https://docs.deribit.com/articles/rate-limits, retrieved 2026-10-05;"
                + " Coinbase App rate limiting"
                + " https://docs.cdp.coinbase.com/coinbase-app/api-architecture/rate-limiting,"
                + " retrieved 2026-10-05",
            budgets(),
            CoinbaseDerivativesRateLimitPolicy::classify,
            RateLimitFeedbackInterpreter.statuses(Set.of(429), Set.of()));
  }

  private static List<RateLimitBudget> budgets() {
    return List.of(
        RateLimitBudget.rollingWindow(USER_HOURLY, ScopeKind.USER, 10_000, Duration.ofHours(1)),
        RateLimitBudget.tokenBucket(CREDITS, ScopeKind.USER, 50_000, 10_000, Duration.ofSeconds(1)),
        RateLimitBudget.tokenBucket(
            PUBLIC_CREDITS, ScopeKind.EGRESS, 50_000, 10_000, Duration.ofSeconds(1)),
        RateLimitBudget.tokenBucket(MATCHING, ScopeKind.USER, 20, 5, Duration.ofSeconds(1)),
        RateLimitBudget.tokenBucket(INSTRUMENTS, ScopeKind.EGRESS, 50, 1, Duration.ofSeconds(1)));
  }

  /**
   * Classifies a JSON-RPC method.
   *
   * @param request the logical request; its operation key is the JSON-RPC method
   * @return the operation, or {@code null} for a method this policy does not know
   */
  private static RateLimitOperation classify(RateLimitRequest request) {
    Rule rule = RULES.get(request.getOperationKey());
    if (rule == null) {
      return null;
    }
    return new RateLimitOperation(
        request.getOperationKey(),
        rule.priority,
        rule.requirements,
        rule.replaySafety != ReplaySafety.PLACEMENT);
  }

  private static Map<String, Rule> rules() {
    Map<String, Rule> rules = new LinkedHashMap<>();
    // Unauthenticated public market data: per-IP credit pool, MARKET_DATA.
    for (String method :
        List.of(
            "public/ticker",
            "public/get_order_book",
            "public/get_last_trades_by_instrument",
            "public/get_tradingview_chart_data",
            "public/get_time")) {
      rules.put(
          method,
          new Rule(
              RateLimitPriority.MARKET_DATA,
              Map.of(PUBLIC_CREDITS, NON_MATCHING_COST),
              ReplaySafety.READ));
    }
    // Method-specific limit instead of the default credit cost (Deribit article, 2026-10-05).
    rules.put(
        "public/get_instruments",
        new Rule(RateLimitPriority.MARKET_DATA, Map.of(INSTRUMENTS, 1L), ReplaySafety.READ));
    // Token acquisition gates every private call, cancellations included.
    Map<String, Long> auth = new LinkedHashMap<>();
    auth.put(PUBLIC_CREDITS, NON_MATCHING_COST);
    auth.put(USER_HOURLY, 1L);
    rules.put("public/auth", new Rule(RateLimitPriority.EXECUTION, auth, ReplaySafety.READ));
    // Private reads required for execution safety: order, trade, position and account state.
    Map<String, Long> privateRead = new LinkedHashMap<>();
    privateRead.put(CREDITS, NON_MATCHING_COST);
    privateRead.put(USER_HOURLY, 1L);
    for (String method :
        List.of(
            "private/get_account_summary",
            "private/get_positions",
            "private/get_open_orders",
            "private/get_open_orders_by_instrument",
            "private/get_order_history_by_instrument",
            "private/get_order_state",
            "private/get_user_trades_by_instrument",
            "private/get_user_trades_by_currency")) {
      rules.put(method, new Rule(RateLimitPriority.EXECUTION, privateRead, ReplaySafety.READ));
    }
    // Matching-engine methods: separate request-rate bucket, no credits.
    Map<String, Long> matching = new LinkedHashMap<>();
    matching.put(MATCHING, 1L);
    matching.put(USER_HOURLY, 1L);
    for (String method :
        List.of(
            "private/cancel",
            "private/cancel_all",
            "private/cancel_all_by_currency",
            "private/cancel_all_by_instrument",
            "private/cancel_by_label")) {
      rules.put(
          method,
          new Rule(RateLimitPriority.EXECUTION, matching, ReplaySafety.IDEMPOTENT_CANCELLATION));
    }
    for (String method :
        List.of(
            "private/buy",
            "private/sell",
            "private/edit",
            "private/edit_by_label",
            "private/close_position")) {
      rules.put(method, new Rule(RateLimitPriority.EXECUTION, matching, ReplaySafety.PLACEMENT));
    }
    return Map.copyOf(rules);
  }

  /**
   * Returns the replay safety this policy attributes to a method.
   *
   * @param method the JSON-RPC method
   * @return its replay safety, or {@code null} for an unknown method
   */
  static ReplaySafety replaySafety(String method) {
    Rule rule = RULES.get(method);
    return rule == null ? null : rule.replaySafety;
  }

  private static final class Rule {
    private final RateLimitPriority priority;
    private final Map<String, Long> requirements;
    private final ReplaySafety replaySafety;

    private Rule(
        RateLimitPriority priority, Map<String, Long> requirements, ReplaySafety replaySafety) {
      this.priority = priority;
      this.requirements = Map.copyOf(requirements);
      this.replaySafety = replaySafety;
    }
  }
}
