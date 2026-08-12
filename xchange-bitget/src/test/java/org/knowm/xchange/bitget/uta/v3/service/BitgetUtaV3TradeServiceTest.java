package org.knowm.xchange.bitget.uta.v3.service;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.bitget.uta.v3.BitgetUtaV3ExchangeWiremock;
import org.knowm.xchange.bitget.uta.v3.BitgetUtaV3UnknownOutcomeException;
import org.knowm.xchange.bitget.uta.v3.trade.BitgetUtaV3StrategyOrderRequest;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.account.OpenPosition;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.OpenOrders;
import org.knowm.xchange.dto.trade.UserTrades;
import org.knowm.xchange.service.trade.TradeService;
import org.knowm.xchange.service.trade.params.DefaultTradeHistoryParamCurrencyPair;
import org.knowm.xchange.service.trade.params.DefaultTradeHistoryParamInstrument;
import org.knowm.xchange.service.trade.params.orders.DefaultOpenOrdersParamInstrument;
import org.knowm.xchange.service.trade.params.orders.DefaultQueryOrderParam;

class BitgetUtaV3TradeServiceTest extends BitgetUtaV3ExchangeWiremock {

  private final TradeService tradeService = exchange.getTradeService();

  @Test
  void place_limit_order_returns_order_id() throws Exception {
    wireMockServer.stubFor(
        com.github.tomakehurst.wiremock.client.WireMock.post(
                urlPathEqualTo("/api/v3/trade/place-order"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"code\":\"00000\",\"msg\":\"success\",\"requestTime\":1725040472073,"
                            + "\"data\":{\"orderId\":\"123456789\",\"clientOid\":\"my-oid\"}}")));

    LimitOrder order =
        new LimitOrder.Builder(Order.OrderType.BID, CurrencyPair.BTC_USDT)
            .originalAmount(new BigDecimal("0.1"))
            .limitPrice(new BigDecimal("60000"))
            .build();

    String orderId = tradeService.placeLimitOrder(order);

    assertThat(orderId).isEqualTo("123456789");
  }

  @Test
  void ambiguous_placement_surfaces_unknown_outcome_no_replay() throws Exception {
    wireMockServer.stubFor(
        com.github.tomakehurst.wiremock.client.WireMock.post(
                urlPathEqualTo("/api/v3/trade/place-order"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"code\":\"40725\",\"msg\":\"Order may be placed, please check\","
                            + "\"requestTime\":1725040472073}")));

    LimitOrder order =
        new LimitOrder.Builder(Order.OrderType.BID, CurrencyPair.BTC_USDT)
            .originalAmount(new BigDecimal("0.1"))
            .limitPrice(new BigDecimal("60000"))
            .build();

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> tradeService.placeLimitOrder(order))
        .isInstanceOf(BitgetUtaV3UnknownOutcomeException.class)
        .satisfies(
            t -> {
              BitgetUtaV3UnknownOutcomeException e = (BitgetUtaV3UnknownOutcomeException) t;
              assertThat(e.getProviderCode()).isEqualTo("40725");
            });

    // Zero automatic replay: exactly one placement request hit the wire.
    wireMockServer.verify(
        1,
        com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor(
            urlPathEqualTo("/api/v3/trade/place-order")));
  }

  @Test
  void get_open_orders_maps_orders() throws Exception {
    wireMockServer.stubFor(
        get(urlPathEqualTo("/api/v3/trade/unfilled-orders"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"code\":\"00000\",\"msg\":\"success\",\"requestTime\":1725040472073,"
                            + "\"data\":{\"list\":[{\"orderId\":\"1\",\"clientOid\":\"c1\","
                            + "\"category\":\"spot\",\"symbol\":\"BTCUSDT\",\"orderType\":\"limit\","
                            + "\"side\":\"buy\",\"price\":\"60000\",\"qty\":\"0.1\","
                            + "\"cumExecQty\":\"0.05\",\"avgPrice\":\"59990\","
                            + "\"orderStatus\":\"partially_filled\",\"createdTime\":\"1725040472073\"}],"
                            + "\"cursor\":\"\"}}")));

    OpenOrders openOrders = tradeService.getOpenOrders(new DefaultOpenOrdersParamInstrument());

    assertThat(openOrders.getOpenOrders()).hasSize(1);
    LimitOrder order = openOrders.getOpenOrders().get(0);
    assertThat(order.getId()).isEqualTo("1");
    assertThat(order.getInstrument()).isEqualTo(CurrencyPair.BTC_USDT);
    assertThat(order.getLimitPrice()).isEqualByComparingTo("60000");
    assertThat(order.getOriginalAmount()).isEqualByComparingTo("0.1");
    assertThat(order.getCumulativeAmount()).isEqualByComparingTo("0.05");
    assertThat(order.getAveragePrice()).isEqualByComparingTo("59990");
    assertThat(order.getStatus()).isEqualTo(Order.OrderStatus.PARTIALLY_FILLED);
  }

  @Test
  void get_order_returns_single_order() throws Exception {
    wireMockServer.stubFor(
        get(urlPathEqualTo("/api/v3/trade/order-info"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"code\":\"00000\",\"msg\":\"success\",\"requestTime\":1725040472073,"
                            + "\"data\":{\"orderId\":\"42\",\"clientOid\":\"c42\","
                            + "\"category\":\"spot\",\"symbol\":\"BTCUSDT\",\"orderType\":\"market\","
                            + "\"side\":\"sell\",\"qty\":\"0.2\",\"cumExecQty\":\"0.2\","
                            + "\"avgPrice\":\"59900\",\"orderStatus\":\"filled\","
                            + "\"createdTime\":\"1725040472073\"}}")));

    Collection<Order> orders = tradeService.getOrder(new DefaultQueryOrderParam("42"));

    assertThat(orders).hasSize(1);
    Order order = orders.iterator().next();
    assertThat(order.getId()).isEqualTo("42");
    assertThat(order.getInstrument()).isEqualTo(CurrencyPair.BTC_USDT);
    assertThat(order.getStatus()).isEqualTo(Order.OrderStatus.FILLED);
  }

  /**
   * The provider returns fills one cursor page at a time. A single uncursored call must not be
   * taken as the whole history: PRD CF-451 requires that partial pages are never silently dropped
   * (the README states repeated cursors are protected). This test proves page 2 is fetched and
   * merged.
   */
  @Test
  void trade_history_follows_cursor_pages() throws Exception {
    // page 2 must win over any previously registered generic fills stub
    wireMockServer.stubFor(
        get(urlPathEqualTo("/api/v3/trade/fills"))
            .atPriority(1)
            .withQueryParam("cursor", com.github.tomakehurst.wiremock.client.WireMock.equalTo("C1"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"code\":\"00000\",\"msg\":\"success\",\"requestTime\":1725040472073,"
                            + "\"data\":{\"list\":[{\"execId\":\"e3\",\"orderId\":\"43\","
                            + "\"clientOid\":\"c43\",\"category\":\"spot\",\"symbol\":\"BTCUSDT\","
                            + "\"orderType\":\"limit\",\"side\":\"buy\",\"execPrice\":\"59950\","
                            + "\"execQty\":\"0.2\",\"createdTime\":\"1725040471073\"}],"
                            + "\"cursor\":\"\"}}")));
    wireMockServer.stubFor(
        get(urlPathEqualTo("/api/v3/trade/fills"))
            .atPriority(1)
            .withQueryParam("cursor", com.github.tomakehurst.wiremock.client.WireMock.absent())
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"code\":\"00000\",\"msg\":\"success\",\"requestTime\":1725040472073,"
                            + "\"data\":{\"list\":[{\"execId\":\"e1\",\"orderId\":\"42\","
                            + "\"clientOid\":\"c42\",\"category\":\"spot\",\"symbol\":\"BTCUSDT\","
                            + "\"orderType\":\"limit\",\"side\":\"buy\",\"execPrice\":\"60000\","
                            + "\"execQty\":\"0.1\",\"createdTime\":\"1725040472073\"},"
                            + "{\"execId\":\"e2\",\"orderId\":\"42\",\"clientOid\":\"c42\","
                            + "\"category\":\"spot\",\"symbol\":\"BTCUSDT\","
                            + "\"orderType\":\"limit\",\"side\":\"buy\",\"execPrice\":\"59990\","
                            + "\"execQty\":\"0.1\",\"createdTime\":\"1725040471573\"}],"
                            + "\"cursor\":\"C1\"}}")));

    UserTrades trades = tradeService.getTradeHistory(tradeService.createTradeHistoryParams());

    assertThat(trades.getUserTrades()).hasSize(3);
    assertThat(trades.getUserTrades().get(2).getId()).isEqualTo("e3");
    assertThat(trades.getUserTrades().get(0).getOrderUserReference())
        .as("fill clientOid must survive into orderUserReference")
        .isEqualTo("c42");
    assertThat(trades.getUserTrades().get(2).getOrderUserReference()).isEqualTo("c43");
  }

  /**
   * The fills endpoint filters by category only (no symbol parameter), so a request scoped to one
   * instrument must not return fills of other instruments in the same category. PRD CF-451 requires
   * that trade-history accounting data matches the requested symbol.
   */
  @Test
  void trade_history_filters_fills_by_requested_instrument() throws Exception {
    wireMockServer.stubFor(
        get(urlPathEqualTo("/api/v3/trade/fills"))
            .withQueryParam("category", equalTo("spot"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"code\":\"00000\",\"msg\":\"success\",\"requestTime\":1725040472073,"
                            + "\"data\":{\"list\":[{\"execId\":\"e1\",\"orderId\":\"42\","
                            + "\"clientOid\":\"c42\",\"category\":\"spot\",\"symbol\":\"BTCUSDT\","
                            + "\"orderType\":\"limit\",\"side\":\"buy\",\"execPrice\":\"60000\","
                            + "\"execQty\":\"0.1\",\"createdTime\":\"1725040472073\"},"
                            + "{\"execId\":\"e2\",\"orderId\":\"77\",\"clientOid\":\"c77\","
                            + "\"category\":\"spot\",\"symbol\":\"ETHUSDT\","
                            + "\"orderType\":\"limit\",\"side\":\"buy\",\"execPrice\":\"3500\","
                            + "\"execQty\":\"1\",\"createdTime\":\"1725040472073\"}],"
                            + "\"cursor\":\"\"}}")));
    stubEmptyMarginFills();

    BitgetUtaV3TradeService.BitgetUtaV3TradeHistoryParams params =
        (BitgetUtaV3TradeService.BitgetUtaV3TradeHistoryParams)
            tradeService.createTradeHistoryParams();
    params.setInstrument(CurrencyPair.BTC_USDT);

    UserTrades trades = tradeService.getTradeHistory(params);

    assertThat(trades.getUserTrades()).hasSize(1);
    assertThat(trades.getUserTrades().get(0).getId()).isEqualTo("e1");
    assertThat(trades.getUserTrades().get(0).getInstrument())
        .isEqualTo(CurrencyPair.BTC_USDT);
    // both the spot and the margin leg of the shared CurrencyPair identity must be queried
    wireMockServer.verify(
        2,
        com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor(
            urlPathEqualTo("/api/v3/trade/fills")));
  }

  /**
   * Spot and margin fills share the {@link CurrencyPair} identity (margin placements use the same
   * pair, and streaming accepts both categories), so an instrument-scoped history must query both
   * categories; querying only spot would silently drop every margin execution (review wave 15e).
   */
  @Test
  void trade_history_includes_margin_fills_for_spot_instrument() throws Exception {
    // margin fills resolve back to instruments through the catalog: category=margin must be stubbed
    wireMockServer.stubFor(
        get(urlPathEqualTo("/api/v3/market/instruments"))
            .withQueryParam("category", equalTo("margin"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"code\":\"00000\",\"msg\":\"success\",\"requestTime\":1725040472073,"
                            + "\"data\":[{\"symbol\":\"BTCUSDT\",\"baseCoin\":\"BTC\","
                            + "\"quoteCoin\":\"USDT\",\"minTradeNum\":\"0.0001\","
                            + "\"maxTradeNum\":\"10\",\"pricePrecision\":\"2\","
                            + "\"quantityPrecision\":\"4\",\"status\":\"online\","
                            + "\"isReality\":\"no\",\"category\":\"margin\"}]}")));
    wireMockServer.stubFor(
        get(urlPathEqualTo("/api/v3/trade/fills"))
            .withQueryParam("category", equalTo("spot"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"code\":\"00000\",\"msg\":\"success\",\"requestTime\":1725040472073,"
                            + "\"data\":{\"list\":[{\"execId\":\"e-spot\",\"orderId\":\"42\","
                            + "\"clientOid\":\"c42\",\"category\":\"spot\",\"symbol\":\"BTCUSDT\","
                            + "\"orderType\":\"limit\",\"side\":\"buy\",\"execPrice\":\"60000\","
                            + "\"execQty\":\"0.1\",\"createdTime\":\"1725040472070\"}],"
                            + "\"cursor\":\"\"}}")));
    wireMockServer.stubFor(
        get(urlPathEqualTo("/api/v3/trade/fills"))
            .withQueryParam("category", equalTo("margin"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"code\":\"00000\",\"msg\":\"success\",\"requestTime\":1725040472073,"
                            + "\"data\":{\"list\":[{\"execId\":\"e-margin\",\"orderId\":\"43\","
                            + "\"clientOid\":\"c43\",\"category\":\"margin\",\"symbol\":\"BTCUSDT\","
                            + "\"orderType\":\"limit\",\"side\":\"buy\",\"execPrice\":\"60000\","
                            + "\"execQty\":\"0.1\",\"createdTime\":\"1725040472073\"}],"
                            + "\"cursor\":\"\"}}")));

    BitgetUtaV3TradeService.BitgetUtaV3TradeHistoryParams params =
        (BitgetUtaV3TradeService.BitgetUtaV3TradeHistoryParams)
            tradeService.createTradeHistoryParams();
    params.setInstrument(CurrencyPair.BTC_USDT);
    params.setLimit(1);

    UserTrades trades = tradeService.getTradeHistory(params);

    // the limit is honored across categories by fill time: the newer margin fill wins
    assertThat(trades.getUserTrades()).hasSize(1);
    assertThat(trades.getUserTrades().get(0).getId()).isEqualTo("e-margin");
  }

  /**
   * The per-category merge must be sorted even when no limit (or a limit at least the aggregate
   * size) is requested: without a stable cross-category sort, an older spot execution can precede
   * a newer margin execution on the shared {@link CurrencyPair} identity (review wave 15k).
   */
  @Test
  void trade_history_merge_sorts_spot_and_margin_fills_without_limit() throws Exception {
    // margin fills resolve back to instruments through the catalog: category=margin must be stubbed
    wireMockServer.stubFor(
        get(urlPathEqualTo("/api/v3/market/instruments"))
            .withQueryParam("category", equalTo("margin"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"code\":\"00000\",\"msg\":\"success\",\"requestTime\":1725040472073,"
                            + "\"data\":[{\"symbol\":\"BTCUSDT\",\"baseCoin\":\"BTC\","
                            + "\"quoteCoin\":\"USDT\",\"minTradeNum\":\"0.0001\","
                            + "\"maxTradeNum\":\"10\",\"pricePrecision\":\"2\","
                            + "\"quantityPrecision\":\"4\",\"status\":\"online\","
                            + "\"isReality\":\"no\",\"category\":\"margin\"}]}")));
    wireMockServer.stubFor(
        get(urlPathEqualTo("/api/v3/trade/fills"))
            .withQueryParam("category", equalTo("spot"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"code\":\"00000\",\"msg\":\"success\",\"requestTime\":1725040472073,"
                            + "\"data\":{\"list\":[{\"execId\":\"e-spot\",\"orderId\":\"42\","
                            + "\"clientOid\":\"c42\",\"category\":\"spot\",\"symbol\":\"BTCUSDT\","
                            + "\"orderType\":\"limit\",\"side\":\"buy\",\"execPrice\":\"60000\","
                            + "\"execQty\":\"0.1\",\"createdTime\":\"1725040472070\"}],"
                            + "\"cursor\":\"\"}}")));
    wireMockServer.stubFor(
        get(urlPathEqualTo("/api/v3/trade/fills"))
            .withQueryParam("category", equalTo("margin"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"code\":\"00000\",\"msg\":\"success\",\"requestTime\":1725040472073,"
                            + "\"data\":{\"list\":[{\"execId\":\"e-margin\",\"orderId\":\"43\","
                            + "\"clientOid\":\"c43\",\"category\":\"margin\",\"symbol\":\"BTCUSDT\","
                            + "\"orderType\":\"limit\",\"side\":\"buy\",\"execPrice\":\"60000\","
                            + "\"execQty\":\"0.1\",\"createdTime\":\"1725040472073\"}],"
                            + "\"cursor\":\"\"}}")));

    BitgetUtaV3TradeService.BitgetUtaV3TradeHistoryParams params =
        (BitgetUtaV3TradeService.BitgetUtaV3TradeHistoryParams)
            tradeService.createTradeHistoryParams();
    params.setInstrument(CurrencyPair.BTC_USDT);
    params.setLimit(null);

    UserTrades trades = tradeService.getTradeHistory(params);

    // newest first across categories even without a limit: the newer margin fill precedes the
    // older spot fill
    assertThat(trades.getUserTrades()).hasSize(2);
    assertThat(trades.getUserTrades().get(0).getId()).isEqualTo("e-margin");
    assertThat(trades.getUserTrades().get(1).getId()).isEqualTo("e-spot");
  }

  /**
   * When the requested limit exceeds the provider page size, the aggregate must be trimmed to the
   * limit instead of returning the overshoot from the final page, so callers can rely on {@code
   * TradeHistoryParamLimit}.
   */
  @Test
  void trade_history_truncates_to_requested_limit() throws Exception {
    StringBuilder page1 = new StringBuilder();
    for (int i = 1; i <= 100; i++) {
      page1
          .append("{\"execId\":\"e")
          .append(i)
          .append("\",\"orderId\":\"")
          .append(i)
          .append("\",\"category\":\"spot\",\"symbol\":\"BTCUSDT\",")
          .append("\"orderType\":\"limit\",\"side\":\"buy\",\"execPrice\":\"60000\",")
          .append("\"execQty\":\"0.1\",\"createdTime\":\"1725040472073\"},");
    }
    page1.setLength(page1.length() - 1); // drop trailing comma
    StringBuilder page2 = new StringBuilder();
    for (int i = 101; i <= 200; i++) {
      page2
          .append("{\"execId\":\"e")
          .append(i)
          .append("\",\"orderId\":\"")
          .append(i)
          .append("\",\"category\":\"spot\",\"symbol\":\"BTCUSDT\",")
          .append("\"orderType\":\"limit\",\"side\":\"buy\",\"execPrice\":\"60000\",")
          .append("\"execQty\":\"0.1\",\"createdTime\":\"1725040472073\"},");
    }
    page2.setLength(page2.length() - 1);
    // newest page first: the cursor-absent call returns page 1 and hands back cursor C1,
    // exactly like the provider (see trade_history_follows_cursor_pages)
    wireMockServer.stubFor(
        get(urlPathEqualTo("/api/v3/trade/fills"))
            .atPriority(1)
            .withQueryParam("cursor", com.github.tomakehurst.wiremock.client.WireMock.absent())
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"code\":\"00000\",\"msg\":\"success\",\"requestTime\":1725040472073,"
                            + "\"data\":{\"list\":["
                            + page1
                            + "],\"cursor\":\"C1\"}}")));
    wireMockServer.stubFor(
        get(urlPathEqualTo("/api/v3/trade/fills"))
            .atPriority(1)
            .withQueryParam("cursor", com.github.tomakehurst.wiremock.client.WireMock.equalTo("C1"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"code\":\"00000\",\"msg\":\"success\",\"requestTime\":1725040472073,"
                            + "\"data\":{\"list\":["
                            + page2
                            + "],\"cursor\":\"\"}}")));

    BitgetUtaV3TradeService.BitgetUtaV3TradeHistoryParams params =
        (BitgetUtaV3TradeService.BitgetUtaV3TradeHistoryParams)
            tradeService.createTradeHistoryParams();
    params.setLimit(150);

    UserTrades trades = tradeService.getTradeHistory(params);

    assertThat(trades.getUserTrades()).hasSize(150);
    assertThat(trades.getUserTrades().get(0).getId()).isEqualTo("e1");
    assertThat(
            trades.getUserTrades().stream()
                .map(t -> t.getId())
                .collect(java.util.stream.Collectors.toList()))
        .isEqualTo(
            java.util.stream.IntStream.rangeClosed(1, 150)
                .mapToObj(i -> "e" + i)
                .collect(java.util.stream.Collectors.toList()));
  }

  /**
   * The symbol filter must be applied during pagination, not after it: {@code
   * TradeHistoryParamLimit} counts rows that survive the instrument filter. A page full of other
   * symbols must not satisfy the limit early — the loop keeps paging until enough matching rows
   * arrive, then trims the final page's overshoot.
   */
  @Test
  void trade_history_limit_counts_only_matching_symbols() throws Exception {
    StringBuilder page1 = new StringBuilder();
    for (int i = 1; i <= 5; i++) {
      page1
          .append("{\"execId\":\"e")
          .append(i)
          .append("\",\"orderId\":\"")
          .append(i)
          .append("\",\"category\":\"spot\",\"symbol\":\"BTCUSDT\",")
          .append("\"orderType\":\"limit\",\"side\":\"buy\",\"execPrice\":\"60000\",")
          .append("\"execQty\":\"0.1\",\"createdTime\":\"1725040472073\"},");
    }
    for (int i = 6; i <= 100; i++) {
      page1
          .append("{\"execId\":\"e")
          .append(i)
          .append("\",\"orderId\":\"")
          .append(i)
          .append("\",\"category\":\"spot\",\"symbol\":\"ETHUSDT\",")
          .append("\"orderType\":\"limit\",\"side\":\"buy\",\"execPrice\":\"3500\",")
          .append("\"execQty\":\"1\",\"createdTime\":\"1725040472073\"},");
    }
    page1.setLength(page1.length() - 1);
    StringBuilder page2 = new StringBuilder();
    for (int i = 101; i <= 160; i++) {
      page2
          .append("{\"execId\":\"e")
          .append(i)
          .append("\",\"orderId\":\"")
          .append(i)
          .append("\",\"category\":\"spot\",\"symbol\":\"BTCUSDT\",")
          .append("\"orderType\":\"limit\",\"side\":\"buy\",\"execPrice\":\"60000\",")
          .append("\"execQty\":\"0.1\",\"createdTime\":\"1725040472073\"},");
    }
    page2.setLength(page2.length() - 1);
    // page 1: only 5 of 100 fills match BTCUSDT; page 2: 60 of 60 match
    wireMockServer.stubFor(
        get(urlPathEqualTo("/api/v3/trade/fills"))
            .atPriority(1)
            .withQueryParam("category", equalTo("spot"))
            .withQueryParam("cursor", com.github.tomakehurst.wiremock.client.WireMock.absent())
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"code\":\"00000\",\"msg\":\"success\",\"requestTime\":1725040472073,"
                            + "\"data\":{\"list\":["
                            + page1
                            + "],\"cursor\":\"C1\"}}")));
    wireMockServer.stubFor(
        get(urlPathEqualTo("/api/v3/trade/fills"))
            .atPriority(1)
            .withQueryParam("category", equalTo("spot"))
            .withQueryParam("cursor", com.github.tomakehurst.wiremock.client.WireMock.equalTo("C1"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"code\":\"00000\",\"msg\":\"success\",\"requestTime\":1725040472073,"
                            + "\"data\":{\"list\":["
                            + page2
                            + "],\"cursor\":\"\"}}")));
    stubEmptyMarginFills();

    BitgetUtaV3TradeService.BitgetUtaV3TradeHistoryParams params =
        (BitgetUtaV3TradeService.BitgetUtaV3TradeHistoryParams)
            tradeService.createTradeHistoryParams();
    params.setInstrument(CurrencyPair.BTC_USDT);
    params.setLimit(50);

    UserTrades trades = tradeService.getTradeHistory(params);

    // 5 matching rows on page 1 are not enough for the limit: the loop must fetch page 2 and
    // take 45 of its 60 matching rows, then trim to exactly 50 (ids e1..e5 + e101..e145,
    // re-sorted ascending by SortByID)
    assertThat(trades.getUserTrades()).hasSize(50);
    assertThat(trades.getUserTrades())
        .allSatisfy(t -> assertThat(t.getInstrument()).isEqualTo(CurrencyPair.BTC_USDT));
    assertThat(
            trades.getUserTrades().stream()
                .map(t -> t.getId())
                .collect(java.util.stream.Collectors.toList()))
        .isEqualTo(
            java.util.stream.Stream.concat(
                    java.util.stream.IntStream.rangeClosed(1, 5).mapToObj(i -> "e" + i),
                    java.util.stream.IntStream.rangeClosed(101, 145).mapToObj(i -> "e" + i))
                .collect(java.util.stream.Collectors.toList()));
    // two spot pages plus the single empty margin leg
    wireMockServer.verify(
        3,
        com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor(
            urlPathEqualTo("/api/v3/trade/fills")));
  }

  /**
   * A provider that repeats the cursor must be rejected even when a row limit is set: the limit
   * check must never run on an un-advanced page, otherwise the duplicated rows would be returned
   * as if they were fresh history.
   */
  @Test
  void trade_history_repeated_cursor_not_satisfied_by_duplicate_page() throws Exception {
    // every fills request returns the same single-row page and the same cursor
    wireMockServer.stubFor(
        get(urlPathEqualTo("/api/v3/trade/fills"))
            .atPriority(1)
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"code\":\"00000\",\"msg\":\"success\",\"requestTime\":1725040472073,"
                            + "\"data\":{\"list\":[{\"execId\":\"e1\",\"orderId\":\"42\","
                            + "\"clientOid\":\"c42\",\"category\":\"spot\",\"symbol\":\"BTCUSDT\","
                            + "\"orderType\":\"limit\",\"side\":\"buy\",\"execPrice\":\"60000\","
                            + "\"execQty\":\"0.1\",\"createdTime\":\"1725040472073\"}],"
                            + "\"cursor\":\"STUCK\"}}")));

    BitgetUtaV3TradeService.BitgetUtaV3TradeHistoryParams params =
        (BitgetUtaV3TradeService.BitgetUtaV3TradeHistoryParams)
            tradeService.createTradeHistoryParams();
    params.setLimit(2);

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> tradeService.getTradeHistory(params))
        .isInstanceOf(org.knowm.xchange.exceptions.ExchangeException.class);
  }

  /**
   * A transport failure on placement (read timeout, connection reset) must not leak as a plain
   * {@code IOException}: the provider may still have accepted the order, so the outcome is
   * unknown and callers must not replay blindly — same contract as the ambiguous provider codes.
   */
  @Test
  void transport_failure_on_placement_surfaces_unknown_outcome() throws Exception {
    wireMockServer.stubFor(
        com.github.tomakehurst.wiremock.client.WireMock.post(
                urlPathEqualTo("/api/v3/trade/place-order"))
            .willReturn(
                aResponse()
                    .withFault(com.github.tomakehurst.wiremock.http.Fault.CONNECTION_RESET_BY_PEER)));

    LimitOrder order =
        new LimitOrder.Builder(Order.OrderType.BID, CurrencyPair.BTC_USDT)
            .originalAmount(new BigDecimal("0.1"))
            .limitPrice(new BigDecimal("60000"))
            .build();

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> tradeService.placeLimitOrder(order))
        .isInstanceOf(BitgetUtaV3UnknownOutcomeException.class)
        .satisfies(
            t -> {
              BitgetUtaV3UnknownOutcomeException e = (BitgetUtaV3UnknownOutcomeException) t;
              assertThat(e.getProviderCode()).isNull();
              assertThat(e.getCause()).isInstanceOf(java.io.IOException.class);
            });

    // No application-level replay: the transport layer may retry a reset connection, but the
    // service must surface the unknown outcome instead of silently resending the placement.
    wireMockServer.verify(
        com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor(
            urlPathEqualTo("/api/v3/trade/place-order")));
  }

  @Test
  void placement_without_user_reference_sends_generated_client_oid() throws Exception {
    wireMockServer.stubFor(
        com.github.tomakehurst.wiremock.client.WireMock.post(
                urlPathEqualTo("/api/v3/trade/place-order"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"code\":\"00000\",\"msg\":\"success\",\"requestTime\":1725040472073,"
                            + "\"data\":{\"orderId\":\"123456789\",\"clientOid\":\"gen-oid\"}}")));

    LimitOrder order =
        new LimitOrder.Builder(Order.OrderType.BID, CurrencyPair.BTC_USDT)
            .originalAmount(new BigDecimal("0.1"))
            .limitPrice(new BigDecimal("60000"))
            .build();

    tradeService.placeLimitOrder(order);

    // an idempotency key must be generated and transmitted when the caller set no userReference
    // (hyphen-stripped UUID: 32 chars, satisfies the ^[\.A-Z\:/a-z0-9_-]{1,32}$ wire constraint)
    wireMockServer.verify(
        com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor(
                urlPathEqualTo("/api/v3/trade/place-order"))
            .withRequestBody(
                com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath(
                    "$.clientOid",
                    com.github.tomakehurst.wiremock.client.WireMock.matching("^[0-9a-f]{32}$"))));
  }

  @Test
  void placement_keeps_caller_supplied_user_reference_as_client_oid() throws Exception {
    wireMockServer.stubFor(
        com.github.tomakehurst.wiremock.client.WireMock.post(
                urlPathEqualTo("/api/v3/trade/place-order"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"code\":\"00000\",\"msg\":\"success\",\"requestTime\":1725040472073,"
                            + "\"data\":{\"orderId\":\"123456789\",\"clientOid\":\"my-oid\"}}")));

    LimitOrder order =
        new LimitOrder.Builder(Order.OrderType.BID, CurrencyPair.BTC_USDT)
            .originalAmount(new BigDecimal("0.1"))
            .limitPrice(new BigDecimal("60000"))
            .userReference("caller-oid-123")
            .build();

    tradeService.placeLimitOrder(order);

    // a caller-supplied userReference is the idempotency key and must pass through untouched
    wireMockServer.verify(
        com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor(
                urlPathEqualTo("/api/v3/trade/place-order"))
            .withRequestBody(
                com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath(
                    "$.clientOid",
                    com.github.tomakehurst.wiremock.client.WireMock.equalTo("caller-oid-123"))));
  }

  @Test
  void unknown_outcome_carries_generated_client_oid_that_reached_the_wire() throws Exception {
    wireMockServer.stubFor(
        com.github.tomakehurst.wiremock.client.WireMock.post(
                urlPathEqualTo("/api/v3/trade/place-order"))
            .willReturn(
                aResponse()
                    .withFault(com.github.tomakehurst.wiremock.http.Fault.CONNECTION_RESET_BY_PEER)));

    LimitOrder order =
        new LimitOrder.Builder(Order.OrderType.BID, CurrencyPair.BTC_USDT)
            .originalAmount(new BigDecimal("0.1"))
            .limitPrice(new BigDecimal("60000"))
            .build();

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> tradeService.placeLimitOrder(order))
        .isInstanceOf(BitgetUtaV3UnknownOutcomeException.class)
        .satisfies(
            t -> {
              BitgetUtaV3UnknownOutcomeException e = (BitgetUtaV3UnknownOutcomeException) t;
              // the surfaced clientOid is the generated key that actually reached the wire, so
              // callers can reconcile through order-info instead of replaying blindly
              assertThat(e.getClientOid()).isNotNull().matches("^[0-9a-f]{32}$");
              List<com.github.tomakehurst.wiremock.verification.LoggedRequest> requests =
                  wireMockServer.findAll(
                      com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor(
                          urlPathEqualTo("/api/v3/trade/place-order")));
              // the transport layer may retry a reset connection; every retry must resend the
              // same idempotency key, and the surfaced exception must carry exactly that key
              assertThat(requests).isNotEmpty();
              com.fasterxml.jackson.databind.ObjectMapper mapper =
                  new com.fasterxml.jackson.databind.ObjectMapper();
              for (com.github.tomakehurst.wiremock.verification.LoggedRequest request : requests) {
                String wireClientOid =
                    mapper.readTree(request.getBodyAsString()).get("clientOid").asText();
                assertThat(wireClientOid).isEqualTo(e.getClientOid());
              }
            });
  }

  /**
   * Same contract for open orders: unfilled-orders paginates by cursor, so a single 100-row page
   * silently drops older open orders. All pages must be aggregated.
   */
  @Test
  void open_orders_follow_cursor_pages() throws Exception {
    wireMockServer.stubFor(
        get(urlPathEqualTo("/api/v3/trade/unfilled-orders"))
            .atPriority(1)
            .withQueryParam("cursor", com.github.tomakehurst.wiremock.client.WireMock.equalTo("o1"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"code\":\"00000\",\"msg\":\"success\",\"requestTime\":1725040472073,"
                            + "\"data\":{\"list\":[{\"orderId\":\"2\",\"clientOid\":\"c2\","
                            + "\"category\":\"spot\",\"symbol\":\"BTCUSDT\",\"orderType\":\"limit\","
                            + "\"side\":\"sell\",\"price\":\"60100\",\"qty\":\"0.2\","
                            + "\"orderStatus\":\"new\",\"createdTime\":\"1725040471073\"}],"
                            + "\"cursor\":\"\"}}")));
    wireMockServer.stubFor(
        get(urlPathEqualTo("/api/v3/trade/unfilled-orders"))
            .atPriority(1)
            .withQueryParam("cursor", com.github.tomakehurst.wiremock.client.WireMock.absent())
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"code\":\"00000\",\"msg\":\"success\",\"requestTime\":1725040472073,"
                            + "\"data\":{\"list\":[{\"orderId\":\"1\",\"clientOid\":\"c1\","
                            + "\"category\":\"spot\",\"symbol\":\"BTCUSDT\",\"orderType\":\"limit\","
                            + "\"side\":\"buy\",\"price\":\"60000\",\"qty\":\"0.1\","
                            + "\"orderStatus\":\"new\",\"createdTime\":\"1725040472073\"}],"
                            + "\"cursor\":\"o1\"}}")));

    OpenOrders openOrders = tradeService.getOpenOrders(new DefaultOpenOrdersParamInstrument());

    assertThat(openOrders.getOpenOrders()).hasSize(2);
  }

  /**
   * The no-argument {@code getOpenOrders()} entry point must work for generic XChange clients:
   * the parameterized overload is the implementation, the no-arg form delegates to it with the
   * default params instead of inheriting the interface's not-implemented default.
   */
  @Test
  void no_arg_get_open_orders_delegates_to_parameterized_overload() throws Exception {
    wireMockServer.stubFor(
        get(urlPathEqualTo("/api/v3/trade/unfilled-orders"))
            .atPriority(1)
            .withQueryParam("cursor", com.github.tomakehurst.wiremock.client.WireMock.absent())
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"code\":\"00000\",\"msg\":\"success\",\"requestTime\":1725040472073,"
                            + "\"data\":{\"list\":[{\"orderId\":\"1\",\"clientOid\":\"c1\","
                            + "\"category\":\"spot\",\"symbol\":\"BTCUSDT\",\"orderType\":\"limit\","
                            + "\"side\":\"buy\",\"price\":\"60000\",\"qty\":\"0.1\","
                            + "\"orderStatus\":\"new\",\"createdTime\":\"1725040472073\"}],"
                            + "\"cursor\":\"\"}}")));

    OpenOrders openOrders = tradeService.getOpenOrders();

    assertThat(openOrders.getOpenOrders()).hasSize(1);
    assertThat(openOrders.getOpenOrders().get(0).getId()).isEqualTo("1");
  }

  /**
   * A provider that repeats the same cursor (or echoes a stale one) must not be followed forever:
   * PRD CF-451 requires repeated/no-progress detection. A stuck cursor must surface an exception
   * instead of returning silently-truncated history.
   */
  @Test
  void trade_history_stops_on_repeated_cursor() throws Exception {
    wireMockServer.stubFor(
        get(urlPathEqualTo("/api/v3/trade/fills"))
            .atPriority(1)
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"code\":\"00000\",\"msg\":\"success\",\"requestTime\":1725040472073,"
                            + "\"data\":{\"list\":[{\"execId\":\"e1\",\"orderId\":\"42\","
                            + "\"clientOid\":\"c42\",\"category\":\"spot\",\"symbol\":\"BTCUSDT\","
                            + "\"orderType\":\"limit\",\"side\":\"buy\",\"execPrice\":\"60000\","
                            + "\"execQty\":\"0.1\",\"createdTime\":\"1725040472073\"}],"
                            + "\"cursor\":\"STUCK\"}}")));

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> tradeService.getTradeHistory(tradeService.createTradeHistoryParams()))
        .isInstanceOf(org.knowm.xchange.exceptions.ExchangeException.class);
  }

  @Test
  void trade_history_maps_fills() throws Exception {
    wireMockServer.stubFor(
        get(urlPathEqualTo("/api/v3/trade/fills"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"code\":\"00000\",\"msg\":\"success\",\"requestTime\":1725040472073,"
                            + "\"data\":{\"list\":[{\"execId\":\"e1\",\"orderId\":\"42\","
                            + "\"clientOid\":\"c42\",\"category\":\"spot\",\"symbol\":\"BTCUSDT\","
                            + "\"orderType\":\"limit\",\"side\":\"buy\",\"execPrice\":\"60000\","
                            + "\"execQty\":\"0.1\",\"execValue\":\"6000\",\"tradeScope\":\"taker\","
                            + "\"tradeSide\":\"open\",\"feeDetail\":[{\"feeCoin\":\"USDT\","
                            + "\"fee\":\"6\"}],\"createdTime\":\"1725040472073\"}],\"cursor\":\"\"}}")));

    UserTrades trades = tradeService.getTradeHistory(tradeService.createTradeHistoryParams());

    assertThat(trades.getUserTrades()).hasSize(1);
    org.knowm.xchange.dto.trade.UserTrade trade = trades.getUserTrades().get(0);
    assertThat(trade.getId()).isEqualTo("e1");
    assertThat(trade.getOrderId()).isEqualTo("42");
    assertThat(trade.getInstrument()).isEqualTo(CurrencyPair.BTC_USDT);
    assertThat(trade.getPrice()).isEqualByComparingTo("60000");
    assertThat(trade.getOriginalAmount()).isEqualByComparingTo("0.1");
    assertThat(trade.getFeeAmount()).isEqualByComparingTo("6");
    assertThat(trade.getFeeCurrency()).isEqualTo(org.knowm.xchange.currency.Currency.USDT);
  }

  @Test
  void trade_history_splits_spans_over_thirty_days() throws Exception {
    // span of ~38.2 days must be split into two ≤30-day windows, newest first
    long start = 1_720_000_000_000L;
    long end = 1_723_300_000_000L;
    long windowMillis = 30L * 24 * 60 * 60 * 1000;
    long windowBoundary = end - windowMillis; // 1_720_708_000_000

    wireMockServer.stubFor(
        get(urlPathEqualTo("/api/v3/trade/fills"))
            .withQueryParam("category", equalTo("spot"))
            .withQueryParam("startTime", equalTo(String.valueOf(windowBoundary)))
            .withQueryParam("endTime", equalTo(String.valueOf(end)))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(fillsPage("e1", "1725040472073", ""))));
    // the older window's end is exclusive of the boundary, so a fill exactly at the boundary is
    // never requested twice
    wireMockServer.stubFor(
        get(urlPathEqualTo("/api/v3/trade/fills"))
            .withQueryParam("category", equalTo("spot"))
            .withQueryParam("startTime", equalTo(String.valueOf(start)))
            .withQueryParam("endTime", equalTo(String.valueOf(windowBoundary - 1)))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(fillsPage("e2", "1725040472073", ""))));
    // an unsplit full-span query must never reach the wire
    wireMockServer.stubFor(
        get(urlPathEqualTo("/api/v3/trade/fills"))
            .withQueryParam("category", equalTo("spot"))
            .withQueryParam("startTime", equalTo(String.valueOf(start)))
            .withQueryParam("endTime", equalTo(String.valueOf(end)))
            .willReturn(aResponse().withStatus(500)));
    stubEmptyMarginFills();

    BitgetUtaV3TradeService.BitgetUtaV3TradeHistoryParams params =
        (BitgetUtaV3TradeService.BitgetUtaV3TradeHistoryParams)
            tradeService.createTradeHistoryParams();
    params.setInstrument(CurrencyPair.BTC_USDT);
    params.setStartTime(new java.util.Date(start));
    params.setEndTime(new java.util.Date(end));

    UserTrades trades = tradeService.getTradeHistory(params);

    assertThat(trades.getUserTrades()).hasSize(2);
    assertThat(trades.getUserTrades().get(0).getId())
        .as("newest window must be fetched first")
        .isEqualTo("e1");
    assertThat(trades.getUserTrades().get(1).getId()).isEqualTo("e2");
  }

  @Test
  void trade_history_splits_an_old_start_without_an_end_time() throws Exception {
    // a start older than the 30-day window with NO end time: the provider treats the omitted end
    // as now, so the span must be split into compliant windows rather than sent unbounded (the
    // endpoint rejects ranges over 30 days); the window boundaries depend on the live clock, so
    // the wire contract is asserted structurally
    long now = System.currentTimeMillis();
    long start = now - 75L * 24 * 60 * 60 * 1000; // ~75 days ago, no end time

    // every windowed spot fills query answers one fill
    wireMockServer.stubFor(
        get(urlPathEqualTo("/api/v3/trade/fills"))
            .withQueryParam("category", equalTo("spot"))
            .withQueryParam(
                "endTime", com.github.tomakehurst.wiremock.client.WireMock.matching("[0-9]+"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(fillsPage("e1", "1725040472073", ""))));
    // an unsplit unbounded query (old start, no endTime) must never reach the wire
    wireMockServer.stubFor(
        get(urlPathEqualTo("/api/v3/trade/fills"))
            .withQueryParam("category", equalTo("spot"))
            .withQueryParam("startTime", equalTo(String.valueOf(start)))
            .withQueryParam(
                "endTime", com.github.tomakehurst.wiremock.client.WireMock.absent())
            .willReturn(aResponse().withStatus(500)));
    stubEmptyMarginFills();

    BitgetUtaV3TradeService.BitgetUtaV3TradeHistoryParams params =
        (BitgetUtaV3TradeService.BitgetUtaV3TradeHistoryParams)
            tradeService.createTradeHistoryParams();
    params.setInstrument(CurrencyPair.BTC_USDT);
    params.setStartTime(new java.util.Date(start));
    // no end time: the implicit end is now

    UserTrades trades = tradeService.getTradeHistory(params);

    assertThat(trades.getUserTrades())
        .as("the span must be fetched as three <=30-day windows, one fill per window")
        .hasSize(3);
    wireMockServer.verify(
        3,
        com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor(
            urlPathEqualTo("/api/v3/trade/fills"))
            .withQueryParam("category", equalTo("spot"))
            .withQueryParam(
                "endTime",
                com.github.tomakehurst.wiremock.client.WireMock.matching("[0-9]+")));
  }

  @Test
  void trade_history_respects_limit_across_windows() throws Exception {
    long start = 1_720_000_000_000L;
    long end = 1_723_300_000_000L;
    long windowMillis = 30L * 24 * 60 * 60 * 1000;
    long windowBoundary = end - windowMillis;

    wireMockServer.stubFor(
        get(urlPathEqualTo("/api/v3/trade/fills"))
            .withQueryParam("category", equalTo("spot"))
            .withQueryParam("startTime", equalTo(String.valueOf(windowBoundary)))
            .withQueryParam("endTime", equalTo(String.valueOf(end)))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(fillsPage("e1,e2,e3", "1725040472073", ""))));
    // the older window must not be fetched once the limit is satisfied
    wireMockServer.stubFor(
        get(urlPathEqualTo("/api/v3/trade/fills"))
            .withQueryParam("category", equalTo("spot"))
            .withQueryParam("startTime", equalTo(String.valueOf(start)))
            .withQueryParam("endTime", equalTo(String.valueOf(windowBoundary - 1)))
            .willReturn(aResponse().withStatus(500)));
    stubEmptyMarginFills();

    BitgetUtaV3TradeService.BitgetUtaV3TradeHistoryParams params =
        (BitgetUtaV3TradeService.BitgetUtaV3TradeHistoryParams)
            tradeService.createTradeHistoryParams();
    params.setInstrument(CurrencyPair.BTC_USDT);
    params.setStartTime(new java.util.Date(start));
    params.setEndTime(new java.util.Date(end));
    params.setLimit(2);

    UserTrades trades = tradeService.getTradeHistory(params);

    assertThat(trades.getUserTrades()).hasSize(2);
    assertThat(trades.getUserTrades().get(0).getId()).isEqualTo("e1");
    assertThat(trades.getUserTrades().get(1).getId()).isEqualTo("e2");
  }

  @Test
  void trade_history_never_duplicates_a_fill_at_the_window_boundary() throws Exception {
    long start = 1_720_000_000_000L;
    long end = 1_723_300_000_000L;
    long windowMillis = 30L * 24 * 60 * 60 * 1000;
    long windowBoundary = end - windowMillis;

    // the newest window keeps the fill whose createdTime sits exactly on the boundary...
    wireMockServer.stubFor(
        get(urlPathEqualTo("/api/v3/trade/fills"))
            .withQueryParam("category", equalTo("spot"))
            .withQueryParam("startTime", equalTo(String.valueOf(windowBoundary)))
            .withQueryParam("endTime", equalTo(String.valueOf(end)))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(fillsPage("e1,e-boundary", String.valueOf(windowBoundary), ""))));
    // ...while the older window requests an end exclusive of the boundary; an overlapping
    // inclusive endTime must never reach the wire
    wireMockServer.stubFor(
        get(urlPathEqualTo("/api/v3/trade/fills"))
            .withQueryParam("category", equalTo("spot"))
            .withQueryParam("startTime", equalTo(String.valueOf(start)))
            .withQueryParam("endTime", equalTo(String.valueOf(windowBoundary - 1)))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(fillsPage("e2", String.valueOf(start), ""))));
    wireMockServer.stubFor(
        get(urlPathEqualTo("/api/v3/trade/fills"))
            .withQueryParam("category", equalTo("spot"))
            .withQueryParam("startTime", equalTo(String.valueOf(start)))
            .withQueryParam("endTime", equalTo(String.valueOf(windowBoundary)))
            .willReturn(aResponse().withStatus(500)));
    stubEmptyMarginFills();

    BitgetUtaV3TradeService.BitgetUtaV3TradeHistoryParams params =
        (BitgetUtaV3TradeService.BitgetUtaV3TradeHistoryParams)
            tradeService.createTradeHistoryParams();
    params.setInstrument(CurrencyPair.BTC_USDT);
    params.setStartTime(new java.util.Date(start));
    params.setEndTime(new java.util.Date(end));

    UserTrades trades = tradeService.getTradeHistory(params);

    assertThat(trades.getUserTrades()).hasSize(3);
    assertThat(trades.getUserTrades())
        .extracting(org.knowm.xchange.dto.trade.UserTrade::getId)
        .containsExactlyInAnyOrder("e1", "e-boundary", "e2");
    assertThat(trades.getUserTrades())
        .extracting(org.knowm.xchange.dto.trade.UserTrade::getId)
        .filteredOn("e-boundary"::equals)
        .as("the boundary fill must be emitted exactly once")
        .hasSize(1);
    assertThat(trades.getUserTrades().get(2).getId())
        .as("the older window must still come last")
        .isEqualTo("e2");
  }

  @Test
  void trade_history_includes_fill_at_start_when_span_leaves_one_millisecond() throws Exception {
    // a span of exactly 30 days plus one millisecond splits into [start+1, end] and a final
    // single-millisecond window [start, start]; the fill exactly at the requested start must be
    // fetched once, not skipped by a strict windowEnd > startMillis bound
    long windowMillis = 30L * 24 * 60 * 60 * 1000;
    long start = 1_720_000_000_000L;
    long end = start + windowMillis + 1;

    wireMockServer.stubFor(
        get(urlPathEqualTo("/api/v3/trade/fills"))
            .withQueryParam("category", equalTo("spot"))
            .withQueryParam("startTime", equalTo(String.valueOf(start + 1)))
            .withQueryParam("endTime", equalTo(String.valueOf(end)))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(fillsPage("e1", String.valueOf(start + 1), ""))));
    wireMockServer.stubFor(
        get(urlPathEqualTo("/api/v3/trade/fills"))
            .withQueryParam("category", equalTo("spot"))
            .withQueryParam("startTime", equalTo(String.valueOf(start)))
            .withQueryParam("endTime", equalTo(String.valueOf(start)))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(fillsPage("e-start", String.valueOf(start), ""))));
    // an unsplit full-span query must never reach the wire
    wireMockServer.stubFor(
        get(urlPathEqualTo("/api/v3/trade/fills"))
            .withQueryParam("category", equalTo("spot"))
            .withQueryParam("startTime", equalTo(String.valueOf(start)))
            .withQueryParam("endTime", equalTo(String.valueOf(end)))
            .willReturn(aResponse().withStatus(500)));
    stubEmptyMarginFills();

    BitgetUtaV3TradeService.BitgetUtaV3TradeHistoryParams params =
        (BitgetUtaV3TradeService.BitgetUtaV3TradeHistoryParams)
            tradeService.createTradeHistoryParams();
    params.setInstrument(CurrencyPair.BTC_USDT);
    params.setStartTime(new java.util.Date(start));
    params.setEndTime(new java.util.Date(end));

    UserTrades trades = tradeService.getTradeHistory(params);

    assertThat(trades.getUserTrades()).hasSize(2);
    assertThat(trades.getUserTrades())
        .extracting(org.knowm.xchange.dto.trade.UserTrade::getId)
        .containsExactlyInAnyOrder("e1", "e-start");
    assertThat(trades.getUserTrades())
        .extracting(org.knowm.xchange.dto.trade.UserTrade::getId)
        .filteredOn("e-start"::equals)
        .as("the fill exactly at the requested start must be fetched exactly once")
        .hasSize(1);
    // the loop must issue the final single-millisecond window [start, start]; with a strict
    // windowEnd > startMillis bound this request never reaches the wire and the fill is dropped
    assertThat(
            wireMockServer
                .findAll(
                    com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor(
                            com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo(
                                "/api/v3/trade/fills"))
                        .withQueryParam("category", equalTo("spot"))
                        .withQueryParam("startTime", equalTo(String.valueOf(start)))
                        .withQueryParam("endTime", equalTo(String.valueOf(start)))))
        .as("the final single-millisecond window [start, start] must be queried")
        .hasSize(1);
  }

  @Test
  void trade_history_with_null_params_does_not_throw() throws Exception {
    wireMockServer.stubFor(
        get(urlPathEqualTo("/api/v3/trade/fills"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"code\":\"00000\",\"msg\":\"success\",\"requestTime\":1725040472073,"
                            + "\"data\":{\"list\":[],\"cursor\":\"\"}}")));

    UserTrades trades = tradeService.getTradeHistory(null);

    assertThat(trades.getUserTrades()).isEmpty();
  }

  @Test
  void trade_history_with_generic_params_does_not_throw() throws Exception {
    wireMockServer.stubFor(
        get(urlPathEqualTo("/api/v3/trade/fills"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"code\":\"00000\",\"msg\":\"success\",\"requestTime\":1725040472073,"
                            + "\"data\":{\"list\":[],\"cursor\":\"\"}}")));

    // generic core params type, not the exchange-specific subclass
    UserTrades trades = tradeService.getTradeHistory(new DefaultTradeHistoryParamCurrencyPair());

    assertThat(trades.getUserTrades()).isEmpty();
  }

  @Test
  void open_positions_maps_position() throws Exception {
    wireMockServer.stubFor(
        get(urlPathEqualTo("/api/v3/position/current-position"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"code\":\"00000\",\"msg\":\"success\",\"requestTime\":1725040472073,"
                            + "\"data\":{\"list\":[{\"category\":\"usdt-futures\","
                            + "\"symbol\":\"BTCUSDT\",\"marginCoin\":\"USDT\","
                            + "\"holdMode\":\"one_way_mode\",\"posSide\":\"long\","
                            + "\"marginMode\":\"crossed\",\"positionBalance\":\"100\","
                            + "\"available\":\"0.5\",\"frozen\":\"0\",\"total\":\"0.5\","
                            + "\"leverage\":\"10\",\"avgPrice\":\"60000\","
                            + "\"positionStatus\":\"normal\",\"unrealisedPnl\":\"100\","
                            + "\"liquidationPrice\":\"45000\",\"createdTime\":\"1725040472073\","
                            + "\"updatedTime\":\"1725040472073\"}],\"cursor\":\"\"}}")));

    List<OpenPosition> positions = tradeService.getOpenPositions().getOpenPositions();

    assertThat(positions).hasSize(1);
    OpenPosition position = positions.get(0);
    assertThat(position.getInstrument())
        .isEqualTo(new org.knowm.xchange.derivative.FuturesContract(CurrencyPair.BTC_USDT, "PERP"));
    assertThat(position.getType()).isEqualTo(OpenPosition.Type.LONG);
    assertThat(position.getMarginMode()).isEqualTo(OpenPosition.MarginMode.CROSS);
    assertThat(position.getSize()).isEqualByComparingTo("0.5");
    assertThat(position.getPrice()).isEqualByComparingTo("60000");
    assertThat(position.getLiquidationPrice()).isEqualByComparingTo("45000");
    assertThat(position.getUnRealisedPnl()).isEqualByComparingTo("100");
  }

  /**
   * Standard core {@code TradeHistoryParamInstrument} implementations must be honored, not
   * discarded by an exact-type gate: the requested instrument filters fills even when the caller
   * never touches the exchange-specific params class.
   */
  @Test
  void generic_trade_history_params_honor_requested_instrument() throws Exception {
    wireMockServer.stubFor(
        get(urlPathEqualTo("/api/v3/trade/fills"))
            .withQueryParam("category", equalTo("spot"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"code\":\"00000\",\"msg\":\"success\",\"requestTime\":1725040472073,"
                            + "\"data\":{\"list\":[{\"execId\":\"e1\",\"orderId\":\"42\","
                            + "\"clientOid\":\"c42\",\"category\":\"spot\",\"symbol\":\"BTCUSDT\","
                            + "\"orderType\":\"limit\",\"side\":\"buy\",\"execPrice\":\"60000\","
                            + "\"execQty\":\"0.1\",\"createdTime\":\"1725040472073\"},"
                            + "{\"execId\":\"e2\",\"orderId\":\"77\",\"clientOid\":\"c77\","
                            + "\"category\":\"spot\",\"symbol\":\"ETHUSDT\","
                            + "\"orderType\":\"limit\",\"side\":\"buy\",\"execPrice\":\"3500\","
                            + "\"execQty\":\"1\",\"createdTime\":\"1725040472073\"}],"
                            + "\"cursor\":\"\"}}")));

    stubEmptyMarginFills();

    DefaultTradeHistoryParamInstrument params = new DefaultTradeHistoryParamInstrument();
    params.setInstrument(CurrencyPair.BTC_USDT);

    UserTrades trades = tradeService.getTradeHistory(params);

    assertThat(trades.getUserTrades()).hasSize(1);
    assertThat(trades.getUserTrades().get(0).getId()).isEqualTo("e1");
    assertThat(trades.getUserTrades().get(0).getInstrument())
        .isEqualTo(CurrencyPair.BTC_USDT);
  }

  /** Same for the currency-pair flavor of the generic core params. */
  @Test
  void generic_currency_pair_params_honor_requested_instrument() throws Exception {
    wireMockServer.stubFor(
        get(urlPathEqualTo("/api/v3/trade/fills"))
            .withQueryParam("category", equalTo("spot"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"code\":\"00000\",\"msg\":\"success\",\"requestTime\":1725040472073,"
                            + "\"data\":{\"list\":[{\"execId\":\"e1\",\"orderId\":\"42\","
                            + "\"clientOid\":\"c42\",\"category\":\"spot\",\"symbol\":\"BTCUSDT\","
                            + "\"orderType\":\"limit\",\"side\":\"buy\",\"execPrice\":\"60000\","
                            + "\"execQty\":\"0.1\",\"createdTime\":\"1725040472073\"},"
                            + "{\"execId\":\"e2\",\"orderId\":\"77\",\"clientOid\":\"c77\","
                            + "\"category\":\"spot\",\"symbol\":\"ETHUSDT\","
                            + "\"orderType\":\"limit\",\"side\":\"buy\",\"execPrice\":\"3500\","
                            + "\"execQty\":\"1\",\"createdTime\":\"1725040472073\"}],"
                            + "\"cursor\":\"\"}}")));

    stubEmptyMarginFills();

    DefaultTradeHistoryParamCurrencyPair params = new DefaultTradeHistoryParamCurrencyPair();
    params.setCurrencyPair(CurrencyPair.BTC_USDT);

    UserTrades trades = tradeService.getTradeHistory(params);

    assertThat(trades.getUserTrades()).hasSize(1);
    assertThat(trades.getUserTrades().get(0).getId()).isEqualTo("e1");
  }

  /**
   * A transport failure on a strategy placement (trigger/TP-SL) must not leak as a plain {@code
   * IOException}: the provider may still have accepted the order, so the outcome is unknown and
   * callers must not replay blindly — same contract as {@code placeOrder}. {@code clientOid} is
   * echoed when the DTO carried one (tpsl orders).
   */
  @Test
  void strategy_placement_transport_failure_surfaces_unknown_outcome() throws Exception {
    wireMockServer.stubFor(
        com.github.tomakehurst.wiremock.client.WireMock.post(
                urlPathEqualTo("/api/v3/trade/place-strategy-order"))
            .willReturn(
                aResponse()
                    .withFault(
                        com.github.tomakehurst.wiremock.http.Fault.CONNECTION_RESET_BY_PEER)));

    BitgetUtaV3StrategyOrderRequest request =
        BitgetUtaV3StrategyOrderRequest.builder()
            .category("spot")
            .symbol("BTCUSDT")
            .type("tpsl")
            .side("buy")
            .qty(new BigDecimal("0.1"))
            .clientOid("strat-oid")
            .build();

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                ((BitgetUtaV3TradeServiceRaw) tradeService)
                    .placeStrategyOrder(request, "api-code"))
        .isInstanceOf(BitgetUtaV3UnknownOutcomeException.class)
        .satisfies(
            t -> {
              BitgetUtaV3UnknownOutcomeException e = (BitgetUtaV3UnknownOutcomeException) t;
              assertThat(e.getProviderCode()).isNull();
              assertThat(e.getClientOid()).isEqualTo("strat-oid");
              assertThat(e.getCause()).isInstanceOf(java.io.IOException.class);
            });

    wireMockServer.verify(
        com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor(
            urlPathEqualTo("/api/v3/trade/place-strategy-order")));
  }

  @Test
  void strategy_ambiguous_placement_surfaces_unknown_outcome_no_replay() throws Exception {
    wireMockServer.stubFor(
        com.github.tomakehurst.wiremock.client.WireMock.post(
                urlPathEqualTo("/api/v3/trade/place-strategy-order"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"code\":\"40725\",\"msg\":\"Order may be placed, please check\","
                            + "\"requestTime\":1725040472073}")));

    BitgetUtaV3StrategyOrderRequest request =
        BitgetUtaV3StrategyOrderRequest.builder()
            .category("spot")
            .symbol("BTCUSDT")
            .type("tpsl")
            .side("buy")
            .qty(new BigDecimal("0.1"))
            .clientOid("strat-oid")
            .build();

    // 40725 means the strategy order may have been accepted server-side; a caller must reconcile
    // by order id instead of treating it as rejected and replaying (which would duplicate orders).
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                ((BitgetUtaV3TradeServiceRaw) tradeService)
                    .placeStrategyOrder(request, "api-code"))
        .isInstanceOf(BitgetUtaV3UnknownOutcomeException.class)
        .satisfies(
            t -> {
              BitgetUtaV3UnknownOutcomeException e = (BitgetUtaV3UnknownOutcomeException) t;
              assertThat(e.getProviderCode()).isEqualTo("40725");
              assertThat(e.getClientOid()).isEqualTo("strat-oid");
            });

    // Zero automatic replay: exactly one strategy placement request hit the wire.
    wireMockServer.verify(
        1,
        com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor(
            urlPathEqualTo("/api/v3/trade/place-strategy-order")));
  }

  @Test
  void strategy_tpsl_placement_injects_generated_client_oid() throws Exception {
    wireMockServer.stubFor(
        com.github.tomakehurst.wiremock.client.WireMock.post(
                urlPathEqualTo("/api/v3/trade/place-strategy-order"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"code\":\"00000\",\"msg\":\"success\",\"requestTime\":1725040472073,"
                            + "\"data\":{\"orderId\":\"123456789\"}}")));

    BitgetUtaV3StrategyOrderRequest request =
        BitgetUtaV3StrategyOrderRequest.builder()
            .category("spot")
            .symbol("BTCUSDT")
            .type("tpsl")
            .side("buy")
            .qty(new BigDecimal("0.1"))
            .build();

    ((BitgetUtaV3TradeServiceRaw) tradeService).placeStrategyOrder(request, "api-code");

    // tpsl is the documented default: a TP-SL placement without a caller clientOid must still get
    // the 6-hour idempotency key (hyphen-stripped UUID, 32 chars, satisfies the wire constraint),
    // so a timeout/ambiguous response can be reconciled instead of replayed into a duplicate
    wireMockServer.verify(
        com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor(
                urlPathEqualTo("/api/v3/trade/place-strategy-order"))
            .withRequestBody(
                com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath(
                    "$.clientOid",
                    com.github.tomakehurst.wiremock.client.WireMock.matching("^[0-9a-f]{32}$"))));
  }

  @Test
  void strategy_trigger_placement_omits_client_oid() throws Exception {
    wireMockServer.stubFor(
        com.github.tomakehurst.wiremock.client.WireMock.post(
                urlPathEqualTo("/api/v3/trade/place-strategy-order"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"code\":\"00000\",\"msg\":\"success\",\"requestTime\":1725040472073,"
                            + "\"data\":{\"orderId\":\"123456789\"}}")));

    BitgetUtaV3StrategyOrderRequest request =
        BitgetUtaV3StrategyOrderRequest.builder()
            .category("spot")
            .symbol("BTCUSDT")
            .type("trigger")
            .side("buy")
            .qty(new BigDecimal("0.1"))
            .triggerPrice(new BigDecimal("55000"))
            .build();

    ((BitgetUtaV3TradeServiceRaw) tradeService).placeStrategyOrder(request, "api-code");

    // trigger orders do not support clientOid: no key may be invented for them
    wireMockServer.verify(
        com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor(
                urlPathEqualTo("/api/v3/trade/place-strategy-order"))
            .withRequestBody(
                com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath(
                    "$.clientOid", com.github.tomakehurst.wiremock.client.WireMock.absent())));
  }

  @Test
  void order_query_by_client_oid_routes_client_reference() throws Exception {
    wireMockServer.stubFor(
        get(urlPathEqualTo("/api/v3/trade/order-info"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"code\":\"00000\",\"msg\":\"success\",\"requestTime\":1725040472073,"
                            + "\"data\":{\"orderId\":\"42\",\"clientOid\":\"c42\","
                            + "\"category\":\"spot\",\"symbol\":\"BTCUSDT\",\"orderType\":\"market\","
                            + "\"side\":\"sell\",\"qty\":\"0.2\",\"cumExecQty\":\"0.2\","
                            + "\"avgPrice\":\"59900\",\"orderStatus\":\"filled\","
                            + "\"createdTime\":\"1725040472073\"}}")));

    BitgetUtaV3OrderQueryParams params = new BitgetUtaV3OrderQueryParams();
    params.setClientOid("c42");

    Collection<Order> orders = tradeService.getOrder(params);

    assertThat(orders).hasSize(1);
    assertThat(orders.iterator().next().getId()).isEqualTo("42");
    // the reconciliation path advertised for unknown-outcome exceptions: the placement clientOid
    // must reach the endpoint's clientOid parameter, not be dropped
    wireMockServer.verify(
        com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor(
                urlPathEqualTo("/api/v3/trade/order-info"))
            .withQueryParam("clientOid", equalTo("c42")));
  }

  /**
   * A transient public-endpoint failure must not silently pair private order data with a {@code
   * null} instrument: the catalog lookup failure propagates as {@code IOException} instead of
   * emitting orders with a null instrument (or filtering requested orders out).
   */
  @Test
  void instrument_catalog_failure_propagates_instead_of_null_instrument() throws Exception {
    // any symbol outside the stubbed catalog forces a fresh catalog fetch; make that fetch fail
    wireMockServer.stubFor(
        get(urlPathEqualTo("/api/v3/market/instruments"))
            .atPriority(1)
            .willReturn(
                aResponse()
                    .withFault(
                        com.github.tomakehurst.wiremock.http.Fault.CONNECTION_RESET_BY_PEER)));
    wireMockServer.stubFor(
        get(urlPathEqualTo("/api/v3/trade/unfilled-orders"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"code\":\"00000\",\"msg\":\"success\",\"requestTime\":1725040472073,"
                            + "\"data\":{\"list\":[{\"orderId\":\"9\",\"clientOid\":\"c9\","
                            + "\"category\":\"spot\",\"symbol\":\"ETHUSDT\",\"orderType\":\"limit\","
                            + "\"side\":\"buy\",\"price\":\"3500\",\"qty\":\"1\","
                            + "\"orderStatus\":\"new\",\"createdTime\":\"1725040472073\"}],"
                            + "\"cursor\":\"\"}}")));

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> tradeService.getOpenOrders())
        .isInstanceOf(java.io.IOException.class);
  }

  /**
   * The endpoint policy must be enforced during pagination: fills page fetches are spaced at the
   * endpoint's 20/s rate (50 ms between requests), so a low-latency run over many pages cannot hit
   * Bitget's limiter partway through {@code getTradeHistory()}. {@code Thread.sleep} only sleeps
   * longer under load, so the wall-clock lower bound is deterministic.
   */
  @Test
  void trade_history_spaces_fills_requests_to_policy_rate() throws Exception {
    wireMockServer.stubFor(
        get(urlPathEqualTo("/api/v3/trade/fills"))
            .atPriority(1)
            .withQueryParam("cursor", com.github.tomakehurst.wiremock.client.WireMock.absent())
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(fillsPage("e1", "1725040472073", "C1"))));
    wireMockServer.stubFor(
        get(urlPathEqualTo("/api/v3/trade/fills"))
            .atPriority(1)
            .withQueryParam("cursor", com.github.tomakehurst.wiremock.client.WireMock.equalTo("C1"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(fillsPage("e2", "1725040472073", "C2"))));
    wireMockServer.stubFor(
        get(urlPathEqualTo("/api/v3/trade/fills"))
            .atPriority(1)
            .withQueryParam("cursor", com.github.tomakehurst.wiremock.client.WireMock.equalTo("C2"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(fillsPage("e3", "1725040472073", ""))));

    long start = System.nanoTime();
    UserTrades trades = tradeService.getTradeHistory(tradeService.createTradeHistoryParams());
    long elapsedMillis = (System.nanoTime() - start) / 1_000_000L;

    assertThat(trades.getUserTrades()).hasSize(3);
    wireMockServer.verify(
        3,
        com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor(
            urlPathEqualTo("/api/v3/trade/fills")));
    // the first request passes unthrottled; the second and third each sleep ~50 ms (2 x 50 ms)
    assertThat(elapsedMillis)
        .as("fills page fetches must be spaced at the endpoint policy's 20/s rate")
        .isGreaterThanOrEqualTo(90);
  }

  /**
   * Same contract for open orders: unfilled-orders pagination must also honor the endpoint policy
   * (20/s) so {@code getOpenOrders()} cannot trip the provider's limiter on many-page accounts.
   */
  @Test
  void open_orders_spaces_unfilled_orders_requests_to_policy_rate() throws Exception {
    wireMockServer.stubFor(
        get(urlPathEqualTo("/api/v3/trade/unfilled-orders"))
            .atPriority(1)
            .withQueryParam("cursor", com.github.tomakehurst.wiremock.client.WireMock.absent())
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"code\":\"00000\",\"msg\":\"success\",\"requestTime\":1725040472073,"
                            + "\"data\":{\"list\":[{\"orderId\":\"1\",\"clientOid\":\"c1\","
                            + "\"category\":\"spot\",\"symbol\":\"BTCUSDT\",\"orderType\":\"limit\","
                            + "\"side\":\"buy\",\"price\":\"60000\",\"qty\":\"0.1\","
                            + "\"orderStatus\":\"new\",\"createdTime\":\"1725040472073\"}],"
                            + "\"cursor\":\"o1\"}}")));
    wireMockServer.stubFor(
        get(urlPathEqualTo("/api/v3/trade/unfilled-orders"))
            .atPriority(1)
            .withQueryParam("cursor", com.github.tomakehurst.wiremock.client.WireMock.equalTo("o1"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"code\":\"00000\",\"msg\":\"success\",\"requestTime\":1725040472073,"
                            + "\"data\":{\"list\":[{\"orderId\":\"2\",\"clientOid\":\"c2\","
                            + "\"category\":\"spot\",\"symbol\":\"BTCUSDT\",\"orderType\":\"limit\","
                            + "\"side\":\"buy\",\"price\":\"60000\",\"qty\":\"0.1\","
                            + "\"orderStatus\":\"new\",\"createdTime\":\"1725040472073\"}],"
                            + "\"cursor\":\"\"}}")));

    long start = System.nanoTime();
    OpenOrders openOrders = tradeService.getOpenOrders(new DefaultOpenOrdersParamInstrument());
    long elapsedMillis = (System.nanoTime() - start) / 1_000_000L;

    assertThat(openOrders.getOpenOrders()).hasSize(2);
    wireMockServer.verify(
        2,
        com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor(
            urlPathEqualTo("/api/v3/trade/unfilled-orders")));
    // the first request passes unthrottled; the second sleeps ~50 ms
    assertThat(elapsedMillis)
        .as("unfilled-orders page fetches must be spaced at the endpoint policy's 20/s rate")
        .isGreaterThanOrEqualTo(45);
  }

  /**
   * Instrument-scoped history queries both spot and margin (they share the {@link CurrencyPair}
   * identity), so every such test must answer the margin leg; this stub returns no margin fills.
   */
  private static void stubEmptyMarginFills() {
    wireMockServer.stubFor(
        get(urlPathEqualTo("/api/v3/trade/fills"))
            .withQueryParam("category", equalTo("margin"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"code\":\"00000\",\"msg\":\"success\",\"requestTime\":1725040472073,"
                            + "\"data\":{\"list\":[],\"cursor\":\"\"}}")));
  }

  private static String fillsPage(String execIdsCsv, String createdTime, String cursor) {
    StringBuilder list = new StringBuilder();
    String[] ids = execIdsCsv.split(",");
    for (int i = 0; i < ids.length; i++) {
      if (i > 0) {
        list.append(',');
      }
      list.append("{\"execId\":\"").append(ids[i]).append("\",\"orderId\":\"42\",\"clientOid\":\"c")
          .append(ids[i])
          .append("\",\"category\":\"spot\",\"symbol\":\"BTCUSDT\",\"orderType\":\"limit\",")
          .append("\"side\":\"buy\",\"execPrice\":\"60000\",\"execQty\":\"0.1\",\"createdTime\":\"")
          .append(createdTime)
          .append("\"}");
    }
    return "{\"code\":\"00000\",\"msg\":\"success\",\"requestTime\":1725040472073,"
        + "\"data\":{\"list\":["
        + list
        + "],\"cursor\":\""
        + cursor
        + "\"}}";
  }
}
