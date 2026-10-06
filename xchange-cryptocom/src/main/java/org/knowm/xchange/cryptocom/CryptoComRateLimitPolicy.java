package org.knowm.xchange.cryptocom;

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
 * Default rate-limit policy of the Crypto.com Exchange v1 REST API, enabled by {@link
 * CryptoComExchange#getDefaultExchangeSpecification()}.
 *
 * <p><b>Source (retrieved 2026-10-06).</b> Crypto.com Exchange API v1, "Rate Limits" / "REST API",
 * https://exchange-docs.crypto.com/exchange/v1/rest-ws/index.html (also published at
 * https://exchange-developer.crypto.com/exchange/v1): authenticated calls are limited per API
 * method and per API key: {@code private/create-order}, {@code private/cancel-order} and {@code
 * private/cancel-all-orders} 15 requests per 100 ms each, {@code private/get-order-detail} 30
 * requests per 100 ms, {@code private/get-trades} and {@code private/get-order-history} 1 request
 * per second, all other private methods 3 requests per 100 ms each; public market-data calls are
 * limited per API method and per IP address at 100 requests per second each ({@code
 * public/get-book}, {@code public/get-ticker}, {@code public/get-trades}, {@code
 * public/get-valuations}, {@code public/get-candlestick}, {@code public/get-insurance}). A breach
 * answers HTTP 429 (code 42901, {@code TOO_MANY_REQUESTS}); no {@code Retry-After} header and no
 * IP-ban status are documented.
 *
 * <p><b>Budgets.</b> Every REST method owns one budget, because the provider counts "per API
 * method". Private budgets are {@link ScopeKind#USER} (the API key), public budgets are {@link
 * ScopeKind#EGRESS} (the IP). They are rolling windows with the documented limit and window, the
 * conservative reading of an unspecified window law. They mirror provider quotas. Three public
 * methods are not listed in the rate-limit table ({@code public/get-instruments}, {@code
 * public/get-expired-settlement-price}, {@code public/get-risk-parameters}); they are paced
 * client-side with the documented public figure of 100 requests per second each, which is a CF
 * assumption and not a provider quota. {@code public/get-tickers} is the plural wire form of the
 * documented {@code public/get-ticker}. The private methods {@code private/advanced/create-order},
 * {@code private/create-withdrawal} and {@code private/close-position} are not listed
 * individually and fall under the documented "all others" figure of 3 requests per 100 ms.
 *
 * <p><b>Weights.</b> Every documented limit counts requests, so each call costs 1 against its own
 * method budget.
 *
 * <p><b>Operation classes.</b> Public market data is {@link RateLimitPriority#MARKET_DATA}; every
 * {@code private/} method is {@link RateLimitPriority#EXECUTION}. Private calls carry their
 * signature inside the request body, so the operation key does not mark them as authenticated and
 * classification uses the {@code private/} path segment. Read-only private queries and public
 * calls are replayed after a confirmed HTTP 429; order placement ({@code private/create-order},
 * {@code private/advanced/create-order}), cancellation ({@code private/cancel-order}, {@code
 * private/cancel-all-orders}), {@code private/create-withdrawal} and {@code
 * private/close-position} are never blind-replayed. Anything not in the table is unclassified and
 * rejected before it is sent.
 *
 * <p><b>Bounds.</b> At most 256 pending market-data and 64 pending execution operations; maximum
 * total wait 60 s for market data and 5 s for execution including replays; 3 attempts; fallback
 * backoff 1 s base, 30 s cap, jitter 0.25 because the provider sends no {@code Retry-After}.
 *
 * @since 1.0.3
 */
final class CryptoComRateLimitPolicy {

  /** Policy namespace of the Crypto.com Exchange v1 REST API. */
  static final String NAMESPACE = "cryptocom.exchange.v1";

  /** Policy version, recorded in context diagnostics. */
  static final String VERSION = "2026-10-06.1";

  private static final String SOURCE =
      "Crypto.com Exchange API v1 Rate Limits (REST) https://exchange-docs.crypto.com/exchange/v1/rest-ws/index.html"
          + " retrieved 2026-10-06 (private: per API method and API key, create-order, cancel-order"
          + " and cancel-all-orders 15 per 100ms each, get-order-detail 30 per 100ms, get-trades and"
          + " get-order-history 1 per second, all others 3 per 100ms each; public: per API method"
          + " and IP, 100 per second each; HTTP 429 code 42901 TOO_MANY_REQUESTS, no Retry-After"
          + " documented); public/get-instruments, public/get-expired-settlement-price and"
          + " public/get-risk-parameters are not in the rate-limit table and are paced client-side"
          + " at the documented public 100 per second, a CF assumption; advanced/create-order,"
          + " create-withdrawal and close-position use the documented all-others private figure;"
          + " pending limits, wait bounds and fallback backoff are CF client policy";

  private static final String API_PREFIX = "exchange/v1/";

  private static final Duration HUNDRED_MILLIS = Duration.ofMillis(100);
  private static final Duration ONE_SECOND = Duration.ofSeconds(1);

  private static final int MARKET_DATA_PENDING_LIMIT = 256;
  private static final int EXECUTION_PENDING_LIMIT = 64;

  /** Operation key (after the API prefix) to endpoint definition, in policy order. */
  private static final Map<String, Endpoint> ENDPOINTS = endpoints();

  private static final RateLimitPolicy DEFAULT = create();

  private CryptoComRateLimitPolicy() {}

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
    List<RateLimitBudget> budgets = new ArrayList<>();
    for (Endpoint endpoint : ENDPOINTS.values()) {
      budgets.add(
          RateLimitBudget.rollingWindow(
              endpoint.budgetId, endpoint.scope, endpoint.limit, endpoint.window));
    }
    return new RateLimitPolicy(
            NAMESPACE,
            VERSION,
            SOURCE,
            budgets,
            CryptoComRateLimitPolicy::classify,
            RateLimitFeedbackInterpreter.statuses(Set.of(429), Set.of()))
        .withPendingLimit(RateLimitPriority.MARKET_DATA, MARKET_DATA_PENDING_LIMIT)
        .withPendingLimit(RateLimitPriority.EXECUTION, EXECUTION_PENDING_LIMIT)
        .withMaxWait(RateLimitPriority.EXECUTION, Duration.ofSeconds(5))
        .withMaxWait(RateLimitPriority.MARKET_DATA, Duration.ofSeconds(60))
        .withMaxAttempts(3)
        .withFallbackBackoff(Duration.ofSeconds(1), Duration.ofSeconds(30), 0.25);
  }

  /**
   * Classifies one logical Crypto.com request. The operation key is {@code "<METHOD>
   * exchange/v1/<api method>"}; an unknown method yields {@code null}.
   *
   * @param request the logical request
   * @return the operation, or {@code null} for an unknown method
   */
  static RateLimitOperation classify(RateLimitRequest request) {
    String key = request.getOperationKey();
    int space = key.indexOf(' ');
    if (space <= 0) {
      return null;
    }
    String httpMethod = key.substring(0, space);
    String path = key.substring(space + 1);
    if (path.startsWith(API_PREFIX)) {
      path = path.substring(API_PREFIX.length());
    }
    Endpoint endpoint = ENDPOINTS.get(path);
    if (endpoint == null || !endpoint.httpMethod.equals(httpMethod)) {
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
    // Public market data: per method and IP, 100 per second each.
    publicEndpoint(map, "public/get-book");
    publicEndpoint(map, "public/get-tickers");
    publicEndpoint(map, "public/get-trades");
    publicEndpoint(map, "public/get-candlestick");
    publicEndpoint(map, "public/get-instruments");
    publicEndpoint(map, "public/get-expired-settlement-price");
    publicEndpoint(map, "public/get-risk-parameters");
    // Private, documented explicitly. Order placement/cancellation are never replayed.
    privateEndpoint(map, "private/create-order", 15, HUNDRED_MILLIS, false);
    privateEndpoint(map, "private/cancel-order", 15, HUNDRED_MILLIS, false);
    privateEndpoint(map, "private/cancel-all-orders", 15, HUNDRED_MILLIS, false);
    privateEndpoint(map, "private/get-order-detail", 30, HUNDRED_MILLIS, true);
    privateEndpoint(map, "private/get-trades", 1, ONE_SECOND, true);
    privateEndpoint(map, "private/get-order-history", 1, ONE_SECOND, true);
    // Private, documented under "all others": 3 per 100 ms each.
    privateEndpoint(map, "private/user-balance", 3, HUNDRED_MILLIS, true);
    privateEndpoint(map, "private/get-open-orders", 3, HUNDRED_MILLIS, true);
    privateEndpoint(map, "private/get-deposit-address", 3, HUNDRED_MILLIS, true);
    privateEndpoint(map, "private/get-deposit-history", 3, HUNDRED_MILLIS, true);
    privateEndpoint(map, "private/get-withdrawal-history", 3, HUNDRED_MILLIS, true);
    privateEndpoint(map, "private/get-positions", 3, HUNDRED_MILLIS, true);
    privateEndpoint(map, "private/get-accounts", 3, HUNDRED_MILLIS, true);
    privateEndpoint(map, "private/get-fee-rate", 3, HUNDRED_MILLIS, true);
    privateEndpoint(map, "private/get-fee-credit-balances", 3, HUNDRED_MILLIS, true);
    privateEndpoint(map, "private/user-balance-history", 3, HUNDRED_MILLIS, true);
    privateEndpoint(map, "private/get-transactions", 3, HUNDRED_MILLIS, true);
    privateEndpoint(map, "private/create-withdrawal", 3, HUNDRED_MILLIS, false);
    privateEndpoint(map, "private/advanced/create-order", 3, HUNDRED_MILLIS, false);
    privateEndpoint(map, "private/close-position", 3, HUNDRED_MILLIS, false);
    return Collections.unmodifiableMap(map);
  }

  private static void publicEndpoint(Map<String, Endpoint> map, String path) {
    map.put(
        path,
        new Endpoint(
            "GET",
            "cryptocom." + path.replace('/', '.'),
            ScopeKind.EGRESS,
            100,
            ONE_SECOND,
            RateLimitPriority.MARKET_DATA,
            true));
  }

  private static void privateEndpoint(
      Map<String, Endpoint> map, String path, long limit, Duration window, boolean replay) {
    map.put(
        path,
        new Endpoint(
            "POST",
            "cryptocom." + path.replace('/', '.'),
            ScopeKind.USER,
            limit,
            window,
            RateLimitPriority.EXECUTION,
            replay));
  }

  private static final class Endpoint {
    final String httpMethod;
    final String budgetId;
    final ScopeKind scope;
    final long limit;
    final Duration window;
    final RateLimitPriority priority;
    final boolean replayOnRateLimit;

    Endpoint(
        String httpMethod,
        String budgetId,
        ScopeKind scope,
        long limit,
        Duration window,
        RateLimitPriority priority,
        boolean replayOnRateLimit) {
      this.httpMethod = httpMethod;
      this.budgetId = budgetId;
      this.scope = scope;
      this.limit = limit;
      this.window = window;
      this.priority = priority;
      this.replayOnRateLimit = replayOnRateLimit;
    }
  }
}
