package org.knowm.xchange.kucoin;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.knowm.xchange.client.ratelimit.RateLimitBudget;
import org.knowm.xchange.client.ratelimit.RateLimitBudget.ScopeKind;
import org.knowm.xchange.client.ratelimit.RateLimitFeedback;
import org.knowm.xchange.client.ratelimit.RateLimitFeedbackInterpreter;
import org.knowm.xchange.client.ratelimit.RateLimitOperation;
import org.knowm.xchange.client.ratelimit.RateLimitPolicy;
import org.knowm.xchange.client.ratelimit.RateLimitPriority;
import org.knowm.xchange.client.ratelimit.RateLimitRequest;

/**
 * Default rate-limit policy of the KuCoin REST API (Classic and UTA generations), enabled by {@link
 * KucoinExchange#getDefaultExchangeSpecification()}.
 *
 * <p><b>Sources (retrieved 2026-10-06).</b>
 *
 * <ul>
 *   <li>Classic rate limit rules, https://www.kucoin.com/docs-new/rate-limit-rule-classic: every
 *       endpoint belongs to one resource pool with a per-endpoint weight; private pools are keyed
 *       by UID, the public pool by IP; the VIP0 quotas are Spot 4000, Futures/Management/Earn/
 *       Public 2000 per 30 s; an exhausted pool answers HTTP 429 with code {@code 429000} and the
 *       headers {@code gw-ratelimit-limit}, {@code gw-ratelimit-remaining} and {@code
 *       gw-ratelimit-reset} (milliseconds until reset); server-overload 429 responses carry no
 *       such headers and do not count against the quota. The page text states a 1 s quota window
 *       while its VIP table states 30 s; the stricter 30 s table value is used here.
 *   <li>UTA rate limit rules, https://www.kucoin.com/docs-new/rate-limit-rule-uta: UTA Trading and
 *       Management pools are 300 per second at VIP0, the Public pool 2000 per 30 s; same weights,
 *       429000 code and headers; V1 and V2 endpoints consume the same pool.
 *   <li>Per-endpoint pages under https://www.kucoin.com/docs-new/rest/ (for example
 *       rest/account-info/account-funding/get-account-list-spot, rest/spot-trading/orders/*,
 *       rest/spot-trading/market-data/*, rest/ua/*) and
 *       https://www.kucoin.com/docs-new/websocket-api/base-info/get-private-token-spot-margin,
 *       get-public-token-spot-margin and get-private-token-uta, each declaring {@code
 *       x-api-rate-limit-pool} and {@code x-api-rate-limit-weight}.
 * </ul>
 *
 * <p><b>Provenance gaps.</b> Provider pages could not be found for endpoints that are only
 * published as abandoned or not at all: {@code POST v1/accounts}, {@code
 * GET v1/accounts/{accountId}/ledgers}, {@code GET v1/hist-orders}, {@code GET v1/symbols},
 * {@code GET v1/currencies}, {@code GET v2/currencies/{currency}} and {@code GET
 * v2/market/orderbook/level3}. They are priced like their documented current equivalent (same
 * path or same resource, documented weights of {@code POST/GET v1/accounts}, {@code
 * GET v1/accounts/ledgers}, {@code GET v1/orders}, {@code GET v2/symbols}, {@code GET
 * v3/currencies}, {@code GET v3/currencies/{currency}} and the part order book); this is a client
 * assumption, not a published weight.
 *
 * <p><b>Budgets.</b> One budget per documented pool, rolling windows at the VIP0 quota. The Classic
 * and UTA public pools are documented on separate pages and kept as separate budgets. The Futures
 * pool is not reachable from this module and is therefore not declared. Quotas are VIP0 defaults;
 * accounts with a higher VIP level are only paced more conservatively than necessary.
 *
 * <p><b>Operation classes.</b> {@link RateLimitPriority#MARKET_DATA} covers the public market data
 * endpoints and the public WebSocket token; every private endpoint (orders, fills, accounts,
 * funding, fees, positions, private WebSocket tokens) is {@link RateLimitPriority#EXECUTION}. Only {@code GET} operations and the
 * WebSocket-token {@code POST}s (they create a connection token and no economic state) are
 * replayed after a confirmed rate rejection; every other mutation (order placement, cancellation,
 * amendment, transfers, withdrawals, leverage, deposit-address creation) is surfaced instead of
 * blindly replayed. Any operation missing from the table is unclassified and rejected before
 * anything is sent.
 *
 * <p><b>Bounds.</b> At most 512 pending market-data and 64 pending execution operations; maximum
 * total wait 5 s for execution and 60 s for market data including replays; 3 attempts; fallback
 * backoff 1 s base, 30 s cap, jitter 0.25 when a rejection carries no reset header.
 *
 * @since 1.0.3
 */
public final class KucoinRateLimitPolicy {

  /** Policy namespace of the KuCoin REST API. */
  public static final String NAMESPACE = "kucoin.rest";

  /** Policy version, recorded in context diagnostics. */
  public static final String VERSION = "2026-10-06.1";

  /** Classic Spot (including Margin) resource pool, per user. */
  public static final String CLASSIC_SPOT = "kucoin.classic.spot";

  /** Classic Management resource pool, per user. */
  public static final String CLASSIC_MANAGEMENT = "kucoin.classic.management";

  /** Classic Earn resource pool, per user. */
  public static final String CLASSIC_EARN = "kucoin.classic.earn";

  /** Classic Public resource pool, per process egress (documented as per IP). */
  public static final String CLASSIC_PUBLIC = "kucoin.classic.public";

  /** UTA Trading resource pool, per user. */
  public static final String UTA_TRADING = "kucoin.uta.trading";

  /** UTA Management resource pool, per user. */
  public static final String UTA_MANAGEMENT = "kucoin.uta.management";

  /** UTA Public resource pool, per process egress (documented as per IP). */
  public static final String UTA_PUBLIC = "kucoin.uta.public";

  /** Pending market-data operations. */
  public static final int MARKET_DATA_PENDING_LIMIT = 512;

  /** Pending execution operations, a bound market data cannot consume. */
  public static final int EXECUTION_PENDING_LIMIT = 64;

  private static final Duration CLASSIC_WINDOW = Duration.ofSeconds(30);
  private static final Duration UTA_PRIVATE_WINDOW = Duration.ofSeconds(1);

  private static final String SOURCE =
      "KuCoin Classic rate limit rules (resource pools with weights; VIP0 Spot 4000, Futures/Management/Earn/Public"
          + " 2000 per 30 s; 429 code 429000, gw-ratelimit-* headers)"
          + " https://www.kucoin.com/docs-new/rate-limit-rule-classic retrieved 2026-10-06; KuCoin UTA rate limit"
          + " rules (VIP0 Trading 300/s, Management 300/s, Public 2000 per 30 s)"
          + " https://www.kucoin.com/docs-new/rate-limit-rule-uta retrieved 2026-10-06; per-endpoint pool and weight"
          + " from the x-api-rate-limit-pool/-weight fields of https://www.kucoin.com/docs-new/rest/** and"
          + " https://www.kucoin.com/docs-new/websocket-api/base-info/get-*-token-* retrieved 2026-10-06;"
          + " endpoints without a current provider page (POST v1/accounts, v1/accounts/{accountId}/ledgers,"
          + " v1/hist-orders, v1/symbols, v1/currencies, v2/currencies/{currency}, v2/market/orderbook/level3)"
          + " are priced like their documented equivalent, a client assumption and not a published weight;"
          + " the classic 30 s window follows the VIP table rather than the 1 s wording of the same page";

  private static final Map<String, Cost> COSTS = costs();

  private static final RateLimitPolicy DEFAULT = create();

  private KucoinRateLimitPolicy() {}

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
                RateLimitBudget.rollingWindow(CLASSIC_SPOT, ScopeKind.USER, 4000, CLASSIC_WINDOW),
                RateLimitBudget.rollingWindow(
                    CLASSIC_MANAGEMENT, ScopeKind.USER, 2000, CLASSIC_WINDOW),
                RateLimitBudget.rollingWindow(CLASSIC_EARN, ScopeKind.USER, 2000, CLASSIC_WINDOW),
                RateLimitBudget.rollingWindow(
                    CLASSIC_PUBLIC, ScopeKind.EGRESS, 2000, CLASSIC_WINDOW),
                RateLimitBudget.rollingWindow(UTA_TRADING, ScopeKind.USER, 300, UTA_PRIVATE_WINDOW),
                RateLimitBudget.rollingWindow(
                    UTA_MANAGEMENT, ScopeKind.USER, 300, UTA_PRIVATE_WINDOW),
                RateLimitBudget.rollingWindow(UTA_PUBLIC, ScopeKind.EGRESS, 2000, CLASSIC_WINDOW)),
            KucoinRateLimitPolicy::classify,
            interpreter())
        .withPendingLimit(RateLimitPriority.MARKET_DATA, MARKET_DATA_PENDING_LIMIT)
        .withPendingLimit(RateLimitPriority.EXECUTION, EXECUTION_PENDING_LIMIT)
        .withMaxWait(RateLimitPriority.EXECUTION, Duration.ofSeconds(5))
        .withMaxWait(RateLimitPriority.MARKET_DATA, Duration.ofSeconds(60))
        .withMaxAttempts(3)
        .withFallbackBackoff(Duration.ofSeconds(1), Duration.ofSeconds(30), 0.25);
  }

  /**
   * Classifies one logical request by its operation key, {@code "<HTTP METHOD> <path>"}.
   *
   * @param request the logical request
   * @return the operation, or {@code null} for an operation the module does not declare
   */
  static RateLimitOperation classify(RateLimitRequest request) {
    String key = request.getOperationKey();
    Cost cost = COSTS.get(key);
    if (cost == null) {
      return null;
    }
    return new RateLimitOperation(
        key,
        cost.priority,
        Map.of(cost.budget, cost.weight),
        cost.replay);
  }

  /**
   * KuCoin answers an exhausted pool with HTTP 429; {@code gw-ratelimit-reset} carries the
   * milliseconds until the quota resets and {@code Retry-After} is honoured when it is the only
   * hint.
   */
  private static RateLimitFeedbackInterpreter interpreter() {
    RateLimitFeedbackInterpreter statuses =
        RateLimitFeedbackInterpreter.statuses(Set.of(429), Set.of());
    return (int status, Function<String, String> headers, Instant wallNow) -> {
      RateLimitFeedback feedback = statuses.interpret(status, headers, wallNow);
      if (feedback.getKind() != RateLimitFeedback.Kind.RATE_REJECTED
          || feedback.getRetryAfter() != null) {
        return feedback;
      }
      Duration reset = parseResetMillis(headers.apply("gw-ratelimit-reset"));
      return reset == null ? feedback : RateLimitFeedback.rejected(reset);
    };
  }

  private static Duration parseResetMillis(String raw) {
    if (raw == null) {
      return null;
    }
    String value = raw.trim();
    if (value.isEmpty() || value.length() > 15) {
      return null;
    }
    for (int i = 0; i < value.length(); i++) {
      if (value.charAt(i) < '0' || value.charAt(i) > '9') {
        return null;
      }
    }
    long millis = Long.parseLong(value);
    return millis == 0 ? null : Duration.ofMillis(millis);
  }

  private static Map<String, Cost> costs() {
    Map<String, Cost> table = new LinkedHashMap<>();

    // Classic, Management pool (private).
    mgmt(table, "GET api/v1/accounts", 5);
    mgmt(table, "POST api/v1/accounts", 5);
    mgmt(table, "GET api/v1/accounts/{accountId}", 5);
    mgmt(table, "GET api/v1/accounts/{accountId}/ledgers", 2);
    mgmt(table, "GET api/v1/accounts/ledgers", 2);
    mgmt(table, "POST api/v2/accounts/inner-transfer", 10);
    mgmt(table, "GET api/v1/deposits", 5);
    mgmt(table, "POST api/v1/deposit-addresses", 20);
    mgmt(table, "GET api/v1/deposit-addresses", 5);
    mgmt(table, "GET api/v2/deposit-addresses", 5);
    mgmt(table, "POST api/v1/withdrawals", 5);
    mgmt(table, "GET api/v1/withdrawals", 20);

    // Classic, Earn pool (private).
    table.put(
        "GET api/v1/earn/hold-assets",
        new Cost(RateLimitPriority.EXECUTION, CLASSIC_EARN, 5, true));

    // Classic, Spot pool (private).
    spot(table, "GET api/v1/fills", 10, true);
    spot(table, "GET api/v1/hist-orders", 2, true);
    spot(table, "GET api/v1/limit/orders", 3, true);
    spot(table, "POST api/v1/hf/orders", 1, false);
    spot(table, "POST api/v1/stop-order", 1, false);
    spot(table, "DELETE api/v1/orders/{orderId}", 3, false);
    spot(table, "DELETE api/v1/orders", 20, false);
    spot(table, "GET api/v1/orders/{orderId}", 2, true);
    spot(table, "GET api/v1/orders", 2, true);
    spot(table, "GET api/v1/base-fee", 3, true);
    spot(table, "GET api/v1/trade-fees", 3, true);
    spot(table, "GET api/v3/market/orderbook/level2", 3, true);
    table.put(
        "POST api/v1/bullet-private",
        new Cost(RateLimitPriority.EXECUTION, CLASSIC_SPOT, 10, true));

    // Classic, Public pool.
    classicPublic(table, "GET api/v1/market/histories", 3);
    classicPublic(table, "GET api/v1/market/candles", 3);
    classicPublic(table, "GET api/v1/market/orderbook/level2_20", 2);
    classicPublic(table, "GET api/v1/market/orderbook/level2_100", 2);
    classicPublic(table, "GET api/v2/market/orderbook/level3", 3);
    classicPublic(table, "GET api/v1/market/orderbook/level1", 2);
    classicPublic(table, "GET api/v1/market/allTickers", 15);
    classicPublic(table, "GET api/v1/market/stats", 15);
    classicPublic(table, "GET api/v1/symbols", 4);
    classicPublic(table, "GET api/v2/symbols", 4);
    classicPublic(table, "GET api/v1/currencies", 3);
    classicPublic(table, "GET api/v2/currencies/{currency}", 3);
    classicPublic(table, "GET api/v3/currencies", 3);
    classicPublic(table, "GET api/v1/prices", 3);
    classicPublic(table, "GET api/v1/timestamp", 3);
    table.put(
        "POST api/v1/bullet-public",
        new Cost(RateLimitPriority.MARKET_DATA, CLASSIC_PUBLIC, 10, true));

    // UTA, Trading pool.
    utaTrading(table, "POST api/ua/v1/unified/order/place", 1);
    utaTrading(table, "POST api/ua/v1/unified/order/cancel", 1);
    utaTrading(table, "POST api/ua/v1/unified/order/amend", 2);
    utaTrading(table, "POST api/ua/v1/unified/order/cancel-batch", 4);

    // UTA, Management pool (private).
    utaMgmt(table, "GET api/ua/v1/unified/order/detail", 4, true);
    utaMgmt(table, "GET api/ua/v1/unified/order/history", 4, true);
    utaMgmt(table, "GET api/ua/v1/unified/order/execution", 4, true);
    utaMgmt(table, "GET api/ua/v1/account/mode", 30, true);
    utaMgmt(table, "GET api/ua/v1/unified/account/overview", 5, true);
    utaMgmt(table, "GET api/ua/v1/unified/account/balance", 5, true);
    utaMgmt(table, "GET api/ua/v1/account/transfer-quota", 20, true);
    utaMgmt(table, "POST api/ua/v1/account/transfer", 4, false);
    utaMgmt(table, "GET api/ua/v1/user/fee-rate", 3, true);
    utaMgmt(table, "POST api/ua/v1/unified/account/modify-leverage", 20, false);
    utaMgmt(table, "GET api/ua/v1/account/ledger", 2, true);
    utaMgmt(table, "GET api/ua/v1/unified/position/open-list", 3, true);
    utaMgmt(table, "GET api/ua/v1/unified/position/margin-mode", 10, true);
    utaMgmt(table, "GET api/ua/v1/position/history", 2, true);
    table.put(
        "POST api/v2/bullet-private",
        new Cost(RateLimitPriority.EXECUTION, UTA_MANAGEMENT, 10, true));
    // Documented in the Management pool although it is a public order book.
    table.put(
        "GET api/ua/v1/market/orderbook",
        new Cost(RateLimitPriority.MARKET_DATA, UTA_MANAGEMENT, 3, true));

    // UTA, Public pool.
    utaPublic(table, "GET api/ua/v1/market/instrument", 4);
    utaPublic(table, "GET api/ua/v1/market/ticker", 15);
    utaPublic(table, "GET api/ua/v1/market/kline", 3);
    utaPublic(table, "GET api/ua/v1/market/trade", 3);

    return Map.copyOf(table);
  }

  private static void mgmt(Map<String, Cost> table, String key, long weight) {
    table.put(
        key,
        new Cost(
            RateLimitPriority.EXECUTION, CLASSIC_MANAGEMENT, weight, key.startsWith("GET ")));
  }

  private static void spot(Map<String, Cost> table, String key, long weight, boolean replay) {
    table.put(key, new Cost(RateLimitPriority.EXECUTION, CLASSIC_SPOT, weight, replay));
  }

  private static void classicPublic(Map<String, Cost> table, String key, long weight) {
    table.put(key, new Cost(RateLimitPriority.MARKET_DATA, CLASSIC_PUBLIC, weight, true));
  }

  private static void utaTrading(Map<String, Cost> table, String key, long weight) {
    table.put(key, new Cost(RateLimitPriority.EXECUTION, UTA_TRADING, weight, false));
  }

  private static void utaMgmt(Map<String, Cost> table, String key, long weight, boolean replay) {
    table.put(key, new Cost(RateLimitPriority.EXECUTION, UTA_MANAGEMENT, weight, replay));
  }

  private static void utaPublic(Map<String, Cost> table, String key, long weight) {
    table.put(key, new Cost(RateLimitPriority.MARKET_DATA, UTA_PUBLIC, weight, true));
  }

  /** Pool, weight and replay safety of one operation. */
  private static final class Cost {
    private final RateLimitPriority priority;
    private final String budget;
    private final long weight;
    private final boolean replay;

    private Cost(RateLimitPriority priority, String budget, long weight, boolean replay) {
      this.priority = priority;
      this.budget = budget;
      this.weight = weight;
      this.replay = replay;
    }
  }
}
