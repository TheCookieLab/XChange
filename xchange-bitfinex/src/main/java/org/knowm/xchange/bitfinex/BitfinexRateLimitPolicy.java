package org.knowm.xchange.bitfinex;

import java.time.Duration;
import java.util.ArrayList;
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
 * Default rate-limit policy of the Bitfinex REST API (v1 and v2), enabled by {@link
 * BitfinexExchange#getDefaultExchangeSpecification()}.
 *
 * <p><b>Sources (retrieved 2026-10-06).</b>
 *
 * <ul>
 *   <li>Requirements and limitations, https://docs.bitfinex.com/docs/requirements-and-limitations:
 *       the REST limit "ranges from 10 to 90 requests per minute depending on the endpoint"; an IP
 *       address that exceeds it is blocked for 60 seconds and answered with {@code
 *       {"error":"ERR_RATE_LIMIT"}}. The limits are therefore keyed by the caller's IP address, not
 *       by API key, which is why every budget is {@link ScopeKind#EGRESS}.
 *   <li>v2 endpoint references, {@code https://docs.bitfinex.com/reference/<endpoint>}: public
 *       trades 15, stats 15, candles 30, tickers 30, platform status 30, derivatives status 90,
 *       conf 90, book 240; authenticated wallets, orders, orders history, order trades, trades,
 *       positions, ledgers, movements and derivative collateral set 90; deposit address 10. The
 *       transfer and withdraw references state no limit.
 *   <li>v1 reference, https://docs.bitfinex.com/v1/docs/rest-general: "10 to 90 requests per minute
 *       depending on factors", no per-endpoint figures.
 * </ul>
 *
 * <p><b>Budgets.</b> Every budget is a rolling one-minute window per egress IP. The v2 budgets
 * mirror the documented per-endpoint figures, one budget per documented endpoint family. {@value
 * #V1_PUBLIC}, {@value #V1_AUTH} and {@value #V2_AUTH_OTHER} (v2 transfer) are <em>not published
 * quotas</em>: they carry the 90 requests per minute the module enforced before the universal
 * limiter (the upper end of the documented range) for endpoints whose reference states no figure.
 * {@value #DEPOSIT_ADDRESS} is the documented v2 deposit-address limit of 10; the v1 {@code
 * deposit/new} request consumes it as well on the assumption that it is the same provider function
 * (the v1 reference publishes no figure). Costs are always 1: Bitfinex documents no request
 * weights.
 *
 * <p><b>Operation classes.</b> {@link RateLimitPriority#MARKET_DATA} covers public GET endpoints;
 * {@link RateLimitPriority#EXECUTION} covers every authenticated endpoint. An unknown path is
 * unclassified and rejected before anything is sent. Only public GETs and the v2 authenticated read
 * POSTs are replayed after a confirmed rate rejection. Every v1 authenticated POST is surfaced
 * instead and never replayed by the limiter, because the mutations (order, offer, cancel, replace,
 * withdraw, deposit, transfer, collateral) may have been applied. The nonce of every authenticated
 * call, v1 and v2, is created inside the admitted wire attempt (v1: stamped into the body when it
 * is signed, v2: evaluated as a header), so a call that waited in admission never sends a nonce
 * older than one consumed meanwhile.
 *
 * <p><b>Bounds.</b> The 60 second IP block is documented, so the fallback backoff starts at 60 s
 * (cap 120 s, jitter 0.1) and market-data operations may wait up to 90 s in total; execution
 * operations wait at most 5 s and therefore fail terminally instead of parking behind a block. 3
 * attempts; 256 pending market-data and 64 pending execution operations.
 *
 * @since 1.0.3
 */
public final class BitfinexRateLimitPolicy {

  /** Policy namespace of the Bitfinex REST API. */
  public static final String NAMESPACE = "bitfinex.rest";

  /** Policy version, recorded in context diagnostics. */
  public static final String VERSION = "2026-10-06.1";

  static final String V1_PUBLIC = "bitfinex.v1.public";
  static final String V1_AUTH = "bitfinex.v1.auth";
  static final String DEPOSIT_ADDRESS = "bitfinex.deposit-address";
  static final String V2_PLATFORM_STATUS = "bitfinex.v2.platform-status";
  static final String V2_TICKERS = "bitfinex.v2.tickers";
  static final String V2_DERIVATIVES_STATUS = "bitfinex.v2.derivatives-status";
  static final String V2_PUBLIC_TRADES = "bitfinex.v2.public-trades";
  static final String V2_CANDLES = "bitfinex.v2.candles";
  static final String V2_STATS = "bitfinex.v2.stats";
  static final String V2_BOOK = "bitfinex.v2.book";
  static final String V2_CONF = "bitfinex.v2.conf";
  static final String V2_POSITIONS = "bitfinex.v2.auth.positions";
  static final String V2_WALLETS = "bitfinex.v2.auth.wallets";
  static final String V2_TRADES = "bitfinex.v2.auth.trades";
  static final String V2_ORDERS = "bitfinex.v2.auth.orders";
  static final String V2_ORDERS_HIST = "bitfinex.v2.auth.orders-hist";
  static final String V2_ORDER_TRADES = "bitfinex.v2.auth.order-trades";
  static final String V2_LEDGERS = "bitfinex.v2.auth.ledgers";
  static final String V2_MOVEMENTS = "bitfinex.v2.auth.movements";
  static final String V2_COLLATERAL = "bitfinex.v2.auth.collateral-set";
  static final String V2_AUTH_OTHER = "bitfinex.v2.auth.other";

  /** Budget id to requests per minute, in declaration order. */
  private static final Map<String, Long> LIMITS_PER_MINUTE = limits();

  private static final String SOURCE =
      "Bitfinex requirements and limitations (10-90 requests/minute depending on endpoint, IP"
          + " blocked for 60 s, ERR_RATE_LIMIT) https://docs.bitfinex.com/docs/requirements-and-limitations"
          + " retrieved 2026-10-06; v2 per-endpoint limits (public trades 15, stats 15, candles 30,"
          + " tickers 30, platform status 30, derivatives status 90, conf 90, book 240, authenticated"
          + " wallets/orders/orders history/order trades/trades/positions/ledgers/movements/derivative"
          + " collateral set 90, deposit address 10) https://docs.bitfinex.com/reference/<endpoint>"
          + " retrieved 2026-10-06; v1 reference (10-90 requests/minute, no per-endpoint figure)"
          + " https://docs.bitfinex.com/v1/docs/rest-general retrieved 2026-10-06; v1 public, v1"
          + " authenticated and v2 transfer budgets of 90/minute are the module's pre-existing in-code"
          + " value, not a published per-endpoint quota; v1 deposit/new sharing the deposit-address 10"
          + "/minute budget is an assumption; all budgets are per egress IP as the 60 s block is IP-wide";

  /** v1 authenticated POST paths: reads, mutations and account operations. */
  private static final Set<String> V1_AUTHENTICATED_PATHS =
      Set.of(
          "account_infos",
          "account_fees",
          "order/new",
          "order/new/multi",
          "offer/new",
          "balances",
          "order/cancel",
          "order/cancel/all",
          "order/cancel/multi",
          "order/cancel/replace",
          "offer/cancel",
          "orders",
          "orders/hist",
          "offers",
          "positions",
          "order/status",
          "offer/status",
          "mytrades",
          "mytrades_funding",
          "credits",
          "margin_infos",
          "withdraw",
          "deposit/new",
          "history/movements",
          "history");

  private static final RateLimitPolicy DEFAULT = create();

  private BitfinexRateLimitPolicy() {}

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
    List<RateLimitBudget> budgets = new ArrayList<>();
    LIMITS_PER_MINUTE.forEach(
        (id, limit) ->
            budgets.add(
                RateLimitBudget.rollingWindow(id, ScopeKind.EGRESS, limit, Duration.ofMinutes(1))));
    return new RateLimitPolicy(
            NAMESPACE,
            VERSION,
            SOURCE,
            budgets,
            BitfinexRateLimitPolicy::classify,
            RateLimitFeedbackInterpreter.statuses(Set.of(429), Set.of()))
        .withPendingLimit(RateLimitPriority.MARKET_DATA, 256)
        .withPendingLimit(RateLimitPriority.EXECUTION, 64)
        .withMaxWait(RateLimitPriority.EXECUTION, Duration.ofSeconds(5))
        .withMaxWait(RateLimitPriority.MARKET_DATA, Duration.ofSeconds(90))
        .withMaxAttempts(3)
        .withFallbackBackoff(Duration.ofSeconds(60), Duration.ofSeconds(120), 0.1);
  }

  /**
   * Classifies one logical request. The operation key is {@code "<METHOD> <path>"} with the path
   * relative to the host, for example {@code GET v2/tickers} or {@code POST v1/order/new}.
   *
   * @param request the logical request
   * @return the operation, or {@code null} for an unknown method or path
   */
  static RateLimitOperation classify(RateLimitRequest request) {
    String key = request.getOperationKey();
    int space = key.indexOf(' ');
    if (space <= 0) {
      return null;
    }
    String method = key.substring(0, space);
    String path = key.substring(space + 1);
    if (path.startsWith("/")) {
      path = path.substring(1);
    }
    if (path.startsWith("v2/")) {
      return classifyV2(method, path.substring(3), key);
    }
    if (path.startsWith("v1/")) {
      return classifyV1(method, path.substring(3), key);
    }
    return null;
  }

  private static RateLimitOperation classifyV1(String method, String path, String key) {
    if ("GET".equals(method)) {
      if (startsWithSegment(path, "pubticker")
          || startsWithSegment(path, "book")
          || startsWithSegment(path, "lendbook")
          || startsWithSegment(path, "trades")
          || startsWithSegment(path, "lends")
          || path.equals("symbols")
          || path.equals("symbols_details")) {
        return operation(key, RateLimitPriority.MARKET_DATA, true, V1_PUBLIC);
      }
      return null;
    }
    if (!"POST".equals(method) || !V1_AUTHENTICATED_PATHS.contains(path)) {
      return null;
    }
    if (path.equals("deposit/new")) {
      return operation(key, RateLimitPriority.EXECUTION, false, V1_AUTH, DEPOSIT_ADDRESS);
    }
    return operation(key, RateLimitPriority.EXECUTION, false, V1_AUTH);
  }

  private static RateLimitOperation classifyV2(String method, String path, String key) {
    if ("GET".equals(method)) {
      String budget = v2PublicBudget(path);
      return budget == null ? null : operation(key, RateLimitPriority.MARKET_DATA, true, budget);
    }
    if (!"POST".equals(method)) {
      return null;
    }
    if (path.equals("auth/w/transfer")) {
      return operation(key, RateLimitPriority.EXECUTION, false, V2_AUTH_OTHER);
    }
    if (path.equals("auth/w/deriv/collateral/set")) {
      return operation(key, RateLimitPriority.EXECUTION, false, V2_COLLATERAL);
    }
    String budget = v2AuthenticatedReadBudget(path);
    return budget == null ? null : operation(key, RateLimitPriority.EXECUTION, true, budget);
  }

  private static String v2PublicBudget(String path) {
    if (path.equals("platform/status")) {
      return V2_PLATFORM_STATUS;
    }
    if (path.equals("tickers")) {
      return V2_TICKERS;
    }
    if (startsWithSegment(path, "status")) {
      return V2_DERIVATIVES_STATUS;
    }
    if (startsWithSegment(path, "trades")) {
      return V2_PUBLIC_TRADES;
    }
    if (startsWithSegment(path, "candles")) {
      return V2_CANDLES;
    }
    if (startsWithSegment(path, "stats1")) {
      return V2_STATS;
    }
    if (startsWithSegment(path, "book")) {
      return V2_BOOK;
    }
    if (startsWithSegment(path, "conf")) {
      return V2_CONF;
    }
    return null;
  }

  private static String v2AuthenticatedReadBudget(String path) {
    if (path.equals("auth/r/positions")) {
      return V2_POSITIONS;
    }
    if (path.equals("auth/r/wallets")) {
      return V2_WALLETS;
    }
    if (path.startsWith("auth/r/trades/") && path.endsWith("/hist")) {
      return V2_TRADES;
    }
    if (path.equals("auth/r/orders") || path.startsWith("auth/r/orders/")) {
      return path.endsWith("/hist") ? V2_ORDERS_HIST : V2_ORDERS;
    }
    if (path.startsWith("auth/r/order/") && path.endsWith("/trades")) {
      return V2_ORDER_TRADES;
    }
    if (path.startsWith("auth/r/ledgers/") && path.endsWith("/hist")) {
      return V2_LEDGERS;
    }
    if (path.startsWith("auth/r/movements/") && path.endsWith("/hist")) {
      return V2_MOVEMENTS;
    }
    return null;
  }

  private static boolean startsWithSegment(String path, String segment) {
    return path.equals(segment) || path.startsWith(segment + "/");
  }

  private static RateLimitOperation operation(
      String key, RateLimitPriority priority, boolean replay, String... budgetIds) {
    Map<String, Long> costs = new LinkedHashMap<>();
    for (String id : budgetIds) {
      costs.put(id, 1L);
    }
    return new RateLimitOperation(key, priority, costs, replay);
  }

  private static Map<String, Long> limits() {
    Map<String, Long> map = new LinkedHashMap<>();
    map.put(V1_PUBLIC, 90L);
    map.put(V1_AUTH, 90L);
    map.put(DEPOSIT_ADDRESS, 10L);
    map.put(V2_PLATFORM_STATUS, 30L);
    map.put(V2_TICKERS, 30L);
    map.put(V2_DERIVATIVES_STATUS, 90L);
    map.put(V2_PUBLIC_TRADES, 15L);
    map.put(V2_CANDLES, 30L);
    map.put(V2_STATS, 15L);
    map.put(V2_BOOK, 240L);
    map.put(V2_CONF, 90L);
    map.put(V2_POSITIONS, 90L);
    map.put(V2_WALLETS, 90L);
    map.put(V2_TRADES, 90L);
    map.put(V2_ORDERS, 90L);
    map.put(V2_ORDERS_HIST, 90L);
    map.put(V2_ORDER_TRADES, 90L);
    map.put(V2_LEDGERS, 90L);
    map.put(V2_MOVEMENTS, 90L);
    map.put(V2_COLLATERAL, 90L);
    map.put(V2_AUTH_OTHER, 90L);
    return map;
  }
}
