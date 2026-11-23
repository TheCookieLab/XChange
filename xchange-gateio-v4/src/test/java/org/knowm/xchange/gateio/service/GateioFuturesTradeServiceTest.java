package org.knowm.xchange.gateio.service;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.derivative.FuturesContract;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.meta.ExchangeMetaData;
import org.knowm.xchange.dto.meta.InstrumentMetaData;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.gateio.GateioExchange;
import org.knowm.xchange.gateio.GateioExchangeWiremock;
import org.knowm.xchange.gateio.dto.GateioExchangeType;
import org.knowm.xchange.gateio.dto.trade.GateioCancelOrderParams;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.service.trade.params.DefaultCancelOrderByInstrumentAndIdParams;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

class GateioFuturesTradeServiceTest extends GateioExchangeWiremock {

  static GateioTradeService gateioTradeService;
  static Instrument btcUsdtPerp = new FuturesContract("BTC/USDT/PERP");

  @BeforeAll
  static void setup() {
    ExchangeSpecification exSpec = exchange.getExchangeSpecification();
    exSpec.setExchangeSpecificParametersItem(GateioExchange.EXCHANGE_TYPE, GateioExchangeType.FUTURES);

    // Use a custom exchange class to allow setting metadata
    TestGateioExchange testExchange = new TestGateioExchange();
    testExchange.applySpecification(exSpec);
    exchange = testExchange;
    gateioTradeService = (GateioTradeService) exchange.getTradeService();

    // Mock metadata for contract value
    Map<Instrument, InstrumentMetaData> instruments = new HashMap<>();
    instruments.put(btcUsdtPerp, InstrumentMetaData.builder()
        .contractValue(new BigDecimal("0.0001"))
        .build());
    ((TestGateioExchange) exchange).setExchangeMetaData(new ExchangeMetaData(instruments, null, null, null, null));
  }


  @Test
  void place_futures_limit_order() throws Exception {
    LimitOrder limitOrder = new LimitOrder.Builder(OrderType.BID, btcUsdtPerp)
        .limitPrice(new BigDecimal("50000"))
        .originalAmount(BigDecimal.ONE)
        .userReference("t-futures-limit-order")
        .build();

    String orderId = gateioTradeService.placeLimitOrder(limitOrder);
    assertThat(orderId).isEqualTo("12345678");
  }


  @Test
  void place_futures_market_order() throws Exception {
    MarketOrder marketOrder = new MarketOrder.Builder(OrderType.ASK, btcUsdtPerp)
        .originalAmount(BigDecimal.ONE)
        .userReference("t-futures-market-order")
        .build();

    String orderId = gateioTradeService.placeMarketOrder(marketOrder);
    assertThat(orderId).isEqualTo("87654321");
  }

  @Test
  void cancel_futures_order() throws Exception {
    boolean cancelled = gateioTradeService.cancelOrder(
        new DefaultCancelOrderByInstrumentAndIdParams(btcUsdtPerp, "15675394"));

    assertThat(cancelled).isTrue();
  }

  @Test
  void cancelFuturesOrderByClientReference() throws Exception {
    wireMockServer()
        .stubFor(
            delete(urlEqualTo("/api/v4/futures/usdt/orders/t-client-1"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBodyFile("api_v4_futures_orders-valid-cancel-order.json")));

    assertThat(
            gateioTradeService.cancelOrder(
                new GateioCancelOrderParams(null, btcUsdtPerp, "client-1")))
        .isTrue();
  }

  @Test
  void order_query_dispatches_spot_and_futures_individually() throws Exception {
    wireMockServer()
        .stubFor(
            com.github.tomakehurst.wiremock.client.WireMock.get(
                    com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo(
                        "/api/v4/futures/usdt/orders/12345678"))
                .willReturn(
                    com.github.tomakehurst.wiremock.client.WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBodyFile("api_v4_futures_order_limit.json")));
    java.util.Collection<org.knowm.xchange.dto.Order> orders =
        gateioTradeService.getOrder(
            new org.knowm.xchange.service.trade.params.orders.DefaultQueryOrderParamInstrument(
                org.knowm.xchange.currency.CurrencyPair.BTC_USDT, "342251629898"),
            new org.knowm.xchange.service.trade.params.orders.DefaultQueryOrderParamInstrument(
                btcUsdtPerp, "12345678"));
    assertThat(orders)
        .extracting(org.knowm.xchange.dto.Order::getId)
        .containsExactly("342251629898", "12345678");
  }

  @Test
  void change_futures_order() throws Exception {
    LimitOrder limitOrder = new LimitOrder.Builder(OrderType.BID, btcUsdtPerp)
        .id("15675394")
        .originalAmount(BigDecimal.ONE)
        .limitPrice(new BigDecimal("50000"))
        .build();

    String orderId = gateioTradeService.changeOrder(limitOrder);
    assertThat(orderId).isEqualTo("15675394");
  }

  private static class TestGateioExchange extends GateioExchange {
    public void setExchangeMetaData(ExchangeMetaData exchangeMetaData) {
      this.exchangeMetaData = exchangeMetaData;
    }
  }

}
