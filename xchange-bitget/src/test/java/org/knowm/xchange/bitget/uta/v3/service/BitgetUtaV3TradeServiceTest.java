package org.knowm.xchange.bitget.uta.v3.service;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.bitget.uta.v3.BitgetUtaV3ExchangeWiremock;
import org.knowm.xchange.bitget.uta.v3.BitgetUtaV3UnknownOutcomeException;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.account.OpenPosition;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.OpenOrders;
import org.knowm.xchange.dto.trade.UserTrades;
import org.knowm.xchange.service.trade.TradeService;
import org.knowm.xchange.service.trade.params.DefaultTradeHistoryParamCurrencyPair;
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

    BitgetUtaV3TradeService.BitgetUtaV3TradeHistoryParams params =
        (BitgetUtaV3TradeService.BitgetUtaV3TradeHistoryParams)
            tradeService.createTradeHistoryParams();
    params.setInstrument(CurrencyPair.BTC_USDT);

    UserTrades trades = tradeService.getTradeHistory(params);

    assertThat(trades.getUserTrades()).hasSize(1);
    assertThat(trades.getUserTrades().get(0).getId()).isEqualTo("e1");
    assertThat(trades.getUserTrades().get(0).getInstrument())
        .isEqualTo(CurrencyPair.BTC_USDT);
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
    wireMockServer.verify(
        2,
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
}
