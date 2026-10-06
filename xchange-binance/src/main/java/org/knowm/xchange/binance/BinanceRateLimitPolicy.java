package org.knowm.xchange.binance;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.knowm.xchange.client.ratelimit.RateLimitBudget;
import org.knowm.xchange.client.ratelimit.RateLimitBudget.ScopeKind;
import org.knowm.xchange.client.ratelimit.RateLimitFeedbackInterpreter;
import org.knowm.xchange.client.ratelimit.RateLimitOperation;
import org.knowm.xchange.client.ratelimit.RateLimitPolicy;
import org.knowm.xchange.client.ratelimit.RateLimitPriority;
import org.knowm.xchange.client.ratelimit.RateLimitRequest;

/**
 * Default rate-limit policy of the Binance REST APIs (Spot, SAPI wallet endpoints, USD-M futures,
 * COIN-M futures and Portfolio Margin), enabled by {@link BinanceExchange#getDefaultExchangeSpecification()}
 * and {@link BinanceUsExchange#getDefaultExchangeSpecification()}.
 *
 * <p><b>Sources (retrieved 2026-10-06).</b>
 *
 * <ul>
 *   <li>Spot REST API, limits and per-endpoint weights,
 *       https://github.com/binance/binance-spot-api-docs/blob/master/rest-api.md and
 *       https://developers.binance.com/docs/binance-spot-api-docs/rest-api/limits:
 *       {@code REQUEST_WEIGHT} 6,000 per minute and {@code RAW_REQUESTS} 61,000 per 5 minutes per
 *       IP; depth weight 5/25/50/250 for limit 1-100/101-500/501-1000/1001-5000; aggTrades 4;
 *       ticker/24hr 2 per symbol and 80 without symbol; account 20; myTrades 20 (5 with {@code
 *       orderId}); HTTP 429 on a breach and 418 for an IP ban, both with {@code Retry-After}. The
 *       user-data-stream weight 2 is retained from the previous module documentation.
 *   <li>Binance.US REST API, https://docs.binance.us/ (the same limits and depth weights; the
 *       Spot weight table above applies unchanged to {@link BinanceUsExchange}).
 *   <li>Wallet SAPI, https://developers.binance.com/en/docs/products/wallet/general-info.md: each
 *       SAPI endpoint has its own, independent IP limit (12,000 weight per minute) or UID limit
 *       (180,000 weight per minute); endpoint weights from the binance-connector-js wallet,
 *       sub-account, simple-earn and fiat clients (https://github.com/binance/binance-connector-js).
 *   <li>USD-M futures, https://developers.binance.com/en/docs/products/derivatives-trading-usds-futures/general-info.md
 *       and the binance-connector-js derivatives-trading-usds-futures client: endpoint weights,
 *       order endpoints weigh 0 on the IP limit and 1 on each order-count limit, {@code
 *       fundingRate}/{@code fundingInfo} share one 500 per 5 minutes per IP limit.
 *   <li>COIN-M futures, https://developers.binance.com/en/docs/products/derivatives-trading-coin-futures/general-info.md
 *       and the binance-connector-js derivatives-trading-coin-futures client: endpoint weights,
 *       order placement weighs 0 on the IP limit and 1 on the 1-minute order-count limit.
 *   <li>Portfolio Margin, https://developers.binance.com/en/docs/products/derivatives-trading-portfolio-margin/general-info.md:
 *       IP limit 6,000 per minute, order limit 1,200 per minute per account.
 * </ul>
 *
 * <p><b>Preserved in-code values.</b> The futures numeric limits (2,400 weight per minute, 1,200
 * orders per minute and 300 orders per 10 seconds) are published only through {@code
 * exchangeInfo}, which was not retrievable from the documentation host during this review; they
 * are kept from the previous module configuration ({@code BinanceResilience}) and are not newly
 * sourced. The Spot order limits (100 orders per 10 seconds and 200,000 per day per account) are
 * preserved from the same configuration; the documents above only show them as {@code exchangeInfo}
 * examples. The daily limit is enforced as 100,000 per rolling 12 hours, which bounds every 24 hour
 * interval to the provider's 200,000.
 *
 * <p><b>Budgets.</b> Weight and raw-request budgets are IP quotas, modelled as the local
 * contribution of one process egress ({@link ScopeKind#EGRESS}); order-count and UID budgets are
 * per account ({@link ScopeKind#USER}). Windows are rolling, which never admits more than the
 * provider's wall-clock-aligned windows do. Every Spot request also counts one {@code RAW_REQUESTS}
 * unit (conservative: Binance.US documents order placement and cancellation against a separate
 * 300,000 per 5 minutes raw limit).
 *
 * <p><b>Operation classes.</b> Public market data is {@link RateLimitPriority#MARKET_DATA}; every
 * order, account, wallet and user-data-stream operation is {@link RateLimitPriority#EXECUTION}.
 * Order placement and modification ({@code POST}/{@code PUT} of an order) and the withdrawal
 * request are not replayed after a confirmed rate rejection; every other operation is an
 * idempotent read, cancellation, setting or keep-alive and may be replayed with fresh admission.
 * Binance answers {@code 429} (rate rejection) and {@code 418} (IP ban); both are owned by the
 * core boundary.
 *
 * @since 1.0.3
 */
public final class BinanceRateLimitPolicy {

  /** Policy namespace of Binance (binance.com). */
  public static final String NAMESPACE = "binance";

  /** Policy namespace of Binance.US. */
  public static final String US_NAMESPACE = "binanceus";

  /** Policy version, recorded in context diagnostics. */
  public static final String VERSION = "2026-10-06.1";

  private static final String SOURCE =
      "Binance Spot REST limits and weights"
          + " https://github.com/binance/binance-spot-api-docs/blob/master/rest-api.md and"
          + " https://developers.binance.com/docs/binance-spot-api-docs/rest-api/limits retrieved"
          + " 2026-10-06 (REQUEST_WEIGHT 6000/min and RAW_REQUESTS 61000/5min per IP; HTTP 429 rate"
          + " rejection, 418 IP ban); Binance.US https://docs.binance.us/ retrieved 2026-10-06;"
          + " Wallet SAPI https://developers.binance.com/en/docs/products/wallet/general-info.md"
          + " retrieved 2026-10-06 (per-endpoint IP 12000/min and UID 180000/min; weights from"
          + " https://github.com/binance/binance-connector-js wallet, sub-account, simple-earn and"
          + " fiat clients retrieved 2026-10-06);"
          + " USD-M https://developers.binance.com/en/docs/products/derivatives-trading-usds-futures/general-info.md"
          + " retrieved 2026-10-06 (endpoint weights, funding rate/info share 500/5min per IP);"
          + " COIN-M https://developers.binance.com/en/docs/products/derivatives-trading-coin-futures/general-info.md"
          + " retrieved 2026-10-06; Portfolio Margin"
          + " https://developers.binance.com/en/docs/products/derivatives-trading-portfolio-margin/general-info.md"
          + " retrieved 2026-10-06 (IP 6000/min, orders 1200/min); futures 2400 weight/min, 1200"
          + " orders/min, 300 orders/10s and Spot 100 orders/10s, 200000 orders/day are preserved"
          + " in-code values of the former BinanceResilience configuration (exchangeInfo, their"
          + " documented source, was unreachable on 2026-10-06), not newly sourced; spot"
          + " userDataStream weight 2 retained from the previous module documentation";

  private static final RateLimitPolicy DEFAULT = create(NAMESPACE);
  private static final RateLimitPolicy US = create(US_NAMESPACE);

  private BinanceRateLimitPolicy() {}

  /**
   * @return the shared immutable default policy of binance.com; derive overrides with its {@code
   *     with*} copy methods
   */
  public static RateLimitPolicy defaultPolicy() {
    return DEFAULT;
  }

  /**
   * @return the shared immutable default policy of Binance.US (its own budget namespace, so its
   *     quotas are never mixed with binance.com in a shared context)
   */
  public static RateLimitPolicy usPolicy() {
    return US;
  }

  private static RateLimitPolicy create(String namespace) {
    Table table = new Table(namespace);
    return new RateLimitPolicy(
        namespace,
        VERSION,
        SOURCE,
        table.budgets(),
        table::classify,
        RateLimitFeedbackInterpreter.statuses(Set.of(429), Set.of(418)));
  }

  /** Cost of one request, computed from its parameters; non-positive entries are dropped. */
  private static final class Rule {
    final RateLimitPriority priority;
    final boolean replay;
    final Function<RateLimitRequest, Map<String, Long>> costs;

    Rule(
        RateLimitPriority priority,
        boolean replay,
        Function<RateLimitRequest, Map<String, Long>> costs) {
      this.priority = priority;
      this.replay = replay;
      this.costs = costs;
    }
  }

  private static final class Table {
    private static final RateLimitPriority MD = RateLimitPriority.MARKET_DATA;
    private static final RateLimitPriority EX = RateLimitPriority.EXECUTION;
    private static final Duration MINUTE = Duration.ofMinutes(1);

    private final String ns;
    private final Map<String, RateLimitBudget> budgets = new LinkedHashMap<>();
    private final Map<String, Rule> rules = new LinkedHashMap<>();

    private final String spotWeight;
    private final String spotRaw;
    private final String spotOrders10s;
    private final String spotOrdersDay;
    private final String usdmWeight;
    private final String usdmOrders1m;
    private final String usdmOrders10s;
    private final String usdmFunding;
    private final String coinmWeight;
    private final String coinmOrders1m;
    private final String pmWeight;
    private final String pmOrders1m;

    Table(String namespace) {
      this.ns = namespace;
      spotWeight = budget("spot.weight", ScopeKind.EGRESS, 6000, MINUTE);
      spotRaw = budget("spot.raw", ScopeKind.EGRESS, 61000, Duration.ofMinutes(5));
      spotOrders10s = budget("spot.orders.10s", ScopeKind.USER, 100, Duration.ofSeconds(10));
      // 200,000 per 24h cannot be a single core window (limit * period overflows exact arithmetic);
      // two consecutive 12h windows of 100,000 bound any 24h interval to at most 200,000.
      spotOrdersDay = budget("spot.orders.12h", ScopeKind.USER, 100000, Duration.ofHours(12));
      usdmWeight = budget("usdm.weight", ScopeKind.EGRESS, 2400, MINUTE);
      usdmOrders1m = budget("usdm.orders.1m", ScopeKind.USER, 1200, MINUTE);
      usdmOrders10s = budget("usdm.orders.10s", ScopeKind.USER, 300, Duration.ofSeconds(10));
      usdmFunding = budget("usdm.funding", ScopeKind.EGRESS, 500, Duration.ofMinutes(5));
      coinmWeight = budget("coinm.weight", ScopeKind.EGRESS, 2400, MINUTE);
      coinmOrders1m = budget("coinm.orders.1m", ScopeKind.USER, 1200, MINUTE);
      pmWeight = budget("pm.weight", ScopeKind.EGRESS, 6000, MINUTE);
      pmOrders1m = budget("pm.orders.1m", ScopeKind.USER, 1200, MINUTE);
      defineSpot();
      defineSapi();
      defineUsdm();
      defineCoinm();
      definePortfolioMargin();
    }

    List<RateLimitBudget> budgets() {
      return new ArrayList<>(budgets.values());
    }

    RateLimitOperation classify(RateLimitRequest request) {
      String key = normalize(request.getOperationKey());
      Rule rule = rules.get(key);
      if (rule == null) {
        return null;
      }
      return new RateLimitOperation(key, rule.priority, rule.costs.apply(request), rule.replay);
    }

    /** Strips a query template and the leading slash of the path: {@code "PUT /a/b?k={k}"}. */
    private static String normalize(String operationKey) {
      int space = operationKey.indexOf(' ');
      if (space <= 0) {
        return operationKey;
      }
      String path = operationKey.substring(space + 1).trim();
      int query = path.indexOf('?');
      if (query >= 0) {
        path = path.substring(0, query);
      }
      int start = 0;
      while (start < path.length() && path.charAt(start) == '/') {
        start++;
      }
      return operationKey.substring(0, space) + " " + path.substring(start);
    }

    private String budget(String suffix, ScopeKind scope, long limit, Duration window) {
      String id = ns + "." + suffix;
      budgets.put(id, RateLimitBudget.rollingWindow(id, scope, limit, window));
      return id;
    }

    private void rule(
        String key,
        RateLimitPriority priority,
        boolean replay,
        Function<RateLimitRequest, Map<String, Long>> costs) {
      if (rules.put(key, new Rule(priority, replay, costs)) != null) {
        throw new IllegalStateException("duplicate rate-limit rule " + key);
      }
    }

    private void fixed(String key, RateLimitPriority priority, boolean replay, Map<String, Long> costs) {
      rule(key, priority, replay, request -> costs);
    }

    // ---- Spot -------------------------------------------------------------------------------

    private Map<String, Long> spot(long weight) {
      return costs(spotWeight, weight, spotRaw, 1);
    }

    private Map<String, Long> spotOrder(long weight) {
      return costs(spotWeight, weight, spotRaw, 1, spotOrders10s, 1, spotOrdersDay, 1);
    }

    private void defineSpot() {
      fixed("GET api/v3/ping", MD, true, spot(1));
      fixed("GET api/v3/time", MD, true, spot(1));
      fixed("GET api/v3/exchangeInfo", MD, true, spot(20));
      rule("GET api/v3/depth", MD, true, r -> spot(spotDepthWeight(limit(r))));
      fixed("GET api/v3/aggTrades", MD, true, spot(4));
      fixed("GET api/v3/klines", MD, true, spot(2));
      rule("GET api/v3/ticker/24hr", MD, true, r -> spot(hasSymbol(r) ? 2 : 80));
      rule("GET api/v3/ticker/price", MD, true, r -> spot(hasSymbol(r) ? 2 : 4));
      rule("GET api/v3/ticker/bookTicker", MD, true, r -> spot(hasSymbol(r) ? 2 : 4));

      fixed("POST api/v3/order", EX, false, spotOrder(1));
      fixed("POST api/v3/order/test", EX, true, spot(1));
      fixed("GET api/v3/order", EX, true, spot(4));
      fixed("DELETE api/v3/order", EX, true, spot(1));
      fixed("DELETE api/v3/openOrders", EX, true, spot(1));
      rule("GET api/v3/openOrders", EX, true, r -> spot(hasSymbol(r) ? 6 : 80));
      fixed("GET api/v3/allOrders", EX, true, spot(20));
      fixed("GET api/v3/account", EX, true, spot(20));
      rule("GET api/v3/myTrades", EX, true, r -> spot(r.getParameter("orderId") != null ? 5 : 20));
      fixed("POST api/v3/userDataStream", EX, true, spot(2));
      fixed("PUT api/v3/userDataStream", EX, true, spot(2));
      fixed("DELETE api/v3/userDataStream", EX, true, spot(2));
    }

    private static long spotDepthWeight(int limit) {
      if (limit <= 100) {
        return 5;
      }
      if (limit <= 500) {
        return 25;
      }
      if (limit <= 1000) {
        return 50;
      }
      return 250;
    }

    // ---- Wallet SAPI: every endpoint owns an independent IP or UID limit ----------------------

    private void defineSapi() {
      sapiIp("GET sapi/v1/system/status", 1);
      sapiIp("GET sapi/v1/asset/dribblet", 1);
      sapiIp("GET sapi/v1/capital/config/getall", 10);
      sapiUid("POST sapi/v1/capital/withdraw/apply", 900, false);
      sapiIp("GET sapi/v1/capital/deposit/hisrec", 1);
      sapiUid("GET sapi/v1/capital/withdraw/history", 18000, true);
      sapiIp("GET sapi/v1/asset/assetDividend", 10);
      sapiIp("GET sapi/v1/sub-account/sub/transfer/history", 1);
      sapiIp("GET sapi/v1/sub-account/transfer/subUserHistory", 1);
      sapiUid("GET sapi/v1/fiat/orders", 45000, true);
      sapiIp("GET sapi/v1/capital/deposit/address", 10);
      sapiIp("GET sapi/v1/asset/assetDetail", 1);
      sapiIp("GET sapi/v1/asset/tradeFee", 1);
      sapiIp("GET sapi/v1/simple-earn/account", 150);
      sapiIp("GET sapi/v1/simple-earn/flexible/position", 150);
      sapiIp("GET sapi/v1/simple-earn/locked/position", 150);
    }

    private void sapiIp(String key, long weight) {
      String id = budget(sapiId(key, "ip"), ScopeKind.EGRESS, 12000, MINUTE);
      fixed(key, EX, true, costs(id, weight));
    }

    private void sapiUid(String key, long weight, boolean replay) {
      String id = budget(sapiId(key, "uid"), ScopeKind.USER, 180000, MINUTE);
      fixed(key, EX, replay, costs(id, weight));
    }

    private static String sapiId(String key, String kind) {
      String path = key.substring(key.indexOf(' ') + 1);
      return "sapi." + path.substring("sapi/v1/".length()).replace('/', '.') + "." + kind;
    }

    // ---- USD-M futures ------------------------------------------------------------------------

    private Map<String, Long> usdm(long weight) {
      return costs(usdmWeight, weight);
    }

    private Map<String, Long> usdmOrder() {
      return costs(usdmOrders10s, 1, usdmOrders1m, 1);
    }

    private void defineUsdm() {
      fixed("GET fapi/v1/exchangeInfo", MD, true, usdm(1));
      rule("GET fapi/v1/depth", MD, true, r -> usdm(usdmDepthWeight(limit(r))));
      rule("GET fapi/v1/ticker/24hr", MD, true, r -> usdm(hasSymbol(r) ? 1 : 40));
      fixed("GET fapi/v1/aggTrades", MD, true, usdm(20));
      rule("GET fapi/v1/premiumIndex", MD, true, r -> usdm(hasSymbol(r) ? 1 : 10));
      fixed("GET fapi/v1/fundingInfo", MD, true, costs(usdmFunding, 1));
      rule("GET fapi/v1/klines", MD, true, r -> usdm(usdmKlinesWeight(limit(r))));
      fixed("GET fapi/v1/fundingRate", MD, true, costs(usdmFunding, 1));

      fixed("GET fapi/v2/account", EX, true, usdm(5));
      fixed("GET fapi/v3/account", EX, true, usdm(5));
      fixed("POST fapi/v1/order", EX, false, usdmOrder());
      fixed("PUT fapi/v1/order", EX, false, usdmOrder());
      fixed("DELETE fapi/v1/order", EX, true, usdm(1));
      fixed("GET fapi/v1/order", EX, true, usdm(1));
      rule("GET fapi/v1/openOrders", EX, true, r -> usdm(hasSymbol(r) ? 1 : 40));
      fixed("GET fapi/v1/userTrades", EX, true, usdm(5));
      fixed("DELETE fapi/v1/allOpenOrders", EX, true, usdm(1));
      fixed("GET fapi/v1/commissionRate", EX, true, usdm(20));
      fixed("GET fapi/v1/allOrders", EX, true, usdm(5));
      fixed("POST fapi/v1/marginType", EX, true, usdm(1));
      fixed("POST fapi/v1/positionSide/dual", EX, true, usdm(1));
      fixed("POST fapi/v1/leverage", EX, true, usdm(1));
      fixed("GET fapi/v2/positionRisk", EX, true, usdm(5));
      fixed("GET fapi/v3/positionRisk", EX, true, usdm(5));
      fixed("POST fapi/v1/listenKey", EX, true, usdm(1));
      fixed("PUT fapi/v1/listenKey", EX, true, usdm(1));
      fixed("DELETE fapi/v1/listenKey", EX, true, usdm(1));
    }

    /** Documented tiers; the default limit of 500 applies when none is sent. */
    private static long usdmDepthWeight(int limit) {
      int effective = limit < 0 ? 500 : limit;
      if (effective <= 50) {
        return 2;
      }
      if (effective <= 100) {
        return 5;
      }
      if (effective <= 500) {
        return 10;
      }
      return 20;
    }

    /** Documented tiers; the default limit of 500 applies when none is sent. */
    private static long usdmKlinesWeight(int limit) {
      int effective = limit < 0 ? 500 : limit;
      if (effective < 100) {
        return 1;
      }
      if (effective < 500) {
        return 2;
      }
      if (effective <= 1000) {
        return 5;
      }
      return 10;
    }

    // ---- COIN-M futures -----------------------------------------------------------------------

    private void defineCoinm() {
      fixed("POST dapi/v1/order", EX, false, costs(coinmOrders1m, 1));
      fixed("DELETE dapi/v1/order", EX, true, costs(coinmWeight, 1));
      rule("GET dapi/v1/openOrders", EX, true, r -> costs(coinmWeight, hasSymbol(r) ? 1 : 40));
      fixed("GET dapi/v1/order", EX, true, costs(coinmWeight, 1));
    }

    // ---- Portfolio Margin ---------------------------------------------------------------------

    private void definePortfolioMargin() {
      for (String market : List.of("um", "cm")) {
        fixed("POST papi/v1/" + market + "/order", EX, false, costs(pmWeight, 1, pmOrders1m, 1));
        fixed("DELETE papi/v1/" + market + "/order", EX, true, costs(pmWeight, 1));
        fixed("GET papi/v1/" + market + "/order", EX, true, costs(pmWeight, 1));
        rule(
            "GET papi/v1/" + market + "/openOrders",
            EX,
            true,
            r -> costs(pmWeight, hasSymbol(r) ? 1 : 40));
      }
    }

    // ---- helpers ------------------------------------------------------------------------------

    private static boolean hasSymbol(RateLimitRequest request) {
      return request.getParameter("symbol") != null;
    }

    /** The {@code limit} query parameter, {@code -1} when absent, {@code MAX_VALUE} if malformed. */
    private static int limit(RateLimitRequest request) {
      String raw = request.getParameter("limit");
      if (raw == null) {
        return -1;
      }
      try {
        return Integer.parseInt(raw.trim());
      } catch (NumberFormatException e) {
        return Integer.MAX_VALUE;
      }
    }

    private static Map<String, Long> costs(Object... budgetIdAndCost) {
      Map<String, Long> costs = new LinkedHashMap<>();
      for (int i = 0; i < budgetIdAndCost.length; i += 2) {
        long cost = ((Number) budgetIdAndCost[i + 1]).longValue();
        if (cost > 0) {
          costs.put((String) budgetIdAndCost[i], cost);
        }
      }
      return costs;
    }
  }
}
