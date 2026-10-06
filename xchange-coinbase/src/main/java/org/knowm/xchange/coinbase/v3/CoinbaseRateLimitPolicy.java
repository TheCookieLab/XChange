package org.knowm.xchange.coinbase.v3;

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
 * Default rate-limit policy of the Coinbase Advanced Trade (v3 brokerage) REST API, enabled by
 * {@link CoinbaseExchange#getDefaultExchangeSpecification()}.
 *
 * <p><b>Sources (retrieved 2026-10-05).</b>
 *
 * <ul>
 *   <li>Coinbase App rate limiting, https://docs.cdp.coinbase.com/coinbase-app/api-architecture/rate-limiting:
 *       "each API key or OAuth-authenticated Coinbase user is rate limited to 10,000 requests per
 *       hour"; a breach answers HTTP 429 with error id {@code rate_limit_exceeded}. The error
 *       catalog (https://docs.cdp.coinbase.com/coinbase-app/api-architecture/error-messages) also
 *       lists {@code resource_exhausted} as a 429 id. Neither page documents {@code Retry-After}
 *       or {@code x-ratelimit-*} headers, so a {@code Retry-After} is honoured when present and a
 *       finite capped fallback backoff applies otherwise.
 *   <li>Advanced Trade REST reference, https://docs.cdp.coinbase.com/coinbase-app/advanced-trade-apis/rest-api:
 *       public and private methods share one host; public responses are cached for one second. No
 *       public per-IP or per-endpoint requests-per-second quota is published for the brokerage API
 *       (the separate Coinbase Exchange limits of 10 requests/s public and 15 requests/s private
 *       are not Advanced Trade limits and are not used here).
 * </ul>
 *
 * <p><b>Budgets.</b> {@value #USER_HOURLY} mirrors the documented hourly allocation and is the
 * explicit aggregate client policy shared with every other Coinbase API family of the same user
 * (identical definition in each family's policy); upstream sharing across families is not
 * established by Coinbase, so the sharing is a conservative client choice. {@value
 * #BROKERAGE_CLIENT} and {@value #BROKERAGE_PUBLIC} are <em>client pacing policies</em>, not
 * published Coinbase quotas: a recorded production incident (an 82-request startup burst answered
 * with 429) proves an undisclosed short-window limit exists, so requests are paced at 10 per
 * second with a burst of 10 per user (authenticated) respectively per process egress (public).
 *
 * <p><b>Operation classes.</b> {@link RateLimitPriority#EXECUTION} covers order placement, edit,
 * cancellation, position close, convert, preview and every order, fill, position, account, portfolio
 * and balance operation; {@link RateLimitPriority#MARKET_DATA} covers products, candles, tickers,
 * books, best bid/ask and server time. Classification is by path family, so a method added later
 * under a known family is limited without code change; an unknown family or a mutation under a
 * market-data family is unclassified and rejected before anything is sent. Only {@code GET}
 * operations are replayed after a confirmed rate rejection: every mutation or preview/quote POST
 * may have been applied or consumed provider state and is surfaced instead.
 *
 * <p><b>Bounds.</b> At most 512 pending market-data operations (the recorded 82-request startup
 * burst fits with headroom; CF requires at least 256) and, independently, 64 pending execution
 * operations that market data can never consume; maximum total wait 5 s for execution and 60 s for
 * market data including replays; 3 attempts; fallback backoff 1 s base, 30 s cap, jitter 0.25
 * (library defaults, which suit Coinbase's per-second pacing and hourly allocation).
 *
 * @since 1.0.3
 */
public final class CoinbaseRateLimitPolicy {

  /** Policy namespace of the Advanced Trade brokerage API. */
  public static final String NAMESPACE = "coinbase.brokerage";

  /** Policy version, recorded in context diagnostics. */
  public static final String VERSION = "2026-10-05.1";

  /**
   * Aggregate documented hourly allocation per API key or Coinbase user. Every Coinbase policy
   * declares exactly this definition so a shared context holds one budget for the user.
   */
  public static final String USER_HOURLY = "coinbase.user.hourly";

  /** Client pacing of authenticated brokerage requests, per user scope. */
  public static final String BROKERAGE_CLIENT = "coinbase.brokerage.client";

  /** Client pacing of public brokerage requests, per process egress. */
  public static final String BROKERAGE_PUBLIC = "coinbase.brokerage.public";

  /** Pending market-data operations; at least the 256 required of the CF Coinbase profile. */
  public static final int MARKET_DATA_PENDING_LIMIT = 512;

  /** Pending execution operations, a bound market data cannot consume. */
  public static final int EXECUTION_PENDING_LIMIT = 64;

  private static final String SOURCE =
      "Coinbase App rate limiting (10,000 requests/hour per API key or user; 429 rate_limit_exceeded)"
          + " https://docs.cdp.coinbase.com/coinbase-app/api-architecture/rate-limiting retrieved"
          + " 2026-10-05; Coinbase error catalog (429 rate_limit_exceeded, resource_exhausted)"
          + " https://docs.cdp.coinbase.com/coinbase-app/api-architecture/error-messages retrieved"
          + " 2026-10-05; Advanced Trade REST reference (no published brokerage RPS)"
          + " https://docs.cdp.coinbase.com/coinbase-app/advanced-trade-apis/rest-api retrieved"
          + " 2026-10-05; client pacing 10/s burst 10 is a CF client policy, not a Coinbase quota";

  private static final String API_PREFIX = "api/v3/brokerage/";
  private static final String V2_PREFIX = "v2/";

  /** Path families classified {@link RateLimitPriority#EXECUTION}. */
  private static final Set<String> EXECUTION_FAMILIES =
      Set.of(
          "orders",
          "accounts",
          "portfolios",
          "convert",
          "cfm",
          "intx",
          "payment_methods",
          "transaction_summary",
          "key_permissions");

  /** Path families classified {@link RateLimitPriority#MARKET_DATA}; read-only. */
  private static final Set<String> MARKET_DATA_FAMILIES =
      Set.of("market", "products", "product_book", "best_bid_ask", "time");

  private static final RateLimitPolicy DEFAULT = create();

  private CoinbaseRateLimitPolicy() {}

  /**
   * @return the shared immutable default policy; derive overrides with its {@code with*} copy
   *     methods
   */
  public static RateLimitPolicy defaultPolicy() {
    return DEFAULT;
  }

  /**
   * Creates a new instance of the default policy.
   *
   * @return a policy equal in definition to {@link #defaultPolicy()}
   */
  public static RateLimitPolicy create() {
    return new RateLimitPolicy(
            NAMESPACE,
            VERSION,
            SOURCE,
            List.of(
                RateLimitBudget.rollingWindow(USER_HOURLY, ScopeKind.USER, 10_000, Duration.ofHours(1)),
                RateLimitBudget.tokenBucket(
                    BROKERAGE_CLIENT, ScopeKind.USER, 10, 10, Duration.ofSeconds(1)),
                RateLimitBudget.tokenBucket(
                    BROKERAGE_PUBLIC, ScopeKind.EGRESS, 10, 10, Duration.ofSeconds(1))),
            CoinbaseRateLimitPolicy::classify,
            RateLimitFeedbackInterpreter.statuses(Set.of(429), Set.of()))
        .withPendingLimit(RateLimitPriority.MARKET_DATA, MARKET_DATA_PENDING_LIMIT)
        .withPendingLimit(RateLimitPriority.EXECUTION, EXECUTION_PENDING_LIMIT)
        .withMaxWait(RateLimitPriority.EXECUTION, Duration.ofSeconds(5))
        .withMaxWait(RateLimitPriority.MARKET_DATA, Duration.ofSeconds(60))
        .withMaxAttempts(3)
        .withFallbackBackoff(Duration.ofSeconds(1), Duration.ofSeconds(30), 0.25);
  }

  /**
   * Classifies one logical brokerage request. The operation key is {@code "<METHOD> <path>"} where
   * the path is the published template or the concrete path, with or without the {@code
   * /api/v3/brokerage} (or deprecated {@code /v2}) base path.
   *
   * @param request the logical request
   * @return the operation, or {@code null} for an unknown family, method or a mutation of a
   *     market-data family
   */
  static RateLimitOperation classify(RateLimitRequest request) {
    String key = request.getOperationKey();
    int space = key.indexOf(' ');
    if (space <= 0) {
      return null;
    }
    String method = key.substring(0, space);
    String path = normalizePath(key.substring(space + 1));
    if (path.isEmpty()) {
      return null;
    }
    boolean read;
    switch (method) {
      case "GET":
        read = true;
        break;
      case "POST":
      case "PUT":
      case "PATCH":
      case "DELETE":
        read = false;
        break;
      default:
        return null;
    }
    int slash = path.indexOf('/');
    String family = slash < 0 ? path : path.substring(0, slash);
    RateLimitPriority priority;
    if (EXECUTION_FAMILIES.contains(family)) {
      priority = RateLimitPriority.EXECUTION;
    } else if (read && MARKET_DATA_FAMILIES.contains(family)) {
      priority = RateLimitPriority.MARKET_DATA;
    } else {
      return null;
    }
    return new RateLimitOperation(
        method + " " + path, priority, requirements(request.isAuthenticated()), read);
  }

  private static Map<String, Long> requirements(boolean authenticated) {
    Map<String, Long> costs = new LinkedHashMap<>();
    if (authenticated) {
      costs.put(USER_HOURLY, 1L);
      costs.put(BROKERAGE_CLIENT, 1L);
    } else {
      costs.put(BROKERAGE_PUBLIC, 1L);
    }
    return costs;
  }

  private static String normalizePath(String rawPath) {
    String path = rawPath.trim();
    int query = path.indexOf('?');
    if (query >= 0) {
      path = path.substring(0, query);
    }
    int start = 0;
    while (start < path.length() && path.charAt(start) == '/') {
      start++;
    }
    path = path.substring(start);
    if (path.startsWith(API_PREFIX)) {
      return path.substring(API_PREFIX.length());
    }
    if (path.startsWith(V2_PREFIX)) {
      return path.substring(V2_PREFIX.length());
    }
    return path;
  }
}
