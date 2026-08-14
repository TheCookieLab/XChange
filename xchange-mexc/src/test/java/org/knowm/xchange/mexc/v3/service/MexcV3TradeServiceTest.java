package org.knowm.xchange.mexc.v3.service;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
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
import org.knowm.xchange.dto.trade.UserTrades;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.exceptions.OrderAmountUnderMinimumException;
import org.knowm.xchange.mexc.v3.BaseMexcV3WiremockTest;
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
  public void placeMarketOrderBuyUsesQuoteOrderQtyAndNoQuantity() throws IOException {
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
            .build();

    assertThat(tradeService().placeMarketOrder(order)).isEqualTo("1");
    verify(
        postRequestedFor(urlPathEqualTo("/api/v3/order"))
            .withQueryParam("quoteOrderQty", com.github.tomakehurst.wiremock.client.WireMock.equalTo("60.00"))
            .withoutQueryParam("quantity"));
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
