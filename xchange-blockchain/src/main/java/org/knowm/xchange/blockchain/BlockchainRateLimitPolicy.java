package org.knowm.xchange.blockchain;

import java.time.Duration;
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
 * Default rate-limit policy of the Blockchain.com Exchange REST API (v3), enabled by {@link
 * BlockchainExchange#getDefaultExchangeSpecification()}.
 *
 * <p><b>Sources (retrieved 2026-10-06).</b>
 *
 * <ul>
 *   <li>Blockchain.com Exchange REST reference, https://api.blockchain.com/v3/: documents base URL,
 *       {@code X-API-Token} authentication and every endpoint, but publishes <em>no</em> REST
 *       request quota, weight table or {@code Retry-After} contract.
 *   <li>Blockchain.com Exchange API introduction, https://exchange.blockchain.com/api/ ("Fair usage
 *       and rate limits"): the only published limit is 1200 messages per minute on the
 *       <em>WebSocket</em> API. It is not a REST limit and is not applied here.
 *   <li>Pre-CF-683 module value: the module paced every REST call (public and authenticated) through
 *       one shared resilience4j limiter of 10 permits per 1 s ({@code limitForPeriod(10)}, {@code
 *       limitRefreshPeriod(1s)}). That value is preserved unchanged as {@value #REST_PACING}; it is
 *       a <em>client pacing policy</em> carried over from the module, not a published Blockchain.com
 *       quota.
 * </ul>
 *
 * <p><b>Budget.</b> A single {@link ScopeKind#USER} token bucket (capacity 10, refill 10 per second)
 * is shared by all fifteen rescu methods, which were one bucket before the migration. Every call
 * costs one token because the provider documents no per-endpoint weights. The exchange sends the
 * {@code X-API-Token} as a default header parameter rather than through a {@code ParamsDigest}, so
 * {@link RateLimitRequest#isAuthenticated()} is {@code false} for every call and cannot select a
 * scope; the user scope ({@code ExchangeSpecification.ResilienceSpecification#getRateLimitUserScope()}) binds the
 * allocation instead.
 *
 * <p><b>Operation classes.</b> {@link RateLimitPriority#MARKET_DATA} covers the public symbol
 * catalog and the L3 order book; {@link RateLimitPriority#EXECUTION} covers account, fee, deposit,
 * withdrawal, order and trade operations. Classification is an exact match of the operation key
 * {@code "<METHOD> v3/exchange/<path>"}: a method added to the REST interfaces without a deliberate
 * entry here is unclassified and rejected before anything is sent. Reads are replayed after a
 * confirmed HTTP 429 rejection with a fresh admission. Order cancellations are replayed too: the
 * module already retries them on transport failure with its default retry configuration, which
 * proves them safe to repeat. Order placement, withdrawal and deposit-address creation are never
 * replayed; the rejection is surfaced.
 *
 * <p><b>Bounds.</b> Library defaults: 64 pending execution and 256 pending market-data operations,
 * maximum total wait 5 s for execution and 60 s for market data including replays, 3 attempts,
 * fallback backoff 1 s base, 30 s cap, jitter 0.25. HTTP 429 is the only rate-rejection status; no
 * ban status is documented.
 *
 * @since 1.0.3
 */
final class BlockchainRateLimitPolicy {

  /** Policy namespace of the Blockchain.com Exchange REST API. */
  static final String NAMESPACE = "blockchain.exchange";

  /** Policy version, recorded in context diagnostics. */
  static final String VERSION = "2026-10-06.1";

  /** Client pacing shared by every REST call of the user, preserved from the pre-CF-683 module. */
  static final String REST_PACING = "blockchain.exchange.rest";

  private static final String SOURCE =
      "Blockchain.com Exchange REST reference (no published REST quota, weights or Retry-After)"
          + " https://api.blockchain.com/v3/ retrieved 2026-10-06; Exchange API fair usage (1200"
          + " messages/minute applies to the WebSocket API only, not applied)"
          + " https://exchange.blockchain.com/api/ retrieved 2026-10-06; 10 requests/second burst 10"
          + " is the pre-CF-683 module value (shared resilience4j endpointLimit), a client pacing"
          + " policy and not a Blockchain.com quota";

  private static final String BASE = "v3/exchange/";

  private static final Map<String, RateLimitPriority> PRIORITIES =
      Map.ofEntries(
          Map.entry("GET " + BASE + "symbols", RateLimitPriority.MARKET_DATA),
          Map.entry("GET " + BASE + "l3/{symbol}", RateLimitPriority.MARKET_DATA),
          Map.entry("GET " + BASE + "accounts", RateLimitPriority.EXECUTION),
          Map.entry("GET " + BASE + "fees", RateLimitPriority.EXECUTION),
          Map.entry("GET " + BASE + "deposits", RateLimitPriority.EXECUTION),
          Map.entry("POST " + BASE + "deposits/{symbol}", RateLimitPriority.EXECUTION),
          Map.entry("GET " + BASE + "withdrawals", RateLimitPriority.EXECUTION),
          Map.entry("POST " + BASE + "withdrawals", RateLimitPriority.EXECUTION),
          Map.entry("GET " + BASE + "orders", RateLimitPriority.EXECUTION),
          Map.entry("GET " + BASE + "orders/{orderId}", RateLimitPriority.EXECUTION),
          Map.entry("POST " + BASE + "orders", RateLimitPriority.EXECUTION),
          Map.entry("DELETE " + BASE + "orders", RateLimitPriority.EXECUTION),
          Map.entry("DELETE " + BASE + "orders/{orderId}", RateLimitPriority.EXECUTION),
          Map.entry("GET " + BASE + "trades", RateLimitPriority.EXECUTION));

  /** Operations safe to repeat after a confirmed rate rejection. */
  private static final Set<String> REPLAY_SAFE_METHODS = Set.of("GET", "DELETE");

  private static final RateLimitPolicy DEFAULT = create();

  private BlockchainRateLimitPolicy() {}

  /**
   * @return the shared immutable default policy; derive overrides with its {@code with*} copy
   *     methods
   */
  static RateLimitPolicy defaultPolicy() {
    return DEFAULT;
  }

  /**
   * Creates a new instance of the default policy.
   *
   * @return a policy equal in definition to {@link #defaultPolicy()}
   */
  static RateLimitPolicy create() {
    return new RateLimitPolicy(
        NAMESPACE,
        VERSION,
        SOURCE,
        List.of(
            RateLimitBudget.tokenBucket(
                REST_PACING, ScopeKind.USER, 10, 10, Duration.ofSeconds(1))),
        BlockchainRateLimitPolicy::classify,
        RateLimitFeedbackInterpreter.statuses(Set.of(429), Set.of()));
  }

  /**
   * Classifies one logical REST request.
   *
   * @param request the logical request
   * @return the operation, or {@code null} for an operation that is not part of the module's REST
   *     interfaces
   */
  static RateLimitOperation classify(RateLimitRequest request) {
    String key = request.getOperationKey();
    RateLimitPriority priority = PRIORITIES.get(key);
    if (priority == null) {
      return null;
    }
    String method = key.substring(0, key.indexOf(' '));
    return new RateLimitOperation(
        key, priority, Map.of(REST_PACING, 1L), REPLAY_SAFE_METHODS.contains(method));
  }
}
