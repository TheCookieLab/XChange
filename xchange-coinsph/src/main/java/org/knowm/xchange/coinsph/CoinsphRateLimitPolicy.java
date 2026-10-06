package org.knowm.xchange.coinsph;

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
 * Default rate-limit policy of the Coins.ph OpenAPI REST API, enabled by {@link
 * CoinsphExchange#getDefaultExchangeSpecification()}.
 *
 * <p><b>Sources (retrieved 2026-10-06).</b>
 *
 * <ul>
 *   <li>Coins.ph REST API reference, https://docs.coins.ph/rest-api/ ("LIMITS", "Api Limit
 *       Introduction"): for {@code /openapi/*} there is an IP limit of 120 requests per minute and
 *       an independent UID limit of 180 requests per minute; every route has a weight ("Weight:"
 *       counts against both limits, "Weight(IP):" and "Weight(UID):" against the named one only);
 *       HTTP 429 answers a breach with a {@code Retry-After} header (an order-count breach answers
 *       429 without it); HTTP 418 is an automated IP ban with {@code Retry-After}. The order
 *       limit is "counted against each IP and UID"; its values are only published through
 *       exchangeInfo and are not modelled here.
 *   <li>Coins.ph help center, "OpenAPI baseline rate limits, effective March 16, 2026"
 *       https://support.coins.ph/hc/en-us/articles/55218334261529: baseline lowered from 1200
 *       (IP) and 1800 (UID) to 120 and 180 requests per minute.
 * </ul>
 *
 * <p><b>Budgets.</b> {@value #IP_MINUTE} (per process egress) and {@value #UID_MINUTE} (per user
 * scope) mirror the documented per-minute provider quotas as rolling windows, the conservative
 * reading of an unspecified window law. They are provider quotas, not client pacing.
 *
 * <p><b>Weights.</b> Documented weights are applied literally: ping, time, exchangeInfo, trades 1;
 * ticker/24hr 1 with a symbol and 40 without; depth 1 for limit 5 to 100 (and the default) and 5
 * for larger limits; account 10; tradeFee 1; order placement 1, query 2, cancel 1; openOrders 10;
 * historyOrders 10 with a symbol and 40 without; myTrades 10; userDataStream 1; withdraw/apply
 * 100 against the UID budget only; deposit/address 10, deposit/history 2 and withdraw/history 2
 * against the IP budget only; fiat cash-out and fiat history 1. The support-channel endpoint is
 * not in the retrieved reference and is priced like its documented fiat siblings (1). Endpoints
 * of the account family are charged to both budgets whether or not a signature argument is
 * present, because the provider counts them per UID.
 *
 * <p><b>Operation classes.</b> Public market data is {@link RateLimitPriority#MARKET_DATA}; every
 * account-bound endpoint is {@link RateLimitPriority#EXECUTION}. Only {@code GET} operations and
 * the two POST queries of the fiat family (support-channel, history) are replayed after a
 * confirmed rate rejection; every other mutation (order placement and cancellation, listen-key
 * management, withdrawal, cash-out) is surfaced instead of being blind-replayed. Anything not in
 * the table is unclassified and rejected before it is sent.
 *
 * <p><b>Bounds.</b> At most 256 pending market-data and 64 pending execution operations; maximum
 * total wait 60 s for market data and 5 s for execution including replays; 3 attempts; fallback
 * backoff 1 s base, 30 s cap, jitter 0.25 when the provider sends no {@code Retry-After}.
 *
 * @since 1.0.3
 */
final class CoinsphRateLimitPolicy {

  /** Policy namespace of the Coins.ph OpenAPI. */
  static final String NAMESPACE = "coinsph.openapi";

  /** Policy version, recorded in context diagnostics. */
  static final String VERSION = "2026-10-06.1";

  /** Documented IP limit: 120 weight per minute across {@code /openapi/*}. */
  static final String IP_MINUTE = "coinsph.ip.minute";

  /** Documented UID limit: 180 weight per minute across {@code /openapi/*}. */
  static final String UID_MINUTE = "coinsph.uid.minute";

  static final long IP_LIMIT = 120;
  static final long UID_LIMIT = 180;

  private static final String SOURCE =
      "Coins.ph REST API reference https://docs.coins.ph/rest-api/ retrieved 2026-10-06 (IP limit"
          + " 120/min and UID limit 180/min across /openapi/*, per-route weights, 429 and 418 with"
          + " Retry-After); Coins.ph help center OpenAPI baseline rate limits effective 2026-03-16"
          + " https://support.coins.ph/hc/en-us/articles/55218334261529 retrieved 2026-10-06;"
          + " order-count limits are published only via exchangeInfo and are not modelled;"
          + " fiat support-channel weight 1 is a sibling-based estimate, not a documented value;"
          + " pending limits, wait bounds and fallback backoff are CF client policy";

  private static final String OPENAPI_PREFIX = "openapi/";
  private static final String V1_PREFIX = "v1/";

  private static final int MARKET_DATA_PENDING_LIMIT = 256;
  private static final int EXECUTION_PENDING_LIMIT = 64;

  private static final RateLimitPolicy DEFAULT = create();

  private CoinsphRateLimitPolicy() {}

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
    return new RateLimitPolicy(
            NAMESPACE,
            VERSION,
            SOURCE,
            List.of(
                RateLimitBudget.rollingWindow(
                    IP_MINUTE, ScopeKind.EGRESS, IP_LIMIT, Duration.ofMinutes(1)),
                RateLimitBudget.rollingWindow(
                    UID_MINUTE, ScopeKind.USER, UID_LIMIT, Duration.ofMinutes(1))),
            CoinsphRateLimitPolicy::classify,
            RateLimitFeedbackInterpreter.statuses(Set.of(429), Set.of(418)))
        .withPendingLimit(RateLimitPriority.MARKET_DATA, MARKET_DATA_PENDING_LIMIT)
        .withPendingLimit(RateLimitPriority.EXECUTION, EXECUTION_PENDING_LIMIT)
        .withMaxWait(RateLimitPriority.EXECUTION, Duration.ofSeconds(5))
        .withMaxWait(RateLimitPriority.MARKET_DATA, Duration.ofSeconds(60))
        .withMaxAttempts(3)
        .withFallbackBackoff(Duration.ofSeconds(1), Duration.ofSeconds(30), 0.25);
  }

  /**
   * Classifies one logical Coins.ph request. The operation key is {@code "<METHOD> <path>"} where
   * the path is the interface path joined with the method path; the {@code openapi/} and {@code
   * v1/} prefixes are insignificant so the methods inherited by the authenticated interface
   * classify like the public ones.
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
    String method = key.substring(0, space);
    String path = normalizePath(key.substring(space + 1));
    switch (method + " " + path) {
      case "GET ping":
      case "GET time":
      case "GET exchangeInfo":
      case "GET trades":
        return publicOperation(method, path, 1);
      case "GET ticker/24hr":
        return publicOperation(method, path, request.getParameter("symbol") == null ? 40 : 1);
      case "GET depth":
        return publicOperation(method, path, depthWeight(request.getParameter("limit")));
      case "GET account":
      case "GET openOrders":
      case "GET myTrades":
        return accountOperation(method, path, RateLimitPriority.EXECUTION, 10, 10, true);
      case "GET asset/tradeFee":
      case "POST fiat/v2/history":
      case "POST fiat/v1/support-channel":
        return accountOperation(method, path, RateLimitPriority.EXECUTION, 1, 1, true);
      case "GET historyOrders":
        long historyWeight = request.getParameter("symbol") == null ? 40 : 10;
        return accountOperation(
            method, path, RateLimitPriority.EXECUTION, historyWeight, historyWeight, true);
      case "POST order":
      case "DELETE order":
      case "POST userDataStream":
      case "PUT userDataStream":
      case "DELETE userDataStream":
      case "POST fiat/v1/cash-out":
        return accountOperation(method, path, RateLimitPriority.EXECUTION, 1, 1, false);
      case "GET order":
        return accountOperation(method, path, RateLimitPriority.EXECUTION, 2, 2, true);
      case "POST wallet/v1/withdraw/apply":
        return accountOperation(method, path, RateLimitPriority.EXECUTION, 0, 100, false);
      case "GET wallet/v1/deposit/address":
        return accountOperation(method, path, RateLimitPriority.EXECUTION, 10, 0, true);
      case "GET wallet/v1/deposit/history":
      case "GET wallet/v1/withdraw/history":
        return accountOperation(method, path, RateLimitPriority.EXECUTION, 2, 0, true);
      default:
        return null;
    }
  }

  private static RateLimitOperation publicOperation(String method, String path, long ipWeight) {
    Map<String, Long> costs = new LinkedHashMap<>();
    costs.put(IP_MINUTE, ipWeight);
    return new RateLimitOperation(
        method + " " + path, RateLimitPriority.MARKET_DATA, costs, true);
  }

  private static RateLimitOperation accountOperation(
      String method,
      String path,
      RateLimitPriority priority,
      long ipWeight,
      long uidWeight,
      boolean replay) {
    Map<String, Long> costs = new LinkedHashMap<>();
    if (ipWeight > 0) {
      costs.put(IP_MINUTE, ipWeight);
    }
    if (uidWeight > 0) {
      costs.put(UID_MINUTE, uidWeight);
    }
    return new RateLimitOperation(method + " " + path, priority, costs, replay);
  }

  /**
   * Documented depth weight: 1 for limits 5, 10, 20, 50, 100 (and the default of 100), 5 for 200.
   * Any other value is priced at the heavier weight.
   */
  private static long depthWeight(String limit) {
    if (limit == null) {
      return 1;
    }
    try {
      long value = Long.parseLong(limit);
      return value >= 1 && value <= 100 ? 1 : 5;
    } catch (NumberFormatException e) {
      return 5;
    }
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
    if (path.startsWith(OPENAPI_PREFIX)) {
      path = path.substring(OPENAPI_PREFIX.length());
    }
    if (path.startsWith(V1_PREFIX)) {
      path = path.substring(V1_PREFIX.length());
    }
    return path;
  }
}
