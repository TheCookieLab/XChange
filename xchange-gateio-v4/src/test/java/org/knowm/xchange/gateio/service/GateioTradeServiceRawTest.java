package org.knowm.xchange.gateio.service;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.notMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

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
import org.knowm.xchange.gateio.dto.account.GateioTriggerTime;
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
  void getUserTrades_no_page_rejects_ceiling_overflow() throws IOException {
    // The no-page convenience accessor refuses to silently truncate history at
    // the default ceiling; it must direct callers to bounded pagination.
    assertThatExceptionOfType(IllegalStateException.class)
        .isThrownBy(
            () ->
                gateioTradeServiceRaw.getGateioUserTrades(
                    new DefaultTradeHistoryParamCurrencyPair(CurrencyPair.BTC_USDT)))
        .withMessageContaining("use bounded pagination");

    // The bounded accessor exposes the ceiling stop with a resumable cursor.
    GateioContinuation<GateioUserTradeRaw> capped =
        gateioTradeServiceRaw.getGateioUserTradesBounded(
            new DefaultTradeHistoryParamCurrencyPair(CurrencyPair.BTC_USDT),
            GateioTradeServiceRaw.DEFAULT_HISTORY_CEILING);
    assertThat(capped.getStop()).isEqualTo(GateioIterationStop.MAX_RESULTS);
    assertThat(capped.getItems()).hasSize(1000);
    assertThat(capped.getNextCursor().getPage()).isEqualTo(2);
    assertThat(capped.getItems().get(0).getId()).isEqualTo(6068816979L);
    assertThat(capped.getItems().get(0).getCurrencyPair()).isEqualTo(CurrencyPair.BTC_USDT);
    assertThat(capped.getItems().get(0).getSide()).isEqualTo(OrderType.BID);
    assertThat(capped.getItems().get(0).getRole()).isEqualTo(Role.MAKER);
  }

  @Test
  void getUserTrades_no_page_exactCeiling_returnsAll() throws IOException {
    // a history that ends exactly at the ceiling must not be rejected: the
    // confirmation page fetch proves there is nothing beyond page 1
    List<GateioUserTradeRaw> all =
        gateioTradeServiceRaw.getGateioUserTrades(
            new DefaultTradeHistoryParamCurrencyPair(CurrencyPair.ETH_USDT));

    assertThat(all).hasSize(1000);
    assertThat(all.get(0).getId()).isEqualTo(6068816979L);
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
    // the ceiling is a hard bound: never more than maxResults, even on a full page
    assertThat(bounded.getItems()).hasSize(1);
    assertThat(bounded.getItems().get(0).getId()).isEqualTo(6068816979L);
    // the cut page is resumable: same page, skip advanced past the consumed item
    assertThat(bounded.getNextCursor()).isEqualTo(GateioPageCursor.page(1).withSkip(1));

    // resume re-fetches the cut page and drops the consumed prefix
    GateioPage<GateioUserTradeRaw> resumed =
        gateioTradeServiceRaw.getGateioUserTradesPage(bounded.getNextCursor(), params);
    assertThat(resumed.getItems()).hasSize(999);
    assertThat(resumed.getItems().get(0).getId()).isEqualTo(6068816978L);
    assertThat(resumed.hasNext()).isTrue();

    GateioPage<GateioUserTradeRaw> second =
        gateioTradeServiceRaw.getGateioUserTradesPage(resumed.getNextCursor(), params);
    assertThat(second.getItems()).hasSize(1);
    assertThat(second.getItems().get(0).getId()).isEqualTo(6068789000L);
    assertThat(second.hasNext()).isFalse();
  }

  @Test
  void getUserTrades_bounded_largeCeiling_keepsConstantPageSize() throws IOException {
    GateioTradeHistoryParams params =
        GateioTradeHistoryParams.builder().currencyPair(CurrencyPair.BTC_USDT).build();

    // a ceiling above the page size must not shrink the limit mid-iteration:
    // page-number addressing is relative to the limit, so a variable limit
    // would duplicate records and skip history
    GateioContinuation<GateioUserTradeRaw> bounded =
        gateioTradeServiceRaw.getGateioUserTradesBounded(params, 1500);

    assertThat(bounded.getStop()).isEqualTo(GateioIterationStop.COMPLETED);
    assertThat(bounded.getItems()).hasSize(1001);
    assertThat(bounded.getItems().get(1000).getId()).isEqualTo(6068789000L);

    wireMockServer()
        .verify(
            1,
            getRequestedFor(urlPathEqualTo("/api/v4/spot/my_trades"))
                .withQueryParam("currency_pair", equalTo("BTC_USDT"))
                .withQueryParam("limit", equalTo("1000"))
                .withQueryParam("page", equalTo("1")));
    wireMockServer()
        .verify(
            1,
            getRequestedFor(urlPathEqualTo("/api/v4/spot/my_trades"))
                .withQueryParam("currency_pair", equalTo("BTC_USDT"))
                .withQueryParam("limit", equalTo("1000"))
                .withQueryParam("page", equalTo("2")));
    wireMockServer()
        .verify(
            0,
            getRequestedFor(urlPathEqualTo("/api/v4/spot/my_trades"))
                .withQueryParam("limit", notMatching("1000")));
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
    // the ceiling is a hard bound: the first page is cut at maxResults
    assertThat(bounded.getItems()).hasSize(1);
    assertThat(bounded.getItems().get(0).getId()).isEqualTo("745504484392");
    // the second order of the cut page is not lost: resume drops the consumed prefix
    assertThat(bounded.getNextCursor()).isEqualTo(GateioPageCursor.page(1).withSkip(1));

    GateioPage<GateioOrder> resumed =
        gateioTradeServiceRaw.getOpenOrdersPage(bounded.getNextCursor(), 2);
    assertThat(resumed.getItems()).hasSize(1);
    assertThat(resumed.getItems().get(0).getId()).isEqualTo("745504484393");
    assertThat(resumed.getNextCursor()).isEqualTo(GateioPageCursor.page(2));
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
    // Gate returns each element flat: the order fields sit at the response root
    assertThat(actual.get(0).getId()).isEqualTo("745504484392");
    assertThat(actual.get(0).getCurrencyPair()).isEqualTo(CurrencyPair.BTC_USDT);
    assertThat(actual.get(0).getStatus()).isEqualTo("open");
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
    GateioTriggerTime actual =
        gateioTradeServiceRaw.countdownCancelAll(
            GateioCountdownCancelRequest.builder()
                .timeout(300)
                .currencyPair(CurrencyPair.BTC_USDT)
                .build());

    assertThat(actual.getTriggerTime()).isEqualTo(1691781600000L);
  }
}
