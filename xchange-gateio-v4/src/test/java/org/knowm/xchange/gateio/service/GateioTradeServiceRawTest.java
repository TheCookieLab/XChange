package org.knowm.xchange.gateio.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.gateio.GateioExchangeWiremock;
import org.knowm.xchange.gateio.dto.GateioContinuation;
import org.knowm.xchange.gateio.dto.GateioIterationStop;
import org.knowm.xchange.gateio.dto.GateioPage;
import org.knowm.xchange.gateio.dto.GateioPageCursor;
import org.knowm.xchange.gateio.dto.account.GateioAmendOrderRequest;
import org.knowm.xchange.gateio.dto.account.GateioBatchOrderResult;
import org.knowm.xchange.gateio.dto.account.GateioCancelBatchRequest;
import org.knowm.xchange.gateio.dto.account.GateioCancelOrderResult;
import org.knowm.xchange.gateio.dto.account.GateioCountdownCancelRequest;
import org.knowm.xchange.gateio.dto.account.GateioCountdownCancelResult;
import org.knowm.xchange.gateio.dto.account.GateioOrder;
import org.knowm.xchange.gateio.dto.trade.GateioUserTradeRaw;
import org.knowm.xchange.gateio.dto.trade.Role;
import org.knowm.xchange.gateio.service.params.GateioTradeHistoryParams;
import org.knowm.xchange.service.trade.params.DefaultTradeHistoryParamCurrencyPair;

class GateioTradeServiceRawTest extends GateioExchangeWiremock {

  GateioTradeServiceRaw gateioTradeServiceRaw = (GateioTradeServiceRaw) exchange.getTradeService();

  GateioOrder sampleMarketOrder =
      GateioOrder.builder()
          .id("342251629898")
          .currencyPair(CurrencyPair.BTC_USDT)
          .clientOrderId("t-valid-market-buy-order")
          .amendText("-")
          .type("market")
          .account("spot")
          .side(OrderType.BID)
          .timeInForce("ioc")
          .amount(BigDecimal.valueOf(20))
          .createdAt(Instant.parse("2023-06-03T22:07:38.451Z"))
          .updatedAt(Instant.parse("2023-06-03T22:07:38.451Z"))
          .status("closed")
          .icebergAmount(BigDecimal.ZERO)
          .amountLeftToFill(new BigDecimal("1.07319"))
          .filledTotalQuote(new BigDecimal("18.92681"))
          .avgDealPrice(new BigDecimal("27038.3"))
          .fee(new BigDecimal("0.0000014"))
          .price(BigDecimal.ZERO)
          .feeCurrency(Currency.BTC)
          .pointFee(BigDecimal.ZERO)
          .gtFee(BigDecimal.ZERO)
          .gtMakerFee(BigDecimal.ZERO)
          .gtTakerFee(BigDecimal.ZERO)
          .rebatedFee(BigDecimal.ZERO)
          .gtDiscount(false)
          .rebatedFeeCurrency(Currency.USDT)
          .finishAs("filled")
          .build();

  @Test
  void valid_market_buy_order() throws IOException {
    GateioOrder gateioOrder =
        GateioOrder.builder()
            .currencyPair(CurrencyPair.BTC_USDT)
            .clientOrderId("t-valid-market-buy-order")
            .type("market")
            .account("spot")
            .side(OrderType.BID)
            .timeInForce("ioc")
            .amount(BigDecimal.valueOf(20))
            .build();

    GateioOrder actualResponse = gateioTradeServiceRaw.createOrder(gateioOrder);
    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(sampleMarketOrder);
  }

  @Test
  void valid_market_sell_order() throws IOException {
    GateioOrder gateioOrder =
        GateioOrder.builder()
            .currencyPair(CurrencyPair.BTC_USDT)
            .clientOrderId("t-valid-market-sell-order")
            .type("market")
            .account("spot")
            .side(OrderType.ASK)
            .timeInForce("ioc")
            .amount(new BigDecimal("0.0007"))
            .build();

    GateioOrder actualResponse = gateioTradeServiceRaw.createOrder(gateioOrder);

    GateioOrder expectedResponse =
        GateioOrder.builder()
            .id("342260949533")
            .currencyPair(CurrencyPair.BTC_USDT)
            .clientOrderId("t-valid-market-sell-order")
            .amendText("-")
            .type("market")
            .account("spot")
            .side(OrderType.ASK)
            .timeInForce("ioc")
            .amount(new BigDecimal("0.0007"))
            .createdAt(Instant.parse("2023-06-03T22:33:21.743Z"))
            .updatedAt(Instant.parse("2023-06-03T22:33:21.743Z"))
            .status("closed")
            .icebergAmount(BigDecimal.ZERO)
            .amountLeftToFill(BigDecimal.ZERO)
            .filledTotalQuote(new BigDecimal("18.94382"))
            .avgDealPrice(new BigDecimal("27062.6"))
            .fee(new BigDecimal("0.03788764"))
            .price(BigDecimal.ZERO)
            .feeCurrency(Currency.USDT)
            .pointFee(BigDecimal.ZERO)
            .gtFee(BigDecimal.ZERO)
            .gtMakerFee(BigDecimal.ZERO)
            .gtTakerFee(BigDecimal.ZERO)
            .rebatedFee(BigDecimal.ZERO)
            .gtDiscount(false)
            .rebatedFeeCurrency(Currency.BTC)
            .finishAs("filled")
            .build();

    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  @Test
  void order_details() throws IOException {
    GateioOrder actualResponse =
        gateioTradeServiceRaw.getOrder("342251629898", CurrencyPair.BTC_USDT);

    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(sampleMarketOrder);
  }

  @Test
  void getUserTrades_default_bounded() throws IOException {
    List<GateioUserTradeRaw> actual =
        gateioTradeServiceRaw.getGateioUserTrades(
            new DefaultTradeHistoryParamCurrencyPair(CurrencyPair.BTC_USDT));

    // The default ceiling (1000) is reached on the first full page, so the
    // convenience accessor stops at the ceiling with a resumable cursor.
    assertThat(actual).hasSize(1000);
    assertThat(actual.get(0).getId()).isEqualTo(6068816979L);
    assertThat(actual.get(0).getCurrencyPair()).isEqualTo(CurrencyPair.BTC_USDT);
    assertThat(actual.get(0).getSide()).isEqualTo(OrderType.BID);
    assertThat(actual.get(0).getRole()).isEqualTo(Role.MAKER);

    GateioContinuation<GateioUserTradeRaw> capped =
        gateioTradeServiceRaw.getGateioUserTradesBounded(
            new DefaultTradeHistoryParamCurrencyPair(CurrencyPair.BTC_USDT),
            GateioTradeServiceRaw.DEFAULT_HISTORY_CEILING);
    assertThat(capped.getStop()).isEqualTo(GateioIterationStop.MAX_RESULTS);
    assertThat(capped.getItems()).hasSize(1000);
    assertThat(capped.getNextCursor().getPage()).isEqualTo(2);
  }

  @Test
  void getUserTrades_page_and_resume() throws IOException {
    GateioTradeHistoryParams params =
        GateioTradeHistoryParams.builder()
            .currencyPair(CurrencyPair.BTC_USDT)
            .pageLength(1000)
            .pageNumber(1)
            .build();

    GateioPage<GateioUserTradeRaw> page =
        gateioTradeServiceRaw.getGateioUserTradesPage(null, params);

    assertThat(page.getItems()).hasSize(1000);
    assertThat(page.getItems().get(0).getId()).isEqualTo(6068816979L);
    assertThat(page.hasNext()).isTrue();
    assertThat(page.getNextCursor().getPage()).isEqualTo(2);

    GateioPage<GateioUserTradeRaw> secondPage =
        gateioTradeServiceRaw.getGateioUserTradesPage(page.getNextCursor(), params);
    assertThat(secondPage.getItems()).hasSize(1);
    assertThat(secondPage.getItems().get(0).getId()).isEqualTo(6068789000L);
    assertThat(secondPage.hasNext()).isFalse();
  }

  @Test
  void getUserTrades_bounded_maxResults_resumable() throws IOException {
    GateioTradeHistoryParams params =
        GateioTradeHistoryParams.builder().currencyPair(CurrencyPair.BTC_USDT).build();

    GateioContinuation<GateioUserTradeRaw> bounded =
        gateioTradeServiceRaw.getGateioUserTradesBounded(params, 1);

    assertThat(bounded.getStop()).isEqualTo(GateioIterationStop.MAX_RESULTS);
    assertThat(bounded.getItems()).hasSize(1000);
    assertThat(bounded.getNextCursor().isPageBased()).isTrue();
    assertThat(bounded.getNextCursor().getPage()).isEqualTo(2);

    GateioPage<GateioUserTradeRaw> resumed =
        gateioTradeServiceRaw.getGateioUserTradesPage(bounded.getNextCursor(), params);
    assertThat(resumed.getItems()).hasSize(1);
    assertThat(resumed.getItems().get(0).getId()).isEqualTo(6068789000L);
    assertThat(resumed.hasNext()).isFalse();
  }

  @Test
  void getOpenOrdersPage_pagination() throws IOException {
    GateioPage<GateioOrder> firstPage = gateioTradeServiceRaw.getOpenOrdersPage(null, 2);

    assertThat(firstPage.getItems()).hasSize(2);
    assertThat(firstPage.hasNext()).isTrue();
    assertThat(firstPage.getNextCursor().getPage()).isEqualTo(2);

    GateioPage<GateioOrder> secondPage =
        gateioTradeServiceRaw.getOpenOrdersPage(firstPage.getNextCursor(), 2);
    assertThat(secondPage.getItems()).hasSize(1);
    assertThat(secondPage.getItems().get(0).getId()).isEqualTo("745504484394");
    assertThat(secondPage.hasNext()).isFalse();
  }

  @Test
  void getOpenOrders_bounded_completed() throws IOException {
    GateioContinuation<GateioOrder> bounded = gateioTradeServiceRaw.getOpenOrdersBounded(2, 3);

    assertThat(bounded.getStop()).isEqualTo(GateioIterationStop.COMPLETED);
    assertThat(bounded.getItems()).hasSize(3);
    assertThat(bounded.getNextCursor()).isNull();
    assertThat(bounded.getItems().get(0).getId()).isEqualTo("745504484392");
    assertThat(bounded.getItems().get(2).getId()).isEqualTo("745504484394");
  }

  @Test
  void getOpenOrders_bounded_maxResults() throws IOException {
    GateioContinuation<GateioOrder> bounded = gateioTradeServiceRaw.getOpenOrdersBounded(2, 1);

    assertThat(bounded.getStop()).isEqualTo(GateioIterationStop.MAX_RESULTS);
    assertThat(bounded.getItems()).hasSize(2);
    assertThat(bounded.getNextCursor().getPage()).isEqualTo(2);
  }
  
  @Test
  void getOpenOrdersPage_flattensProviderGroups() throws IOException {
    GateioPage<GateioOrder> page = gateioTradeServiceRaw.getOpenOrdersPage(null, 99);

    assertThat(page.getItems()).hasSize(1);
    assertThat(page.getItems().get(0).getId()).isEqualTo("745504484392");
  }

  @Test
  void amendOrder_valid() throws IOException {
    GateioOrder actual =
        gateioTradeServiceRaw.amendOrder(
            "1",
            CurrencyPair.BTC_USDT,
            GateioAmendOrderRequest.builder().amount(new BigDecimal("0.0002")).build());

    assertThat(actual.getId()).isEqualTo("1");
    assertThat(actual.getAmount()).isEqualByComparingTo("0.0002");
    assertThat(actual.getPrice()).isEqualByComparingTo("80000.5");
    assertThat(actual.getStatus()).isEqualTo("open");
  }

  @Test
  void cancelAllOrders_valid() throws IOException {
    List<GateioOrder> actual = gateioTradeServiceRaw.cancelAllOrders(CurrencyPair.BTC_USDT);

    assertThat(actual).hasSize(2);
    assertThat(actual.get(0).getId()).isEqualTo("376835979523");
    assertThat(actual.get(0).getStatus()).isEqualTo("cancelled");
    assertThat(actual.get(1).getId()).isEqualTo("376835979524");
  }

  @Test
  void createBatchOrders_valid() throws IOException {
    List<GateioBatchOrderResult> actual =
        gateioTradeServiceRaw.createBatchOrders(List.of(sampleMarketOrder));

    assertThat(actual).hasSize(2);
    assertThat(actual.get(0).getSucceeded()).isTrue();
    assertThat(actual.get(0).getId()).isEqualTo("745504484399");
    assertThat(actual.get(0).getOrder().getId()).isEqualTo("745504484392");
    assertThat(actual.get(1).getSucceeded()).isFalse();
    assertThat(actual.get(1).getLabel()).isEqualTo("ORDER_NOT_FOUND");
    assertThat(actual.get(1).getMessage()).isEqualTo("order not found");
  }

  @Test
  void cancelBatchOrders_valid() throws IOException {
    List<GateioCancelOrderResult> actual =
        gateioTradeServiceRaw.cancelBatchOrders(
            List.of(
                GateioCancelBatchRequest.builder()
                    .currencyPair(CurrencyPair.BTC_USDT)
                    .orderId("376835979523")
                    .build(),
                GateioCancelBatchRequest.builder()
                    .currencyPair(CurrencyPair.BTC_USDT)
                    .orderId("376835979524")
                    .build()));

    assertThat(actual).hasSize(2);
    assertThat(actual.get(0).getSucceeded()).isTrue();
    assertThat(actual.get(0).getId()).isEqualTo("376835979523");
    assertThat(actual.get(1).getSucceeded()).isFalse();
    assertThat(actual.get(1).getLabel()).isEqualTo("ORDER_NOT_FOUND");
  }

  @Test
  void countdownCancelAll_valid() throws IOException {
    GateioCountdownCancelResult actual =
        gateioTradeServiceRaw.countdownCancelAll(
            GateioCountdownCancelRequest.builder()
                .timeout(300)
                .currencyPair(CurrencyPair.BTC_USDT)
                .build());

    assertThat(actual.getTriggered()).isTrue();
    assertThat(actual.getOrderIds()).containsExactly("745504484392", "745504484393");
  }
}
