package org.knowm.xchange.mexc.v3.service;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.http.Fault;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Collection;
import org.junit.Test;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order.OrderStatus;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.dto.trade.OpenOrders;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.dto.trade.UserTrades;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.exceptions.OrderAmountUnderMinimumException;
import org.knowm.xchange.exceptions.OrderNotValidException;
import org.knowm.xchange.mexc.v3.BaseMexcV3WiremockTest;
import org.knowm.xchange.mexc.v3.MexcV3OrderFlags;
import org.knowm.xchange.mexc.v3.client.MexcV3Exception;
import org.knowm.xchange.mexc.v3.client.RetryClassification;
import org.knowm.xchange.mexc.v3.dto.trade.MexcV3OrderSide;
import org.knowm.xchange.mexc.v3.dto.trade.MexcV3OrderType;
import org.knowm.xchange.service.trade.params.CancelAllOrders;
import org.knowm.xchange.service.trade.params.CancelOrderByCurrencyPair;
import org.knowm.xchange.service.trade.params.CancelOrderByIdParams;
import org.knowm.xchange.service.trade.params.CancelOrderByUserReferenceParams;
import org.knowm.xchange.service.trade.params.DefaultCancelAllOrdersByInstrument;
import org.knowm.xchange.service.trade.params.TradeHistoryParamCurrencyPair;
import org.knowm.xchange.service.trade.params.TradeHistoryParamOrderId;
import org.knowm.xchange.service.trade.params.TradeHistoryParams;
import org.knowm.xchange.service.trade.params.TradeHistoryParamsTimeSpan;
import org.knowm.xchange.service.trade.params.orders.DefaultOpenOrdersParamCurrencyPair;
import org.knowm.xchange.service.trade.params.orders.DefaultQueryOrderParamCurrencyPair;
import org.knowm.xchange.service.trade.params.orders.OpenOrdersParams;
import org.knowm.xchange.service.trade.params.orders.OrderQueryParams;

/** Placement replay-safety, cancel, order query and history coverage (WireMock). */
public class MexcV3TradeServiceTest extends BaseMexcV3WiremockTest {

  private MexcV3TradeService tradeService() throws IOException {
    return (MexcV3TradeService) createExchange().getTradeService();
  }

  private static final String ORDER_BODY =
      "{\"symbol\":\"BTCUSDT\",\"orderId\":\"123456789\",\"orderListId\":-1,"
          + "\"price\":\"60000.00\",\"origQty\":\"0.001\",\"executedQty\":\"0.000\","
          + "\"cummulativeQuoteQty\":\"0.000\",\"status\":\"NEW\",\"timeInForce\":\"GTC\","
          + "\"type\":\"LIMIT\",\"side\":\"BUY\",\"stopPrice\":\"0.0\",\"icebergQty\":\"0.0\","
          + "\"time\":1645539742000,\"updateTime\":1645539742000,\"isWorking\":true,"
          + "\"origQuoteOrderQty\":\"0.000000\"}";

  @Test
  public void placeLimitOrderReturnsProviderOrderIdAndSignsRequest() throws IOException {
    stubFor(
        post(urlPathEqualTo("/api/v3/order"))
            .willReturn(
                aResponse()
                    .withBody(
                        "{\"symbol\":\"BTCUSDT\",\"orderId\":\"987654321\",\"orderListId\":-1,"
                            + "\"price\":\"60000.00\",\"origQty\":\"0.001\",\"type\":\"LIMIT\","
                            + "\"side\":\"BUY\",\"transactTime\":1645539742000}")));

    LimitOrder order =
        new LimitOrder.Builder(OrderType.BID, CurrencyPair.BTC_USDT)
            .originalAmount(new BigDecimal("0.001"))
            .limitPrice(new BigDecimal("60000.00"))
            .userReference("my-ref-1")
            .build();

    assertThat(tradeService().placeLimitOrder(order)).isEqualTo("987654321");
    verify(
        postRequestedFor(urlPathEqualTo("/api/v3/order"))
            .withQueryParam("symbol", com.github.tomakehurst.wiremock.client.WireMock.equalTo("BTCUSDT"))
            .withQueryParam("side", com.github.tomakehurst.wiremock.client.WireMock.equalTo("BUY"))
            .withQueryParam("type", com.github.tomakehurst.wiremock.client.WireMock.equalTo("LIMIT"))
            .withQueryParam("newClientOrderId", com.github.tomakehurst.wiremock.client.WireMock.equalTo("my-ref-1"))
            .withQueryParam("signature", com.github.tomakehurst.wiremock.client.WireMock.matching("[a-f0-9]{64}"))
            .withHeader("X-MEXC-APIKEY", com.github.tomakehurst.wiremock.client.WireMock.equalTo("test_api_key")));
  }

  @Test
  public void placeMarketOrderBuyDefaultsToBaseQuantity() throws IOException {
    stubFor(
        post(urlPathEqualTo("/api/v3/order"))
            .willReturn(
                aResponse()
                    .withBody(
                        "{\"symbol\":\"BTCUSDT\",\"orderId\":\"1\",\"orderListId\":-1,"
                            + "\"price\":\"60000.00\",\"origQty\":\"0.001\",\"type\":\"MARKET\","
                            + "\"side\":\"BUY\",\"transactTime\":1645539742000}")));

    MarketOrder order =
        new MarketOrder.Builder(OrderType.BID, CurrencyPair.BTC_USDT)
            .originalAmount(new BigDecimal("0.001"))
            .build();

    assertThat(tradeService().placeMarketOrder(order)).isEqualTo("1");
    verify(
        postRequestedFor(urlPathEqualTo("/api/v3/order"))
            .withQueryParam("quantity", com.github.tomakehurst.wiremock.client.WireMock.equalTo("0.001"))
            .withoutQueryParam("quoteOrderQty"));
  }

  @Test
  public void placeMarketOrderBuyWithQuoteOrderQtyFlagSendsQuoteOrderQty() throws IOException {
    stubFor(
        post(urlPathEqualTo("/api/v3/order"))
            .willReturn(
                aResponse()
                    .withBody(
                        "{\"symbol\":\"BTCUSDT\",\"orderId\":\"1\",\"orderListId\":-1,"
                            + "\"price\":\"60000.00\",\"origQty\":\"0.001\",\"type\":\"MARKET\","
                            + "\"side\":\"BUY\",\"transactTime\":1645539742000}")));

    MarketOrder order =
        new MarketOrder.Builder(OrderType.BID, CurrencyPair.BTC_USDT)
            .originalAmount(new BigDecimal("60.00"))
            .flag(MexcV3OrderFlags.QUOTE_ORDER_QTY)
            .build();

    assertThat(tradeService().placeMarketOrder(order)).isEqualTo("1");
    verify(
        postRequestedFor(urlPathEqualTo("/api/v3/order"))
            .withQueryParam("quoteOrderQty", com.github.tomakehurst.wiremock.client.WireMock.equalTo("60.00"))
            .withoutQueryParam("quantity"));
  }

  @Test
  public void placeMarketOrderSellWithQuoteOrderQtyFlagIsRejected() throws IOException {
    MarketOrder order =
        new MarketOrder.Builder(OrderType.ASK, CurrencyPair.BTC_USDT)
            .originalAmount(new BigDecimal("0.001"))
            .flag(MexcV3OrderFlags.QUOTE_ORDER_QTY)
            .build();

    assertThatThrownBy(() -> tradeService().placeMarketOrder(order))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("only valid on market BUY");
    verify(0, postRequestedFor(urlPathEqualTo("/api/v3/order")));
  }

  @Test
  public void placeMarketOrderGeneratesCorrelationClientOrderId() throws IOException {
    stubFor(
        post(urlPathEqualTo("/api/v3/order"))
            .willReturn(
                aResponse()
                    .withBody(
                        "{\"symbol\":\"BTCUSDT\",\"orderId\":\"1\",\"orderListId\":-1,"
                            + "\"price\":\"60000.00\",\"origQty\":\"0.001\",\"type\":\"MARKET\","
                            + "\"side\":\"BUY\",\"transactTime\":1645539742000}")));

    MarketOrder order =
        new MarketOrder.Builder(OrderType.BID, CurrencyPair.BTC_USDT)
            .originalAmount(new BigDecimal("0.001"))
            .build();

    assertThat(tradeService().placeMarketOrder(order)).isEqualTo("1");
    verify(
        postRequestedFor(urlPathEqualTo("/api/v3/order"))
            .withQueryParam(
                "newClientOrderId",
                com.github.tomakehurst.wiremock.client.WireMock.matching("[a-f0-9]{32}")));
  }

  @Test
  public void placeMarketOrderSellUsesQuantityAndNoQuoteOrderQty() throws IOException {
    stubFor(
        post(urlPathEqualTo("/api/v3/order"))
            .willReturn(
                aResponse()
                    .withBody(
                        "{\"symbol\":\"BTCUSDT\",\"orderId\":\"2\",\"orderListId\":-1,"
                            + "\"price\":\"60000.00\",\"origQty\":\"0.001\",\"type\":\"MARKET\","
                            + "\"side\":\"SELL\",\"transactTime\":1645539742000}")));

    MarketOrder order =
        new MarketOrder.Builder(OrderType.ASK, CurrencyPair.BTC_USDT)
            .originalAmount(new BigDecimal("0.001"))
            .build();

    assertThat(tradeService().placeMarketOrder(order)).isEqualTo("2");
    verify(
        postRequestedFor(urlPathEqualTo("/api/v3/order"))
            .withQueryParam("quantity", com.github.tomakehurst.wiremock.client.WireMock.equalTo("0.001"))
            .withoutQueryParam("quoteOrderQty"));
  }

  @Test
  public void placementTransportFailureIsAmbiguousNeverBlindlyReplayed() throws IOException {
    stubFor(
        post(urlPathEqualTo("/api/v3/order"))
            .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
    // The reconciliation lookup also fails at the transport layer: outcome stays unknown.
    stubFor(
        get(urlPathEqualTo("/api/v3/order"))
            .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

    LimitOrder order =
        new LimitOrder.Builder(OrderType.BID, CurrencyPair.BTC_USDT)
            .originalAmount(new BigDecimal("0.001"))
            .limitPrice(new BigDecimal("60000.00"))
            .build();

    assertThatThrownBy(() -> tradeService().placeLimitOrder(order))
        .isInstanceOf(MexcV3Exception.class)
        .hasMessageContaining("outcome is ambiguous")
        .hasMessageContaining("reconcile")
        .isInstanceOfSatisfying(
            MexcV3Exception.class,
            e ->
                assertThat(e.getRetryClassification())
                    .isEqualTo(RetryClassification.AMBIGUOUS));
    // Exactly one reconciliation lookup by the generated correlation id; no orderId query.
    verify(
        getRequestedFor(urlPathEqualTo("/api/v3/order"))
            .withQueryParam(
                "origClientOrderId",
                com.github.tomakehurst.wiremock.client.WireMock.matching("[a-f0-9]{32}"))
            .withoutQueryParam("orderId"));
  }

  @Test
  public void ambiguousPlacementReconcilesFoundOrderByClientOrderId() throws IOException {
    stubFor(
        post(urlPathEqualTo("/api/v3/order"))
            .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
    stubFor(
        get(urlPathEqualTo("/api/v3/order"))
            .willReturn(aResponse().withBody(ORDER_BODY)));

    LimitOrder order =
        new LimitOrder.Builder(OrderType.BID, CurrencyPair.BTC_USDT)
            .originalAmount(new BigDecimal("0.001"))
            .limitPrice(new BigDecimal("60000.00"))
            .userReference("my-ref-1")
            .build();

    // The transport failure was inconclusive, but the lookup proves the order exists.
    assertThat(tradeService().placeLimitOrder(order)).isEqualTo("123456789");
    verify(
        getRequestedFor(urlPathEqualTo("/api/v3/order"))
            .withQueryParam("symbol", com.github.tomakehurst.wiremock.client.WireMock.equalTo("BTCUSDT"))
            .withQueryParam(
                "origClientOrderId",
                com.github.tomakehurst.wiremock.client.WireMock.equalTo("my-ref-1")));
  }

  @Test
  public void ambiguousPlacementLookupNotFoundAdaptsToOrderNotValid() throws IOException {
    stubFor(
        post(urlPathEqualTo("/api/v3/order"))
            .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
    stubFor(
        get(urlPathEqualTo("/api/v3/order"))
            .willReturn(
                aResponse()
                    .withStatus(400)
                    .withBody("{\"code\":20116,\"msg\":\"Order does not exist\"}")));

    LimitOrder order =
        new LimitOrder.Builder(OrderType.BID, CurrencyPair.BTC_USDT)
            .originalAmount(new BigDecimal("0.001"))
            .limitPrice(new BigDecimal("60000.00"))
            .userReference("my-ref-1")
            .build();

    // The lookup proves the order is absent: the provider rejection is definitive.
    assertThatThrownBy(() -> tradeService().placeLimitOrder(order))
        .isInstanceOf(OrderNotValidException.class);
  }

  @Test
  public void ambiguousPlacementLookupRateLimitPreservesAmbiguity() throws IOException {
    stubFor(
        post(urlPathEqualTo("/api/v3/order"))
            .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
    stubFor(
        get(urlPathEqualTo("/api/v3/order"))
            .willReturn(
                aResponse()
                    .withStatus(429)
                    .withBody("{\"code\":429,\"msg\":\"Too many requests\"}")));

    LimitOrder order =
        new LimitOrder.Builder(OrderType.BID, CurrencyPair.BTC_USDT)
            .originalAmount(new BigDecimal("0.001"))
            .limitPrice(new BigDecimal("60000.00"))
            .userReference("my-ref-1")
            .build();

    // A rate-limit lookup does NOT prove absence: the order may still have been accepted, so the
    // original AMBIGUOUS classification must survive and the caller must not retry blindly.
    assertThatThrownBy(() -> tradeService().placeLimitOrder(order))
        .isInstanceOf(MexcV3Exception.class)
        .satisfies(
            e ->
                assertThat(((MexcV3Exception) e).getRetryClassification())
                    .isEqualTo(RetryClassification.AMBIGUOUS));
  }

  @Test
  public void placementProviderErrorAdaptsToExceptionHierarchy() throws IOException {
    stubFor(
        post(urlPathEqualTo("/api/v3/order"))
            .willReturn(
                aResponse()
                    .withStatus(400)
                    .withBody("{\"code\":30002,\"msg\":\"The minimum transaction volume\"}")));

    LimitOrder order =
        new LimitOrder.Builder(OrderType.BID, CurrencyPair.BTC_USDT)
            .originalAmount(new BigDecimal("0.0000001"))
            .limitPrice(new BigDecimal("60000.00"))
            .build();

    assertThatThrownBy(() -> tradeService().placeLimitOrder(order))
        .isInstanceOf(OrderAmountUnderMinimumException.class);
  }

  @Test
  public void cancelOrderByIdReturnsTrue() throws IOException {
    stubFor(delete(urlPathEqualTo("/api/v3/order")).willReturn(aResponse().withBody(ORDER_BODY)));

    assertThat(
            tradeService()
                .cancelOrder(new CancelParams("123456789", CurrencyPair.BTC_USDT, null)))
        .isTrue();
    verify(
        deleteRequestedFor(urlPathEqualTo("/api/v3/order"))
            .withQueryParam("orderId", com.github.tomakehurst.wiremock.client.WireMock.equalTo("123456789")));
  }

  @Test
  public void cancelOrderByUserReferenceUsesOrigClientOrderId() throws IOException {
    stubFor(delete(urlPathEqualTo("/api/v3/order")).willReturn(aResponse().withBody(ORDER_BODY)));

    assertThat(
            tradeService().cancelOrder(new CancelParams(null, CurrencyPair.BTC_USDT, "ref-42")))
        .isTrue();
    verify(
        deleteRequestedFor(urlPathEqualTo("/api/v3/order"))
            .withQueryParam("origClientOrderId", com.github.tomakehurst.wiremock.client.WireMock.equalTo("ref-42")));
  }

  @Test
  public void cancelAllOrdersReturnsProviderIds() throws IOException {
    stubFor(
        delete(urlPathEqualTo("/api/v3/openOrders"))
            .willReturn(
                aResponse()
                    .withBody(
                        "["
                            + ORDER_BODY
                            + ",{\"symbol\":\"BTCUSDT\",\"orderId\":\"555\",\"orderListId\":-1,"
                            + "\"price\":\"61000.00\",\"origQty\":\"0.002\",\"executedQty\":\"0.000\","
                            + "\"cummulativeQuoteQty\":\"0.000\",\"status\":\"NEW\",\"timeInForce\":\"GTC\","
                            + "\"type\":\"LIMIT\",\"side\":\"BUY\",\"stopPrice\":\"0.0\",\"icebergQty\":\"0.0\","
                            + "\"time\":1645539742000,\"updateTime\":1645539742000,\"isWorking\":true,"
                            + "\"origQuoteOrderQty\":\"0.000000\"}]")));

    CancelAllOrders params = new DefaultCancelAllOrdersByInstrument(CurrencyPair.BTC_USDT);
    Collection<String> ids = tradeService().cancelAllOrders(params);

    assertThat(ids).containsExactly("123456789", "555");
  }

  @Test
  public void cancelOrderWithoutCurrencyPairIsRejected() throws IOException {
    CancelOrderByIdParams params =
        new CancelOrderByIdParams() {
          @Override
          public String getOrderId() {
            return "1";
          }
        };
    assertThatThrownBy(() -> tradeService().cancelOrder(params))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("requires a currency pair/instrument");
  }

  @Test
  public void getOpenOrdersAdaptsProviderOrders() throws IOException {
    stubFor(
        get(urlPathEqualTo("/api/v3/openOrders")).willReturn(aResponse().withBody("[" + ORDER_BODY + "]")));

    OpenOrdersParams params = new DefaultOpenOrdersParamCurrencyPair();
    ((org.knowm.xchange.service.trade.params.orders.OpenOrdersParamCurrencyPair) params)
        .setCurrencyPair(CurrencyPair.BTC_USDT);

    OpenOrders openOrders = tradeService().getOpenOrders(params);
    assertThat(openOrders.getOpenOrders()).hasSize(1);
    LimitOrder order = openOrders.getOpenOrders().get(0);
    assertThat(order.getId()).isEqualTo("123456789");
    assertThat(order.getInstrument()).isEqualTo(CurrencyPair.BTC_USDT);
    assertThat(order.getType()).isEqualTo(OrderType.BID);
    assertThat(order.getOriginalAmount()).isEqualByComparingTo("0.001");
    assertThat(order.getLimitPrice()).isEqualByComparingTo("60000.00");
    assertThat(order.getStatus()).isEqualTo(OrderStatus.NEW);
  }

  @Test
  public void getOpenOrdersSplitsMarketAndLimitOrders() throws IOException {
    stubFor(
        get(urlPathEqualTo("/api/v3/openOrders"))
            .willReturn(
                aResponse()
                    .withBody(
                        "["
                            + ORDER_BODY
                            + ",{\"symbol\":\"BTCUSDT\",\"orderId\":\"777\",\"orderListId\":-1,"
                            + "\"price\":\"0.0\",\"origQty\":\"0.002\",\"executedQty\":\"0.001\","
                            + "\"cummulativeQuoteQty\":\"0.000\",\"status\":\"PARTIALLY_FILLED\","
                            + "\"timeInForce\":\"GTC\",\"type\":\"MARKET\",\"side\":\"BUY\","
                            + "\"stopPrice\":\"0.0\",\"icebergQty\":\"0.0\","
                            + "\"time\":1645539743000,\"updateTime\":1645539743000,"
                            + "\"isWorking\":true,\"origQuoteOrderQty\":\"0.000000\"}]")));

    OpenOrdersParams params = new DefaultOpenOrdersParamCurrencyPair();
    ((org.knowm.xchange.service.trade.params.orders.OpenOrdersParamCurrencyPair) params)
        .setCurrencyPair(CurrencyPair.BTC_USDT);

    OpenOrders openOrders = tradeService().getOpenOrders(params);

    assertThat(openOrders.getOpenOrders()).hasSize(1);
    assertThat(openOrders.getOpenOrders().get(0).getId()).isEqualTo("123456789");
    assertThat(openOrders.getOpenOrders().get(0)).isInstanceOf(LimitOrder.class);
    assertThat(openOrders.getAllOpenOrders()).hasSize(2);
    org.knowm.xchange.dto.Order market = openOrders.getAllOpenOrders().get(1);
    assertThat(market).isInstanceOf(MarketOrder.class);
    assertThat(market.getId()).isEqualTo("777");
    assertThat(market.getStatus()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
    assertThat(market.getCumulativeAmount()).isEqualByComparingTo("0.001");
  }

  @Test
  public void getOpenOrdersWithoutSymbolThrows() throws IOException {
    assertThatThrownBy(() -> tradeService().getOpenOrders())
        .isInstanceOf(ExchangeException.class)
        .hasMessageContaining("requires a symbol");
  }

  @Test
  public void getOrderAdaptsProviderOrder() throws IOException {
    stubFor(get(urlPathEqualTo("/api/v3/order")).willReturn(aResponse().withBody(ORDER_BODY)));

    OrderQueryParams query =
        new DefaultQueryOrderParamCurrencyPair(CurrencyPair.BTC_USDT, "123456789");
    Collection<org.knowm.xchange.dto.Order> orders = tradeService().getOrder(query);

    assertThat(orders).hasSize(1);
    org.knowm.xchange.dto.Order order = orders.iterator().next();
    assertThat(order.getId()).isEqualTo("123456789");
    assertThat(order.getInstrument()).isEqualTo(CurrencyPair.BTC_USDT);
  }

  @Test
  public void getTradeHistoryAdaptsMyTrades() throws IOException {
    stubFor(
        get(urlPathEqualTo("/api/v3/myTrades"))
            .willReturn(
                aResponse()
                    .withBody(
                        "[{\"symbol\":\"BTCUSDT\",\"id\":28457,\"orderId\":\"100234\","
                            + "\"orderListId\":-1,\"price\":\"33198.31\",\"qty\":\"0.001\","
                            + "\"quoteQty\":\"33.19831\",\"commission\":\"0.000001\","
                            + "\"commissionAsset\":\"BTC\",\"time\":1621305898000,"
                            + "\"isBuyer\":true,\"isMaker\":false,\"isBestMatch\":true,"
                            + "\"isSelfTrade\":false,\"clientOrderId\":\"my-trade-ref\"}]")));

    MexcV3TradeHistoryParams history = new MexcV3TradeHistoryParams();
    history.setCurrencyPair(CurrencyPair.BTC_USDT);

    UserTrades trades = tradeService().getTradeHistory(history);

    assertThat(trades.getUserTrades()).hasSize(1);
    org.knowm.xchange.dto.trade.UserTrade trade = trades.getUserTrades().get(0);
    assertThat(trade.getId()).isEqualTo("28457");
    assertThat(trade.getOrderId()).isEqualTo("100234");
    assertThat(trade.getInstrument()).isEqualTo(CurrencyPair.BTC_USDT);
    assertThat(trade.getType()).isEqualTo(OrderType.BID);
    assertThat(trade.getOriginalAmount()).isEqualByComparingTo("0.001");
    assertThat(trade.getPrice()).isEqualByComparingTo("33198.31");
    assertThat(trade.getFeeAmount()).isEqualByComparingTo("0.000001");
    assertThat(trade.getFeeCurrency().toString()).isEqualTo("BTC");
  }

  @Test
  public void getTradeHistoryPagesForwardRespectingExplicitLimit() throws IOException {
    // WireMock matches the most recently added stub first, so the generic first-page stub is
    // declared before the window-specific stubs. Page size is capped at the provider maximum
    // of 100 per request. The cursor is inclusive: each next page re-fetches the boundary
    // millisecond, so the second page starts at the first page's newest trade time (1099) and
    // the boundary trade (id 1099) is deduplicated rather than skipped. Only newly collected
    // trades count toward the caller's limit: after two full pages the budget is 51, so the
    // third request pages with limit 51 and a short reply (40 rows) exhausts the window.
    stubFor(
        get(urlPathEqualTo("/api/v3/myTrades")).willReturn(aResponse().withBody(myTradesBody(1000, 100))));
    stubFor(
        get(urlPathEqualTo("/api/v3/myTrades"))
            .withQueryParam("startTime", equalTo("1099"))
            .withQueryParam("limit", equalTo("100"))
            .willReturn(aResponse().withBody(myTradesBody(1099, 100))));
    stubFor(
        get(urlPathEqualTo("/api/v3/myTrades"))
            .withQueryParam("startTime", equalTo("1198"))
            .withQueryParam("limit", equalTo("51"))
            .willReturn(aResponse().withBody(myTradesBody(1198, 40))));

    MexcV3TradeHistoryParams history = new MexcV3TradeHistoryParams();
    history.setCurrencyPair(CurrencyPair.BTC_USDT);
    history.setLimit(250);

    UserTrades trades = tradeService().getTradeHistory(history);

    assertThat(trades.getUserTrades()).hasSize(238); // 100 + 99 + 39; boundary ids deduplicated
    assertThat(trades.getUserTrades().get(0).getId()).isEqualTo("1000");
    assertThat(trades.getUserTrades().get(237).getId()).isEqualTo("1237");
    verify(3, getRequestedFor(urlPathEqualTo("/api/v3/myTrades")));
    verify(
        getRequestedFor(urlPathEqualTo("/api/v3/myTrades"))
            .withQueryParam("startTime", equalTo("1099"))
            .withQueryParam("limit", equalTo("100")));
    verify(
        getRequestedFor(urlPathEqualTo("/api/v3/myTrades"))
            .withQueryParam("startTime", equalTo("1198"))
            .withQueryParam("limit", equalTo("51")));
  }

  @Test
  public void getTradeHistoryKeepsFillsSharingTheBoundaryMillisecond() throws IOException {
    // A full page whose newest fills share one millisecond must not skip the rest: the cursor
    // re-fetches from the boundary timestamp (inclusive) and deduplicates by trade id. The
    // second page (10 rows at t=1000) is short, so the window is exhausted and the unseen ids
    // are all collected.
    StringBuilder firstPage = new StringBuilder("[");
    for (int i = 1; i <= 97; i++) {
      if (i > 1) {
        firstPage.append(',');
      }
      firstPage.append(myTradeRow(999L, i));
    }
    for (int i = 98; i <= 100; i++) {
      firstPage.append(',').append(myTradeRow(1000L, i));
    }
    firstPage.append(']');
    stubFor(
        get(urlPathEqualTo("/api/v3/myTrades"))
            .willReturn(aResponse().withBody(firstPage.toString())));
    stubFor(
        get(urlPathEqualTo("/api/v3/myTrades"))
            .withQueryParam("startTime", equalTo("1000"))
            .willReturn(aResponse().withBody(myTradesAtTime(1000L, 98, 10))));

    MexcV3TradeHistoryParams history = new MexcV3TradeHistoryParams();
    history.setCurrencyPair(CurrencyPair.BTC_USDT);

    UserTrades trades = tradeService().getTradeHistory(history);

    assertThat(trades.getUserTrades()).hasSize(107); // 97 + 3 + 7 remaining at t=1000
    java.util.Set<String> ids =
        trades.getUserTrades().stream()
            .map(UserTrade::getId)
            .collect(java.util.stream.Collectors.toSet());
    assertThat(ids).hasSize(107); // no duplicates from the inclusive boundary re-fetch
    assertThat(ids).contains("98", "100", "101", "107");
    verify(2, getRequestedFor(urlPathEqualTo("/api/v3/myTrades")));
    verify(
        1,
        getRequestedFor(urlPathEqualTo("/api/v3/myTrades"))
            .withQueryParam("startTime", equalTo("1000")));
  }

  @Test
  public void getTradeHistoryContinuesPastABoundaryMillisecondOnFullPages() throws IOException {
    // The reviewer-grade case: a full page ends with fills at t=1000, and 97 more fills share
    // that same millisecond. An exclusive cursor would skip them; the inclusive cursor re-fetches
    // from t=1000, and because the second page is full (100 rows, 3 seen + 97 new) the loop must
    // keep going until a repeated page adds nothing new.
    StringBuilder firstPage = new StringBuilder("[");
    for (int i = 1; i <= 97; i++) {
      if (i > 1) {
        firstPage.append(',');
      }
      firstPage.append(myTradeRow(999L, i));
    }
    for (int i = 98; i <= 100; i++) {
      firstPage.append(',').append(myTradeRow(1000L, i));
    }
    firstPage.append(']');
    stubFor(
        get(urlPathEqualTo("/api/v3/myTrades"))
            .willReturn(aResponse().withBody(firstPage.toString())));
    stubFor(
        get(urlPathEqualTo("/api/v3/myTrades"))
            .withQueryParam("startTime", equalTo("1000"))
            .willReturn(aResponse().withBody(myTradesAtTime(1000L, 98, 100))));
    // After the repeated t=1000 page the pager skips the unqueryable millisecond and probes
    // t=1001; a startTime-honoring provider answers with an empty page and the span ends.
    stubFor(
        get(urlPathEqualTo("/api/v3/myTrades"))
            .withQueryParam("startTime", equalTo("1001"))
            .willReturn(aResponse().withBody("[]")));

    MexcV3TradeHistoryParams history = new MexcV3TradeHistoryParams();
    history.setCurrencyPair(CurrencyPair.BTC_USDT);

    UserTrades trades = tradeService().getTradeHistory(history);

    assertThat(trades.getUserTrades()).hasSize(197); // 97 + 3 + 97 more at t=1000
    java.util.Set<String> ids =
        trades.getUserTrades().stream()
            .map(UserTrade::getId)
            .collect(java.util.stream.Collectors.toSet());
    assertThat(ids).hasSize(197); // no duplicates from the inclusive boundary re-fetch
    assertThat(ids).contains("98", "100", "101", "194", "197");
    verify(4, getRequestedFor(urlPathEqualTo("/api/v3/myTrades")));
    verify(
        2,
        getRequestedFor(urlPathEqualTo("/api/v3/myTrades"))
            .withQueryParam("startTime", equalTo("1000")));
  }

  @Test
  public void getTradeHistoryPartitionsLongSpanIntoProviderWindows() throws IOException {
    // A span longer than the provider's one-month per-request window must be split into
    // 30-day windows before paging, otherwise the first request would exceed the endpoint's
    // queryable range and be rejected.
    stubFor(
        get(urlPathEqualTo("/api/v3/myTrades")).willReturn(aResponse().withBody("[]")));

    long start = 1_700_000_000_000L; // 2023-11-14, well in the past
    long windowMs = 30L * 24 * 60 * 60 * 1000;
    MexcV3TradeHistoryParams history = new MexcV3TradeHistoryParams();
    history.setCurrencyPair(CurrencyPair.BTC_USDT);
    history.setStartTime(new java.util.Date(start));
    history.setEndTime(new java.util.Date(start + 3 * windowMs));

    UserTrades trades = tradeService().getTradeHistory(history);

    assertThat(trades.getUserTrades()).isEmpty();
    java.util.List<com.github.tomakehurst.wiremock.verification.LoggedRequest> requests =
        wireMockRule.findAll(getRequestedFor(urlPathEqualTo("/api/v3/myTrades")));
    assertThat(requests).hasSize(3);
    assertThat(
            requests.stream()
                .map(e -> e.queryParameter("startTime").firstValue())
                .collect(java.util.stream.Collectors.toList()))
        .containsExactly(
            String.valueOf(start), String.valueOf(start + windowMs), String.valueOf(start + 2 * windowMs));
    assertThat(
            requests.stream()
                .map(e -> e.queryParameter("endTime").firstValue())
                .collect(java.util.stream.Collectors.toList()))
        .containsExactly(
            String.valueOf(start + windowMs - 1),
            String.valueOf(start + 2 * windowMs - 1),
            String.valueOf(start + 3 * windowMs));
  }

  @Test
  public void getTradeHistoryStopsWhenWindowDoesNotAdvance() throws IOException {
    // A full page whose newest trade never moves simulates a provider that ignores startTime:
    // the first repeated page could still be a same-millisecond overflow, so the pager probes
    // one millisecond ahead; a second consecutive repeat proves startTime is ignored and the
    // no-progress guard must stop the loop.
    stubFor(get(urlPathEqualTo("/api/v3/myTrades")).willReturn(aResponse().withBody(myTradesBody(5000, 100))));

    MexcV3TradeHistoryParams history = new MexcV3TradeHistoryParams();
    history.setCurrencyPair(CurrencyPair.BTC_USDT);

    UserTrades trades = tradeService().getTradeHistory(history);

    assertThat(trades.getUserTrades()).hasSize(100);
    verify(3, getRequestedFor(urlPathEqualTo("/api/v3/myTrades")));
  }

  @Test
  public void getTradeHistorySkipsPastAnUnqueryableMillisecondAndKeepsCollecting() throws IOException {
    // More than one full page of fills share the newest millisecond (150 at t=1000): the
    // inclusive cursor re-fetches the same first page and adds nothing. myTrades has no
    // trade-id cursor and caps pages at 100, so the remaining fills at t=1000 are not
    // queryable — the pager must skip that millisecond and keep collecting the older span
    // (t=1005, t=1010) instead of stopping at the repeat, which would abandon it.
    stubFor(
        get(urlPathEqualTo("/api/v3/myTrades"))
            .willReturn(aResponse().withBody(myTradesAtTime(1000L, 1, 100))));
    stubFor(
        get(urlPathEqualTo("/api/v3/myTrades"))
            .withQueryParam("startTime", equalTo("1000"))
            .willReturn(aResponse().withBody(myTradesAtTime(1000L, 1, 100))));
    stubFor(
        get(urlPathEqualTo("/api/v3/myTrades"))
            .withQueryParam("startTime", equalTo("1001"))
            .willReturn(aResponse().withBody(myTradesAtTime(1005L, 200, 100))));
    stubFor(
        get(urlPathEqualTo("/api/v3/myTrades"))
            .withQueryParam("startTime", equalTo("1005"))
            .willReturn(aResponse().withBody(myTradesAtTime(1005L, 200, 100))));
    stubFor(
        get(urlPathEqualTo("/api/v3/myTrades"))
            .withQueryParam("startTime", equalTo("1006"))
            .willReturn(aResponse().withBody(myTradesAtTime(1010L, 300, 50))));

    MexcV3TradeHistoryParams history = new MexcV3TradeHistoryParams();
    history.setCurrencyPair(CurrencyPair.BTC_USDT);

    UserTrades trades = tradeService().getTradeHistory(history);

    assertThat(trades.getUserTrades()).hasSize(250); // 100 + 100 + 50
    java.util.Set<String> ids =
        trades.getUserTrades().stream()
            .map(UserTrade::getId)
            .collect(java.util.stream.Collectors.toSet());
    assertThat(ids).hasSize(250);
    assertThat(ids).contains("1", "100", "200", "300", "349");
    // The fills beyond the first page of t=1000 (ids 101..150) are unqueryable by design.
    assertThat(ids).doesNotContain("101");
    verify(5, getRequestedFor(urlPathEqualTo("/api/v3/myTrades")));
    verify(
        1,
        getRequestedFor(urlPathEqualTo("/api/v3/myTrades"))
            .withQueryParam("startTime", equalTo("1001")));
  }

  /** Builds {@code count} myTrades rows with consecutive ids and times starting at {@code fromTime}. */
  private static String myTradesBody(int fromTime, int count) {
    StringBuilder body = new StringBuilder("[");
    for (int i = 0; i < count; i++) {
      if (i > 0) {
        body.append(',');
      }
      long t = fromTime + i;
      body.append(myTradeRow(t, t));
    }
    return body.append(']').toString();
  }

  /** Builds {@code count} myTrades rows with ids {@code startId..startId+count-1}, all at {@code time}. */
  private static String myTradesAtTime(long time, long startId, int count) {
    StringBuilder body = new StringBuilder("[");
    for (int i = 0; i < count; i++) {
      if (i > 0) {
        body.append(',');
      }
      body.append(myTradeRow(time, startId + i));
    }
    return body.append(']').toString();
  }

  private static String myTradeRow(long time, long id) {
    return new StringBuilder("{\"symbol\":\"BTCUSDT\",\"id\":")
        .append(id)
        .append(",\"orderId\":\"order-").append(id)
        .append("\",\"orderListId\":-1,\"price\":\"33198.31\",\"qty\":\"0.001\",")
        .append("\"quoteQty\":\"33.19831\",\"commission\":\"0.000001\",")
        .append("\"commissionAsset\":\"BTC\",\"time\":").append(time)
        .append(",\"isBuyer\":true,\"isMaker\":false,\"isBestMatch\":true,")
        .append("\"isSelfTrade\":false,\"clientOrderId\":\"ref-").append(id).append("\"}")
        .toString();
  }

  @Test
  public void getTradeHistoryDoesNotSpendTheLimitOnBoundaryDuplicates() throws IOException {
    // limit=150 with a full first page (100) and a second page of 50 that re-fetches the
    // boundary trade (id 1099, deduplicated): 49 new trades. Only those 49 count toward the
    // budget, so one unit remains and the next one-row request returns a new fill (1149).
    // Charging the duplicate against the caller's limit (raw page size 50) would leave the
    // budget at zero and wrongly stop at 149 trades.
    stubFor(
        get(urlPathEqualTo("/api/v3/myTrades")).willReturn(aResponse().withBody(myTradesBody(1000, 100))));
    stubFor(
        get(urlPathEqualTo("/api/v3/myTrades"))
            .withQueryParam("startTime", equalTo("1099"))
            .withQueryParam("limit", equalTo("50"))
            .willReturn(aResponse().withBody(myTradesBody(1099, 50))));
    stubFor(
        get(urlPathEqualTo("/api/v3/myTrades"))
            .withQueryParam("startTime", equalTo("1148"))
            .withQueryParam("limit", equalTo("1"))
            .willReturn(aResponse().withBody(myTradesAtTime(1149L, 1149, 1))));

    MexcV3TradeHistoryParams history = new MexcV3TradeHistoryParams();
    history.setCurrencyPair(CurrencyPair.BTC_USDT);
    history.setLimit(150);

    UserTrades trades = tradeService().getTradeHistory(history);

    assertThat(trades.getUserTrades()).hasSize(150);
    assertThat(trades.getUserTrades().get(149).getId()).isEqualTo("1149");
    verify(3, getRequestedFor(urlPathEqualTo("/api/v3/myTrades")));
  }

  @Test
  public void createTradeHistoryParamsIsCurrencyPairBased() throws IOException {
    assertThat(tradeService().createTradeHistoryParams())
        .isInstanceOf(TradeHistoryParamCurrencyPair.class)
        .isInstanceOf(TradeHistoryParamOrderId.class)
        .isInstanceOf(TradeHistoryParamsTimeSpan.class);
  }

  @Test
  public void placeOrderTestParsesProviderEcho() throws IOException {
    stubFor(
        post(urlPathEqualTo("/api/v3/order/test"))
            .willReturn(
                aResponse()
                    .withBody(
                        "{\"symbol\":\"BTCUSDT\",\"orderId\":\"42\",\"orderListId\":-1,"
                            + "\"price\":\"60000.00\",\"origQty\":\"0.001\",\"type\":\"LIMIT\","
                            + "\"side\":\"BUY\",\"transactTime\":1645539742000}")));

    org.knowm.xchange.mexc.v3.dto.trade.MexcV3OrderResponse echo =
        tradeService()
            .placeOrderTest(
                CurrencyPair.BTC_USDT,
                MexcV3OrderSide.BUY,
                MexcV3OrderType.LIMIT,
                "0.001",
                "60000.00");

    assertThat(echo.getOrderId()).isEqualTo("42");
    assertThat(echo.getSymbol()).isEqualTo("BTCUSDT");
    verify(
        postRequestedFor(urlPathEqualTo("/api/v3/order/test"))
            .withQueryParam("symbol", com.github.tomakehurst.wiremock.client.WireMock.equalTo("BTCUSDT"))
            .withQueryParam("type", com.github.tomakehurst.wiremock.client.WireMock.equalTo("LIMIT")));
  }

  /** Adapter bundling the cancel-param interfaces the service recognizes. */
  private static final class CancelParams
      implements CancelOrderByIdParams, CancelOrderByCurrencyPair, CancelOrderByUserReferenceParams {
    private final String orderId;
    private final CurrencyPair pair;
    private final String userReference;

    CancelParams(String orderId, CurrencyPair pair, String userReference) {
      this.orderId = orderId;
      this.pair = pair;
      this.userReference = userReference;
    }

    @Override
    public String getOrderId() {
      return orderId;
    }

    @Override
    public CurrencyPair getCurrencyPair() {
      return pair;
    }

    @Override
    public String getUserReference() {
      return userReference;
    }
  }
}
