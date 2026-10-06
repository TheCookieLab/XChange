package org.knowm.xchange.bybit;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.knowm.xchange.bybit.dto.BybitResult;
import org.knowm.xchange.bybit.dto.trade.batch.BybitBatchResult;
import org.knowm.xchange.bybit.dto.trade.batch.BybitBatchRetExtItem;
import org.knowm.xchange.bybit.service.BybitException;
import org.knowm.xchange.client.ratelimit.RateLimitBudget;
import org.knowm.xchange.client.ratelimit.RateLimitBudget.ScopeKind;
import org.knowm.xchange.client.ratelimit.RateLimitFeedback;
import org.knowm.xchange.client.ratelimit.RateLimitFeedbackInterpreter;
import org.knowm.xchange.client.ratelimit.RateLimitOperation;
import org.knowm.xchange.client.ratelimit.RateLimitPolicy;
import org.knowm.xchange.client.ratelimit.RateLimitPriority;
import org.knowm.xchange.client.ratelimit.RateLimitRequest;

/**
 * Default rate-limit policy of the Bybit v5 REST API, enabled by {@link
 * BybitExchange#getDefaultExchangeSpecification()}.
 *
 * <p><b>Sources (retrieved 2026-10-06).</b> Bybit "Rate Limit Rules",
 * https://bybit-exchange.github.io/docs/v5/rate-limit:
 *
 * <ul>
 *   <li>HTTP IP limit: 600 requests within a 5-second window per IP across all REST traffic; a
 *       breach answers {@code 403 access too frequent} and the IP stays banned for at least 10
 *       minutes.
 *   <li>API rate limit: per UID, per endpoint, on a rolling one-second window (rolling one minute
 *       for the minute-based asset endpoints); a breach is reported as {@code "retCode": 10006,
 *       "retMsg": "Too many visits!"} in the JSON response. Responses carry {@code X-Bapi-Limit},
 *       {@code X-Bapi-Limit-Status} and {@code X-Bapi-Limit-Reset-Timestamp}.
 *   <li>The per-endpoint table (trade, position, account, asset and spot-margin sections) supplies
 *       every UID limit below. Market-data endpoints are limited by the IP limit only.
 * </ul>
 *
 * <p><b>Budgets.</b> {@value #IP} is the documented provider quota, per process egress. Each
 * authenticated endpoint has its own per-user budget named after the endpoint; they are separate
 * upstream buckets, never shared. All UID budgets are documented provider quotas except {@value
 * #POSITION_SET_RISK_LIMIT}: {@code /v5/position/set-risk-limit} is not listed in the table, so it
 * is paced like the other position mutations (10/s), a client pacing choice and not a Bybit quota.
 *
 * <p><b>Documented limits that cannot be modelled.</b> The request classifier sees the wire
 * operation and its query/form/path parameters, not the JSON body of {@code POST} requests. Two
 * body-dependent rules are therefore not representable: the category-dependent order limits (spot
 * 20/s for create, cancel, cancel-all and the batch endpoints; option cancel-all 1/s) are paced at
 * the common 10/s of inverse, linear and option, and a batch request costs one unit although
 * Bybit counts each order of the batch.
 *
 * <p><b>Body-level rejections.</b> Bybit reports a UID limit breach as {@code retCode 10006} and
 * an IP frequency breach as {@code retCode 10018}, typically in an HTTP 200 body. Either code, in
 * a decoded response, in every item of a batch or in a decoded {@link BybitException}, is rate
 * feedback: it starts the same cooldown as HTTP 429 and is replayed only for replay-safe
 * operations. A partially executed batch is returned to the caller. The body
 * interpreter does not see {@code X-Bapi-Limit-Reset-Timestamp}, so the cooldown is the fallback
 * backoff (1 s base, matching the 1 s UID window).
 *
 * <p><b>Operation classes.</b> Public market data is {@link RateLimitPriority#MARKET_DATA};
 * every authenticated operation is {@link RateLimitPriority#EXECUTION}. Reads are replayed after a
 * confirmed 429 rejection, as are the side-effect-free {@code pre-check} and the idempotent {@code
 * set-leverage} and {@code switch-mode} (the module accepts "not modified" as success). Order
 * placement, amendment, cancellation, batches, margin, trading-stop, risk-limit and transfer
 * mutations are never replayed. An operation that is not in the table is unclassified and rejected
 * before anything is sent.
 *
 * <p><b>Feedback.</b> HTTP 429 is a rate rejection (honouring {@code Retry-After}, else {@code
 * X-Bapi-Limit-Reset-Timestamp}); HTTP 403 is the documented IP ban and is never replayed.
 *
 * <p><b>Bounds.</b> At most 512 pending market-data and 64 pending execution operations; maximum
 * total wait 60 s for market data and 5 s for execution including replays; 3 attempts; fallback
 * backoff 1 s base to the documented 10 minute ban length, jitter 0.25.
 *
 * @since 1.0.3
 */
final class BybitRateLimitPolicy {

  static final String NAMESPACE = "bybit.v5";

  static final String VERSION = "2026-10-06.2";

  /** Documented 600 requests per 5 seconds per IP; every request consumes one unit. */
  static final String IP = "bybit.v5.ip";

  static final String ORDER_CREATE = "bybit.v5.order.create";
  static final String ORDER_AMEND = "bybit.v5.order.amend";
  static final String ORDER_CANCEL = "bybit.v5.order.cancel";
  static final String ORDER_CANCEL_ALL = "bybit.v5.order.cancel-all";
  static final String ORDER_CREATE_BATCH = "bybit.v5.order.create-batch";
  static final String ORDER_AMEND_BATCH = "bybit.v5.order.amend-batch";
  static final String ORDER_CANCEL_BATCH = "bybit.v5.order.cancel-batch";
  static final String ORDER_PRE_CHECK = "bybit.v5.order.pre-check";
  static final String ORDER_REALTIME = "bybit.v5.order.realtime";
  static final String ORDER_HISTORY = "bybit.v5.order.history";
  static final String EXECUTION_LIST = "bybit.v5.execution.list";
  static final String POSITION_LIST = "bybit.v5.position.list";
  static final String POSITION_CLOSED_PNL = "bybit.v5.position.closed-pnl";
  static final String POSITION_SET_LEVERAGE = "bybit.v5.position.set-leverage";
  static final String POSITION_SWITCH_MODE = "bybit.v5.position.switch-mode";
  static final String POSITION_TRADING_STOP = "bybit.v5.position.trading-stop";
  static final String POSITION_SET_RISK_LIMIT = "bybit.v5.position.set-risk-limit";
  static final String POSITION_ADD_MARGIN = "bybit.v5.position.add-margin";
  static final String POSITION_SET_AUTO_ADD_MARGIN = "bybit.v5.position.set-auto-add-margin";
  static final String ACCOUNT_WALLET_BALANCE = "bybit.v5.account.wallet-balance";
  static final String ACCOUNT_FEE_RATE = "bybit.v5.account.fee-rate";
  static final String ACCOUNT_INFO = "bybit.v5.account.info";
  static final String ACCOUNT_TRANSACTION_LOG = "bybit.v5.account.transaction-log";
  static final String ACCOUNT_COLLATERAL_INFO = "bybit.v5.account.collateral-info";
  static final String ACCOUNT_BORROW_HISTORY = "bybit.v5.account.borrow-history";
  static final String ASSET_COINS_BALANCE = "bybit.v5.asset.query-account-coins-balance";
  static final String ASSET_COIN_INFO = "bybit.v5.asset.coin.query-info";
  static final String ASSET_DELIVERY_RECORD = "bybit.v5.asset.delivery-record";
  static final String ASSET_INTER_TRANSFER = "bybit.v5.asset.inter-transfer";
  static final String SPOT_MARGIN_MAX_BORROWABLE = "bybit.v5.spot-margin.max-borrowable";

  static final int MARKET_DATA_PENDING_LIMIT = 512;
  static final int EXECUTION_PENDING_LIMIT = 64;

  private static final String SOURCE =
      "Bybit Rate Limit Rules https://bybit-exchange.github.io/docs/v5/rate-limit retrieved"
          + " 2026-10-06: HTTP IP limit 600 requests per 5 s per IP, 403 access too frequent bans"
          + " the IP for at least 10 minutes; API rate limit per UID per endpoint on a rolling 1 s"
          + " window (retCode 10006 Too many visits, read from the HTTP 200 body like retCode"
          + " 10018), limits from the Trade, Position, Account, Asset and Spot Margin Trade tables;"
          + " market data is IP limited only. Client choices, not Bybit quotas: set-risk-limit is"
          + " unlisted and paced at the 10/s of the position mutations; category-dependent order"
          + " limits use the common 10/s because the JSON body is not visible to the classifier; a"
          + " batch request costs one unit.";

  private static final Set<Integer> RATE_LIMIT_RET_CODES = Set.of(10006, 10018);

  private static final Duration IP_BAN = Duration.ofMinutes(10);

  private static final Map<String, Spec> OPERATIONS = operations();

  private static final RateLimitPolicy DEFAULT = create();

  private BybitRateLimitPolicy() {}

  /**
   * @return the shared immutable default policy
   */
  static RateLimitPolicy defaultPolicy() {
    return DEFAULT;
  }

  static RateLimitPolicy create() {
    List<RateLimitBudget> budgets = new ArrayList<>();
    budgets.add(RateLimitBudget.rollingWindow(IP, ScopeKind.EGRESS, 600, Duration.ofSeconds(5)));
    for (Spec spec : OPERATIONS.values()) {
      if (spec.userBudget != null) {
        budgets.add(
            RateLimitBudget.rollingWindow(
                spec.userBudget, ScopeKind.USER, spec.limit, spec.window));
      }
    }
    return new RateLimitPolicy(
            NAMESPACE,
            VERSION,
            SOURCE,
            budgets,
            BybitRateLimitPolicy::classify,
            BybitRateLimitPolicy::interpret)
        .withBodyFeedbackInterpreter(BybitRateLimitPolicy::interpretBody)
        .withPendingLimit(RateLimitPriority.MARKET_DATA, MARKET_DATA_PENDING_LIMIT)
        .withPendingLimit(RateLimitPriority.EXECUTION, EXECUTION_PENDING_LIMIT)
        .withMaxWait(RateLimitPriority.EXECUTION, Duration.ofSeconds(5))
        .withMaxWait(RateLimitPriority.MARKET_DATA, Duration.ofSeconds(60))
        .withMaxAttempts(3)
        .withFallbackBackoff(Duration.ofSeconds(1), IP_BAN, 0.25);
  }

  /**
   * Classifies one logical request by its operation key.
   *
   * @return the operation, or {@code null} for an operation the policy does not know
   */
  static RateLimitOperation classify(RateLimitRequest request) {
    Spec spec = OPERATIONS.get(request.getOperationKey());
    if (spec == null) {
      return null;
    }
    Map<String, Long> costs = new LinkedHashMap<>();
    costs.put(IP, 1L);
    boolean authenticated = request.isAuthenticated() && spec.userBudget != null;
    if (authenticated) {
      costs.put(spec.userBudget, 1L);
    }
    return new RateLimitOperation(
        request.getOperationKey(),
        spec.userBudget == null ? RateLimitPriority.MARKET_DATA : RateLimitPriority.EXECUTION,
        costs,
        spec.replayOnRateLimit);
  }

  /** 429 is a rejection, 403 the documented IP ban; the reset header supplies a missing delay. */
  static RateLimitFeedback interpret(
      int status, Function<String, String> headerLookup, Instant wallNow) {
    RateLimitFeedback feedback =
        RateLimitFeedbackInterpreter.statuses(Set.of(429), Set.of(403))
            .interpret(status, headerLookup, wallNow);
    if (feedback.getKind() != RateLimitFeedback.Kind.RATE_REJECTED
        || feedback.getRetryAfter() != null) {
      return feedback;
    }
    String reset = headerLookup.apply("X-Bapi-Limit-Reset-Timestamp");
    if (reset == null) {
      return feedback;
    }
    try {
      long resetMillis = Long.parseLong(reset.trim());
      return RateLimitFeedback.rejected(
          Duration.ofMillis(resetMillis - wallNow.toEpochMilli()));
    } catch (NumberFormatException e) {
      return feedback;
    }
  }

  /**
   * Reads a rate breach ({@code retCode} 10006 or 10018) from a decoded response, the item codes of
   * a batch response or a decoded module exception. A batch is rejected through its items only when
   * every item carries such a code: a partially executed batch is returned to the caller, because a
   * rejection would hide the orders Bybit accepted.
   */
  static RateLimitFeedback interpretBody(Object result, Exception failure) {
    boolean limited;
    if (failure != null) {
      limited =
          failure instanceof BybitException
              && RATE_LIMIT_RET_CODES.contains(((BybitException) failure).getRetCode());
    } else if (result instanceof BybitResult) {
      limited = RATE_LIMIT_RET_CODES.contains(((BybitResult<?>) result).getRetCode());
    } else if (result instanceof BybitBatchResult) {
      limited = isRateLimitedBatch((BybitBatchResult) result);
    } else {
      limited = false;
    }
    return limited ? RateLimitFeedback.rejected(null) : RateLimitFeedback.NONE;
  }

  private static boolean isRateLimitedBatch(BybitBatchResult result) {
    if (RATE_LIMIT_RET_CODES.contains(result.getRetCode())) {
      return true;
    }
    if (result.getRetExtInfo() == null
        || result.getRetExtInfo().getList() == null
        || result.getRetExtInfo().getList().isEmpty()) {
      return false;
    }
    for (BybitBatchRetExtItem item : result.getRetExtInfo().getList()) {
      if (item == null || item.getCode() == null || !RATE_LIMIT_RET_CODES.contains(item.getCode())) {
        return false;
      }
    }
    return true;
  }

  private static Map<String, Spec> operations() {
    Map<String, Spec> map = new LinkedHashMap<>();
    for (String path :
        List.of(
            "tickers",
            "orderbook",
            "instruments-info",
            "funding/history",
            "public-trades",
            "time",
            "open-interest",
            "risk-limit",
            "delivery-price",
            "kline")) {
      map.put("GET v5/market/" + path, Spec.publicRead());
    }
    // Trade
    map.put("POST v5/order/create", Spec.perSecond(ORDER_CREATE, 10, false));
    map.put("POST v5/order/amend", Spec.perSecond(ORDER_AMEND, 10, false));
    map.put("POST v5/order/cancel", Spec.perSecond(ORDER_CANCEL, 10, false));
    map.put("POST v5/order/cancel-all", Spec.perSecond(ORDER_CANCEL_ALL, 10, false));
    map.put("POST v5/order/create-batch", Spec.perSecond(ORDER_CREATE_BATCH, 10, false));
    map.put("POST v5/order/amend-batch", Spec.perSecond(ORDER_AMEND_BATCH, 10, false));
    map.put("POST v5/order/cancel-batch", Spec.perSecond(ORDER_CANCEL_BATCH, 10, false));
    map.put("POST v5/order/pre-check", Spec.perSecond(ORDER_PRE_CHECK, 10, true));
    map.put("GET v5/order/realtime", Spec.perSecond(ORDER_REALTIME, 50, true));
    map.put("GET v5/order/history", Spec.perSecond(ORDER_HISTORY, 50, true));
    map.put("GET v5/execution/list", Spec.perSecond(EXECUTION_LIST, 50, true));
    // Position
    map.put("GET v5/position/list", Spec.perSecond(POSITION_LIST, 50, true));
    map.put("GET v5/position/closed-pnl", Spec.perSecond(POSITION_CLOSED_PNL, 50, true));
    map.put("POST v5/position/set-leverage", Spec.perSecond(POSITION_SET_LEVERAGE, 10, true));
    map.put("POST v5/position/switch-mode", Spec.perSecond(POSITION_SWITCH_MODE, 10, true));
    map.put("POST v5/position/trading-stop", Spec.perSecond(POSITION_TRADING_STOP, 10, false));
    map.put("POST v5/position/set-risk-limit", Spec.perSecond(POSITION_SET_RISK_LIMIT, 10, false));
    map.put("POST v5/position/add-margin", Spec.perSecond(POSITION_ADD_MARGIN, 10, false));
    map.put(
        "POST v5/position/set-auto-add-margin",
        Spec.perSecond(POSITION_SET_AUTO_ADD_MARGIN, 10, false));
    // Account
    map.put("GET v5/account/wallet-balance", Spec.perSecond(ACCOUNT_WALLET_BALANCE, 50, true));
    map.put("GET v5/account/fee-rate", Spec.perSecond(ACCOUNT_FEE_RATE, 5, true));
    map.put("GET v5/account/info", Spec.perSecond(ACCOUNT_INFO, 50, true));
    map.put("GET v5/account/transaction-log", Spec.perSecond(ACCOUNT_TRANSACTION_LOG, 25, true));
    map.put("GET v5/account/collateral-info", Spec.perSecond(ACCOUNT_COLLATERAL_INFO, 50, true));
    map.put("GET v5/account/borrow-history", Spec.perSecond(ACCOUNT_BORROW_HISTORY, 50, true));
    // Asset
    map.put(
        "GET v5/asset/transfer/query-account-coins-balance",
        Spec.perSecond(ASSET_COINS_BALANCE, 5, true));
    map.put("GET v5/asset/coin/query-info", Spec.perSecond(ASSET_COIN_INFO, 5, true));
    map.put("GET v5/asset/delivery-record", Spec.perSecond(ASSET_DELIVERY_RECORD, 50, true));
    map.put(
        "POST v5/asset/transfer/inter-transfer",
        new Spec(ASSET_INTER_TRANSFER, 60, Duration.ofMinutes(1), false));
    // Spot margin
    map.put(
        "GET v5/spot-margin-trade/max-borrowable",
        Spec.perSecond(SPOT_MARGIN_MAX_BORROWABLE, 50, true));
    return Map.copyOf(map);
  }

  /** One operation: its per-user budget (null for public), limit and replay-safety. */
  private static final class Spec {
    private final String userBudget;
    private final long limit;
    private final Duration window;
    private final boolean replayOnRateLimit;

    private Spec(String userBudget, long limit, Duration window, boolean replayOnRateLimit) {
      this.userBudget = userBudget;
      this.limit = limit;
      this.window = window;
      this.replayOnRateLimit = replayOnRateLimit;
    }

    static Spec publicRead() {
      return new Spec(null, 0, Duration.ZERO, true);
    }

    static Spec perSecond(String budget, long limit, boolean replay) {
      return new Spec(budget, limit, Duration.ofSeconds(1), replay);
    }
  }
}
