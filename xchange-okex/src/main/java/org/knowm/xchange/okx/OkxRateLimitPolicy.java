package org.knowm.xchange.okx;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.knowm.xchange.client.ratelimit.RateLimitBudget;
import org.knowm.xchange.client.ratelimit.RateLimitBudget.ScopeKind;
import org.knowm.xchange.client.ratelimit.RateLimitFeedback;
import org.knowm.xchange.client.ratelimit.RateLimitFeedbackInterpreter;
import org.knowm.xchange.client.ratelimit.RateLimitOperation;
import org.knowm.xchange.client.ratelimit.RateLimitPolicy;
import org.knowm.xchange.client.ratelimit.RateLimitPriority;
import org.knowm.xchange.client.ratelimit.RateLimitRequest;
import org.knowm.xchange.okx.dto.OkxException;
import org.knowm.xchange.okx.dto.OkxResponse;
import org.knowm.xchange.okx.dto.trade.OkxAlgoOrderResponse;
import org.knowm.xchange.okx.dto.trade.OkxOrderResponse;

/**
 * Default rate-limit policy of the OKX v5 REST API, enabled by {@link
 * OkxExchange#getDefaultExchangeSpecification()}. It replaces the module-owned resilience4j rate
 * limiters: every REST wire attempt is admitted and its HTTP feedback interpreted exactly once by
 * the universal rate limiter of {@code xchange-core}.
 *
 * <p><b>Sources (retrieved 2026-10-06).</b>
 *
 * <ul>
 *   <li>OKX API v5 "Rate Limits" overview and per-endpoint "Rate Limit / Rate limit rule" lines,
 *       https://www.okx.com/docs-v5/en/#overview-rate-limits : public unauthenticated REST limits
 *       are per IP address, private REST limits are per User ID (every sub-account has its own User
 *       ID); place, amend and cancel limits are independent from each other; a multiple-order
 *       endpoint is independent from its single-order endpoint; sub-account limit of 1000 new plus
 *       amended orders per 2 seconds (Tier 1, the default; higher tiers depend on the fill ratio),
 *       counting every order of a batch individually, answered with error code 50061.
 *   <li>OKX API v5 error code table, https://www.okx.com/docs-v5/en/#error-code : rate limit
 *       reached is error code 50011 and is delivered with HTTP status 429 (also 58102 for
 *       transfers); error 50061 is delivered with HTTP 200. No {@code Retry-After} header is
 *       documented, so one is honoured when present and the finite fallback backoff applies
 *       otherwise.
 *   <li>Existing module knowledge: {@code GET /asset/piggy-balance} is no longer in the current
 *       documentation (OKX replaced it with {@code /finance/savings/balance}, 6 requests per
 *       second); its pre-existing in-code value of 6 requests per second is retained and is a
 *       <em>legacy, undocumented</em> value.
 * </ul>
 *
 * <p><b>Budgets.</b> One rolling-window budget per documented endpoint limit; public endpoints are
 * keyed by process egress ({@link ScopeKind#EGRESS}, the IP of the provider rule), private
 * endpoints by user ({@link ScopeKind#USER}). OKX additionally keys several limits by instrument
 * ID, instrument type, instrument family or currency. The core budget model has no such dimension,
 * and a REST request body is not visible to the classifier, so each documented per-dimension limit
 * is applied as a single aggregate client budget. This is deliberately conservative: it never
 * admits more than OKX would per dimension, but it can delay traffic spread over many instruments.
 * {@code GET /trade/order} and {@code POST /trade/order} share a path but are independent provider
 * limits and therefore separate budgets. Batch endpoints are documented in orders per 2 seconds and
 * take up to 20 orders per request (10 for cancel algo orders); the order count is only in the
 * body, so each batch request is charged the documented maximum (conservative). The Tier 1
 * sub-account budget {@value #SUBACCOUNT_ORDERS} is charged in addition for every request that
 * places or amends orders. Limits are shared by OKX with the WebSocket order channels; the
 * WebSocket path is not governed by this policy, so a process mixing both channels must keep its
 * own WebSocket pacing in mind. A sub-account breach reported through error 50061 (as the response
 * code of an HTTP 200 body, as the code of every order of a batch response, or as the code of a
 * decoded {@link OkxException}) is rate feedback: it starts the same cooldown as HTTP 429 and is
 * replayed only for replay-safe operations. A partially placed batch is returned to the caller.
 * Responses of the deprecated {@code Okex*} interfaces use deprecated wrapper DTOs that are not
 * inspected; their exceptions are.
 *
 * <p><b>Operation classes.</b> Public market-data reads are {@link RateLimitPriority#MARKET_DATA};
 * every private account, asset, trade, fill and sub-account operation is {@link
 * RateLimitPriority#EXECUTION}. Only {@code GET} operations and order/algo cancellations are
 * replayed after a confirmed rate rejection (cancellation is replay-safe by request identity, see
 * the module README); every other mutation (order placement, amendment, withdrawal, transfer,
 * leverage, position mode, margin change) is surfaced instead of being blindly replayed. A
 * reachable operation that is not in this policy is unclassified and rejected before anything is
 * sent.
 *
 * <p><b>Bounds.</b> At most 512 pending market-data and 64 pending execution operations, maximum
 * total wait 5 s for execution and 60 s for market data including replays, 3 attempts, fallback
 * backoff 1 s base, 30 s cap, jitter 0.25 (core defaults).
 *
 * @since 1.0.3
 */
public final class OkxRateLimitPolicy {

  /** Policy namespace of the OKX v5 REST API. */
  public static final String NAMESPACE = "okx.rest";

  /** Policy version, recorded in context diagnostics. */
  public static final String VERSION = "2026-10-06.2";

  /**
   * Tier 1 sub-account budget: 1000 new plus amended orders per 2 seconds per User ID, counted per
   * order for batch requests.
   */
  public static final String SUBACCOUNT_ORDERS = "okx.user.order-rate";

  /** Pending market-data operations. */
  public static final int MARKET_DATA_PENDING_LIMIT = 512;

  /** Pending execution operations, a bound market data cannot consume. */
  public static final int EXECUTION_PENDING_LIMIT = 64;

  /** OKX error code of a sub-account order-rate breach, delivered with HTTP 200. */
  private static final String SUBACCOUNT_RATE_LIMIT_CODE = "50061";

  private static final String SOURCE =
      "OKX API v5 rate limits (public per IP, private per User ID; per-endpoint limits; sub-account"
          + " 1000 orders/2s Tier 1; error 50011) https://www.okx.com/docs-v5/en/#overview-rate-limits"
          + " retrieved 2026-10-06; OKX error codes (50011 and 58102 delivered as HTTP 429, 50061 as"
          + " HTTP 200, interpreted from the response body) https://www.okx.com/docs-v5/en/#error-code"
          + " retrieved 2026-10-06;"
          + " per-instrument, per-instrument-type and per-currency limits are applied as single"
          + " aggregate client budgets and batch requests are charged their documented maximum order"
          + " count (client-side conservative pacing, not a provider quota);"
          + " /asset/piggy-balance 6/s is a legacy in-code value absent from the current OKX"
          + " documentation";

  private static final String API_PREFIX = "api/v5/";

  private static final Duration SECOND = Duration.ofSeconds(1);
  private static final Duration TWO_SECONDS = Duration.ofSeconds(2);

  private static final long BATCH = 20;
  private static final long ALGO_CANCEL_BATCH = 10;

  private static final List<Endpoint> ENDPOINTS = endpoints();

  private static final Map<String, Endpoint> BY_KEY = index(ENDPOINTS);

  private static final RateLimitPolicy DEFAULT = create();

  private OkxRateLimitPolicy() {}

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
    Set<String> seen = new LinkedHashSet<>();
    for (Endpoint endpoint : ENDPOINTS) {
      if (seen.add(endpoint.budget().getId())) {
        budgets.add(endpoint.budget());
      }
    }
    budgets.add(
        RateLimitBudget.rollingWindow(SUBACCOUNT_ORDERS, ScopeKind.USER, 1000, TWO_SECONDS));
    return new RateLimitPolicy(
            NAMESPACE,
            VERSION,
            SOURCE,
            budgets,
            OkxRateLimitPolicy::classify,
            RateLimitFeedbackInterpreter.statuses(Set.of(429), Set.of()))
        .withBodyFeedbackInterpreter(OkxRateLimitPolicy::interpretBody)
        .withPendingLimit(RateLimitPriority.MARKET_DATA, MARKET_DATA_PENDING_LIMIT)
        .withPendingLimit(RateLimitPriority.EXECUTION, EXECUTION_PENDING_LIMIT)
        .withMaxWait(RateLimitPriority.EXECUTION, Duration.ofSeconds(5))
        .withMaxWait(RateLimitPriority.MARKET_DATA, Duration.ofSeconds(60))
        .withMaxAttempts(3)
        .withFallbackBackoff(Duration.ofSeconds(1), Duration.ofSeconds(30), 0.25);
  }

  /**
   * Classifies one logical OKX REST request. The operation key is {@code "<METHOD> api/v5/<path>"}
   * as produced by the core for the {@link Okx}, {@link OkxAuthenticated} and deprecated {@code
   * Okex*} rescu interfaces.
   *
   * @param request the logical request
   * @return the operation, or {@code null} for a method/path that is not part of the OKX policy
   */
  static RateLimitOperation classify(RateLimitRequest request) {
    String key = request.getOperationKey();
    int space = key.indexOf(' ');
    if (space <= 0) {
      return null;
    }
    String path = key.substring(space + 1);
    if (!path.startsWith(API_PREFIX)) {
      return null;
    }
    Endpoint endpoint =
        BY_KEY.get(key.substring(0, space) + " " + path.substring(API_PREFIX.length()));
    if (endpoint == null) {
      return null;
    }
    Map<String, Long> requirements = new LinkedHashMap<>();
    requirements.put(endpoint.budget().getId(), endpoint.cost());
    if (endpoint.subAccountOrders()) {
      requirements.put(SUBACCOUNT_ORDERS, endpoint.cost());
    }
    return new RateLimitOperation(
        key, endpoint.priority(), requirements, endpoint.replayOnRateLimit());
  }

  /**
   * One classified OKX REST method.
   *
   * @param key {@code "<METHOD> <path>"} without the {@code /api/v5} base path and leading slash
   * @param budget the endpoint budget
   * @param cost units of {@code budget} (and of the sub-account budget) one request consumes
   * @param subAccountOrders whether the request also consumes the sub-account order budget
   * @param priority the service class
   * @param replayOnRateLimit whether replay after a confirmed rate rejection is safe
   */
  private record Endpoint(
      String key,
      RateLimitBudget budget,
      long cost,
      boolean subAccountOrders,
      RateLimitPriority priority,
      boolean replayOnRateLimit) {}

  private static Map<String, Endpoint> index(List<Endpoint> endpoints) {
    Map<String, Endpoint> byKey = new LinkedHashMap<>();
    for (Endpoint endpoint : endpoints) {
      if (byKey.put(endpoint.key(), endpoint) != null) {
        throw new IllegalStateException("duplicate OKX rate-limit endpoint " + endpoint.key());
      }
    }
    return Map.copyOf(byKey);
  }

  private static List<Endpoint> endpoints() {
    List<Endpoint> list = new ArrayList<>();
    // Public market data: per IP (egress).
    publicGet(list, "public/instruments", "instruments", 20);
    publicGet(list, "public/underlying", "underlying", 20);
    publicGet(list, "market/trades", "trades", 100);
    publicGet(list, "market/ticker", "ticker", 20);
    publicGet(list, "market/tickers", "tickers", 20);
    publicGet(list, "market/books", "books", 40);
    publicGet(list, "market/history-candles", "history-candles", 20);
    publicGet(list, "market/candles", "candles", 40);
    publicGet(list, "public/funding-rate", "funding-rate", 10);
    publicGet(list, "public/funding-rate-history", "funding-rate-history", 10);

    // Account and asset reads: per User ID.
    read(list, "account/balance", "account-balance", 10, TWO_SECONDS);
    read(list, "account/config", "account-config", 5, TWO_SECONDS);
    read(list, "account/trade-fee", "trade-fee", 5, TWO_SECONDS);
    read(list, "account/bills", "bills", 5, SECOND);
    read(list, "account/bills-archive", "bills-archive", 5, TWO_SECONDS);
    read(list, "account/positions", "positions", 10, TWO_SECONDS);
    read(list, "account/positions-history", "positions-history", 10, TWO_SECONDS);
    read(list, "account/account-position-risk", "position-risk", 10, TWO_SECONDS);
    read(list, "asset/currencies", "asset-currencies", 6, SECOND);
    read(list, "asset/balances", "asset-balances", 6, SECOND);
    read(list, "asset/deposit-address", "deposit-address", 6, SECOND);
    read(list, "asset/piggy-balance", "piggy-balance", 6, SECOND);
    read(list, "users/subaccount/list", "subaccount-list", 20, TWO_SECONDS);
    read(list, "account/subaccount/balances", "subaccount-balances", 6, TWO_SECONDS);

    // Account and asset mutations: never blindly replayed.
    mutation(list, "account/position/margin-balance", "margin-balance", 20, TWO_SECONDS, 1, false);
    mutation(list, "account/set-leverage", "set-leverage", 20, TWO_SECONDS, 1, false);
    mutation(list, "account/set-position-mode", "set-position-mode", 5, TWO_SECONDS, 1, false);
    mutation(list, "asset/withdrawal", "asset-withdrawal", 6, SECOND, 1, false);
    mutation(list, "asset/transfer", "asset-transfer", 2, SECOND, 1, false);

    // Trade reads.
    read(list, "trade/order", "order-details", 60, TWO_SECONDS);
    read(list, "trade/orders-pending", "orders-pending", 60, TWO_SECONDS);
    read(list, "trade/orders-history", "orders-history", 40, TWO_SECONDS);
    read(list, "trade/fills", "fills", 60, TWO_SECONDS);
    read(list, "trade/fills-history", "fills-history", 10, TWO_SECONDS);
    read(list, "trade/orders-algo-pending", "algo-pending", 20, TWO_SECONDS);
    read(list, "trade/orders-algo-history", "algo-history", 20, TWO_SECONDS);

    // Order placement and amendment: independent per-endpoint budgets, plus the sub-account
    // budget; economic mutations are never blindly replayed.
    order(list, "trade/order", "order-place", 60, 1, false);
    order(list, "trade/batch-orders", "order-place-batch", 300, BATCH, false);
    order(list, "trade/amend-order", "order-amend", 60, 1, false);
    order(list, "trade/amend-batch-orders", "order-amend-batch", 300, BATCH, false);

    // Cancellation: identity-keyed, replay-safe after a confirmed rate rejection (README).
    mutation(list, "trade/cancel-order", "order-cancel", 60, TWO_SECONDS, 1, true);
    mutation(
        list, "trade/cancel-batch-orders", "order-cancel-batch", 300, TWO_SECONDS, BATCH, true);

    // Algo orders.
    mutation(list, "trade/order-algo", "algo-place", 20, TWO_SECONDS, 1, false);
    mutation(list, "trade/amend-algos", "algo-amend", 20, TWO_SECONDS, 1, false);
    mutation(list, "trade/cancel-algos", "algo-cancel", 20, TWO_SECONDS, ALGO_CANCEL_BATCH, true);
    return List.copyOf(list);
  }

  private static void publicGet(List<Endpoint> list, String path, String name, long limit) {
    list.add(
        new Endpoint(
            "GET " + path,
            RateLimitBudget.rollingWindow(
                "okx.public." + name, ScopeKind.EGRESS, limit, TWO_SECONDS),
            1,
            false,
            RateLimitPriority.MARKET_DATA,
            true));
  }

  private static void read(
      List<Endpoint> list, String path, String name, long limit, Duration window) {
    list.add(
        new Endpoint(
            "GET " + path,
            RateLimitBudget.rollingWindow("okx.user." + name, ScopeKind.USER, limit, window),
            1,
            false,
            RateLimitPriority.EXECUTION,
            true));
  }

  private static void mutation(
      List<Endpoint> list,
      String path,
      String name,
      long limit,
      Duration window,
      long cost,
      boolean replay) {
    list.add(
        new Endpoint(
            "POST " + path,
            RateLimitBudget.rollingWindow("okx.user." + name, ScopeKind.USER, limit, window),
            cost,
            false,
            RateLimitPriority.EXECUTION,
            replay));
  }

  private static void order(
      List<Endpoint> list, String path, String name, long limit, long cost, boolean replay) {
    list.add(
        new Endpoint(
            "POST " + path,
            RateLimitBudget.rollingWindow("okx.user." + name, ScopeKind.USER, limit, TWO_SECONDS),
            cost,
            true,
            RateLimitPriority.EXECUTION,
            replay));
  }

  /**
   * Reads a sub-account rate breach (error 50061) from a decoded response code, the per-order codes
   * of a batch response, or a decoded module exception. A batch is rejected only when every order
   * carries 50061: a partially placed batch is returned to the caller, because a rejection would
   * hide the orders OKX accepted. OKX gives no retry delay, so the core fallback backoff applies.
   */
  static RateLimitFeedback interpretBody(Object result, Exception failure) {
    if (failure instanceof OkxException) {
      return isSubaccountRateLimit(String.valueOf(((OkxException) failure).getCode()))
          ? RateLimitFeedback.rejected(null)
          : RateLimitFeedback.NONE;
    }
    if (!(result instanceof OkxResponse)) {
      return RateLimitFeedback.NONE;
    }
    OkxResponse<?> response = (OkxResponse<?>) result;
    if (isSubaccountRateLimit(response.getCode())) {
      return RateLimitFeedback.rejected(null);
    }
    if (!(response.getData() instanceof List) || ((List<?>) response.getData()).isEmpty()) {
      return RateLimitFeedback.NONE;
    }
    for (Object item : (List<?>) response.getData()) {
      String code =
          item instanceof OkxOrderResponse
              ? ((OkxOrderResponse) item).getCode()
              : item instanceof OkxAlgoOrderResponse ? ((OkxAlgoOrderResponse) item).getCode() : null;
      if (!isSubaccountRateLimit(code)) {
        return RateLimitFeedback.NONE;
      }
    }
    return RateLimitFeedback.rejected(null);
  }

  private static boolean isSubaccountRateLimit(String code) {
    return SUBACCOUNT_RATE_LIMIT_CODE.equals(code);
  }
}
