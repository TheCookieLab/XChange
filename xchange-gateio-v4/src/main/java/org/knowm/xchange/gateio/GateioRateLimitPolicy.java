package org.knowm.xchange.gateio;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
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
 * Default rate-limit policy of the Gate.io API v4 REST API (spot and perpetual futures), enabled by
 * {@link GateioExchange#getDefaultExchangeSpecification()}.
 *
 * <p><b>Source (retrieved 2026-10-06).</b> Gate API v4 "Frequency limit rule" and "Rate Limit"
 * sections, https://www.gate.com/docs/developers/apiv4/en/ (content read from the identical
 * mini-app publication https://miniapp.gate.com/docs/developers/apiv4/en/): public endpoints
 * 200 requests per 10 s per endpoint and IP; wallet withdrawal ({@code POST /withdrawals}) 1
 * request per 3 s per UID; all other wallet, spot and futures private endpoints 200 requests per
 * 10 s per endpoint and UID; spot order placement and amendment (single and batch) a total of 10
 * requests per second per UID and market; spot order cancellation (single and batch) a total of 200
 * requests per second; perpetual futures order placement and amendment (single and batch) a total
 * of 100 requests per second and cancellation 200 requests per second (the base tier; the
 * documented fill-ratio tiers raise it and are not modelled). The provider documents the response
 * headers {@code X-Gate-RateLimit-Requests-Remain}, {@code X-Gate-RateLimit-Limit} and {@code
 * X-Gate-RateLimit-Reset-Timestamp} and lists status 429 "Too many requests". No {@code
 * Retry-After} header and no IP-ban status are documented.
 *
 * <p><b>Budgets.</b> Provider quotas are {@link ScopeKind#USER} (UID) for private endpoints and
 * {@link ScopeKind#EGRESS} (IP) for public endpoints. They are rolling windows with the documented
 * limit and window, the conservative reading of an unspecified window law. Spot order placement,
 * batch placement and amendment share the single budget {@code gateio.spot.order-write}; spot
 * single cancel, cancel-all and batch cancel share {@code gateio.spot.order-cancel}; futures
 * placement and amendment share {@code gateio.futures.order-write}; futures cancel is {@code
 * gateio.futures.order-cancel}. Every other endpoint owns one budget per HTTP method and path
 * because the provider counts "per endpoint". The spot placement quota is documented per UID and
 * market, but a budget id cannot carry the market, so it is paced per UID, which is stricter than
 * the provider for multi-market traders; this is a deliberate client-side conservatism and not a
 * provider quota. Futures budgets are shared across settlement currencies for the same reason.
 *
 * <p><b>Weights.</b> Every documented limit counts requests, so each call costs 1 against its
 * budget; a batch request counts as one request.
 *
 * <p><b>Operation classes.</b> Public market data is {@link RateLimitPriority#MARKET_DATA}; every
 * authenticated endpoint is {@link RateLimitPriority#EXECUTION}. Read-only calls are replayed after
 * a confirmed HTTP 429. Order placement, amendment, cancellation (single, all and batch), the
 * countdown-cancel timer, withdrawals and position-leverage updates are never blind-replayed.
 * Anything not in the table is unclassified and rejected before it is sent.
 *
 * <p><b>Bounds.</b> At most 256 pending market-data and 64 pending execution operations; maximum
 * total wait 60 s for market data and 5 s for execution including replays; 3 attempts; fallback
 * backoff 1 s base, 30 s cap, jitter 0.25 because the provider sends no {@code Retry-After}.
 *
 * @since 1.0.3
 */
final class GateioRateLimitPolicy {

  /** Policy namespace of the Gate.io API v4 REST API. */
  static final String NAMESPACE = "gateio.v4";

  /** Policy version, recorded in context diagnostics. */
  static final String VERSION = "2026-10-06.1";

  /** Shared budget of spot order placement, batch placement and amendment. */
  static final String SPOT_ORDER_WRITE = "gateio.spot.order-write";

  /** Shared budget of spot single, all and batch order cancellation. */
  static final String SPOT_ORDER_CANCEL = "gateio.spot.order-cancel";

  /** Shared budget of futures order placement and amendment. */
  static final String FUTURES_ORDER_WRITE = "gateio.futures.order-write";

  /** Budget of futures order cancellation. */
  static final String FUTURES_ORDER_CANCEL = "gateio.futures.order-cancel";

  /** Budget of withdrawals. */
  static final String WITHDRAW = "gateio.wallet.withdraw";

  private static final String SOURCE =
      "Gate API v4 Frequency limit rule https://www.gate.com/docs/developers/apiv4/en/ (content read"
          + " from https://miniapp.gate.com/docs/developers/apiv4/en/) retrieved 2026-10-06"
          + " (public 200 per 10s per endpoint and IP; wallet withdrawal 1 per 3s per UID; other"
          + " private endpoints 200 per 10s per endpoint and UID; spot order placement and amend 10"
          + " per second per UID and market; spot cancel 200 per second; futures placement and amend"
          + " 100 per second, futures cancel 200 per second; headers X-Gate-RateLimit-Requests-Remain,"
          + " X-Gate-RateLimit-Limit, X-Gate-RateLimit-Reset-Timestamp; HTTP 429, no Retry-After and"
          + " no ban status documented); spot placement is paced per UID because a budget id cannot"
          + " carry the market, and futures budgets span settlement currencies, both client-side"
          + " conservatism and not provider quota; pending limits, wait bounds and fallback backoff"
          + " are CF client policy";

  private static final Duration ONE_SECOND = Duration.ofSeconds(1);
  private static final Duration THREE_SECONDS = Duration.ofSeconds(3);
  private static final Duration TEN_SECONDS = Duration.ofSeconds(10);

  private static final int MARKET_DATA_PENDING_LIMIT = 256;
  private static final int EXECUTION_PENDING_LIMIT = 64;

  /** Operation key to endpoint definition, in policy order. */
  private static final Map<String, Endpoint> ENDPOINTS = endpoints();

  private static final RateLimitPolicy DEFAULT = create();

  private GateioRateLimitPolicy() {}

  /**
   * @return the shared immutable default policy
   */
  static RateLimitPolicy defaultPolicy() {
    return DEFAULT;
  }

  /**
   * @return a new policy equal in definition to {@link #defaultPolicy()}
   */
  static RateLimitPolicy create() {
    Map<String, RateLimitBudget> budgets = new LinkedHashMap<>();
    for (Endpoint endpoint : ENDPOINTS.values()) {
      budgets.putIfAbsent(
          endpoint.budgetId,
          RateLimitBudget.rollingWindow(
              endpoint.budgetId, endpoint.scope, endpoint.limit, endpoint.window));
    }
    return new RateLimitPolicy(
            NAMESPACE,
            VERSION,
            SOURCE,
            new ArrayList<>(budgets.values()),
            GateioRateLimitPolicy::classify,
            RateLimitFeedbackInterpreter.statuses(Set.of(429), Set.of()))
        .withPendingLimit(RateLimitPriority.MARKET_DATA, MARKET_DATA_PENDING_LIMIT)
        .withPendingLimit(RateLimitPriority.EXECUTION, EXECUTION_PENDING_LIMIT)
        .withMaxWait(RateLimitPriority.EXECUTION, Duration.ofSeconds(5))
        .withMaxWait(RateLimitPriority.MARKET_DATA, Duration.ofSeconds(60))
        .withMaxAttempts(3)
        .withFallbackBackoff(Duration.ofSeconds(1), Duration.ofSeconds(30), 0.25);
  }

  /**
   * Classifies one logical Gate.io request. The operation key is {@code "<METHOD> api/v4/<path>"}
   * with unexpanded path placeholders; an unknown endpoint yields {@code null}.
   *
   * @param request the logical request
   * @return the operation, or {@code null} for an unknown endpoint
   */
  static RateLimitOperation classify(RateLimitRequest request) {
    String key = request.getOperationKey();
    Endpoint endpoint = ENDPOINTS.get(key);
    if (endpoint == null) {
      return null;
    }
    return new RateLimitOperation(
        key,
        endpoint.priority,
        Collections.singletonMap(endpoint.budgetId, 1L),
        endpoint.replayOnRateLimit);
  }

  private static Map<String, Endpoint> endpoints() {
    Map<String, Endpoint> map = new LinkedHashMap<>();
    // Public market data: 200 per 10 s per endpoint and IP.
    for (String path :
        List.of(
            "spot/time",
            "spot/currencies",
            "spot/currencies/{currency}",
            "spot/trades",
            "spot/candlesticks",
            "spot/order_book",
            "wallet/currency_chains",
            "spot/currency_pairs",
            "spot/currency_pairs/{currency_pair}",
            "spot/tickers",
            "futures/usdt/contracts",
            "futures/{settle}/tickers",
            "futures/{settle}/funding_rate",
            "futures/{settle}/candlesticks")) {
      String key = "GET api/v4/" + path;
      map.put(
          key,
          new Endpoint(
              "gateio.public." + key,
              ScopeKind.EGRESS,
              200,
              TEN_SECONDS,
              RateLimitPriority.MARKET_DATA,
              true));
    }
    // Spot placement, batch placement and amendment: one shared budget, never replayed.
    shared(map, "POST api/v4/spot/orders", SPOT_ORDER_WRITE, 10, ONE_SECOND);
    shared(map, "POST api/v4/spot/batch_orders", SPOT_ORDER_WRITE, 10, ONE_SECOND);
    shared(map, "PATCH api/v4/spot/orders/{order_id}", SPOT_ORDER_WRITE, 10, ONE_SECOND);
    // Spot cancellation: one shared budget, never replayed.
    shared(map, "DELETE api/v4/spot/orders/{order_id}", SPOT_ORDER_CANCEL, 200, ONE_SECOND);
    shared(map, "DELETE api/v4/spot/orders", SPOT_ORDER_CANCEL, 200, ONE_SECOND);
    shared(map, "POST api/v4/spot/cancel_batch_orders", SPOT_ORDER_CANCEL, 200, ONE_SECOND);
    // Futures placement and amendment, and cancellation: never replayed.
    shared(map, "POST api/v4/futures/{settle}/orders", FUTURES_ORDER_WRITE, 100, ONE_SECOND);
    shared(
        map, "PUT api/v4/futures/{settle}/orders/{order_id}", FUTURES_ORDER_WRITE, 100, ONE_SECOND);
    shared(
        map,
        "DELETE api/v4/futures/{settle}/orders/{order_id}",
        FUTURES_ORDER_CANCEL,
        200,
        ONE_SECOND);
    // Withdrawal: 1 per 3 s per UID, never replayed.
    shared(map, "POST api/v4/withdrawals", WITHDRAW, 1, THREE_SECONDS);
    // Other private endpoints that mutate state: per endpoint budget, never replayed.
    privateEndpoint(map, "POST api/v4/spot/countdown_cancel_all", false);
    privateEndpoint(map, "POST api/v4/futures/{settle}/positions/{contract}/leverage", false);
    // Read-only private endpoints: 200 per 10 s per endpoint and UID, replay-safe.
    for (String key :
        List.of(
            "GET api/v4/wallet/deposit_address",
            "GET api/v4/wallet/withdraw_status",
            "GET api/v4/spot/accounts",
            "GET api/v4/wallet/fee",
            "GET api/v4/futures/{settle}/fee",
            "GET api/v4/spot/account_book",
            "GET api/v4/spot/orders",
            "GET api/v4/spot/orders/{order_id}",
            "GET api/v4/futures/{settle}/orders/{order_id}",
            "GET api/v4/spot/open_orders",
            "GET api/v4/spot/my_trades",
            "GET api/v4/wallet/saved_address",
            "GET api/v4/wallet/sub_account_transfers",
            "GET api/v4/wallet/withdrawals",
            "GET api/v4/wallet/deposits")) {
      privateEndpoint(map, key, true);
    }
    return map;
  }

  private static void shared(
      Map<String, Endpoint> map, String key, String budgetId, long limit, Duration window) {
    map.put(
        key,
        new Endpoint(budgetId, ScopeKind.USER, limit, window, RateLimitPriority.EXECUTION, false));
  }

  private static void privateEndpoint(
      Map<String, Endpoint> map, String key, boolean replayOnRateLimit) {
    map.put(
        key,
        new Endpoint(
            "gateio.private." + key,
            ScopeKind.USER,
            200,
            TEN_SECONDS,
            RateLimitPriority.EXECUTION,
            replayOnRateLimit));
  }

  private static final class Endpoint {
    final String budgetId;
    final ScopeKind scope;
    final long limit;
    final Duration window;
    final RateLimitPriority priority;
    final boolean replayOnRateLimit;

    Endpoint(
        String budgetId,
        ScopeKind scope,
        long limit,
        Duration window,
        RateLimitPriority priority,
        boolean replayOnRateLimit) {
      this.budgetId = budgetId;
      this.scope = scope;
      this.limit = limit;
      this.window = window;
      this.priority = priority;
      this.replayOnRateLimit = replayOnRateLimit;
    }
  }
}
