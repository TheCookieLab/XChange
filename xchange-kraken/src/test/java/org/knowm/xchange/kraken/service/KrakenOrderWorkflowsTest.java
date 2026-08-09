package org.knowm.xchange.kraken.service;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.kraken.KrakenExchangeWiremock;
import org.knowm.xchange.kraken.dto.trade.KrakenAddOrderBatchResponse;
import org.knowm.xchange.kraken.dto.trade.KrakenAmendOrderResponse;
import org.knowm.xchange.kraken.dto.trade.KrakenCancelAllOrdersAfterResponse;
import org.knowm.xchange.kraken.dto.trade.KrakenStandardOrder;
import org.knowm.xchange.kraken.dto.trade.KrakenType;
import org.knowm.xchange.kraken.service.KrakenException.RetryClass;

/** Spot order workflows: atomic amend, batch placement, dead-man timer, no blind replay. */
public class KrakenOrderWorkflowsTest extends KrakenExchangeWiremock {

  private KrakenTradeServiceRaw raw;

  @BeforeAll
  public static void configureWireMockClient() {
    com.github.tomakehurst.wiremock.client.WireMock.configureFor(
        "localhost", wireMockServer.port());
  }

  @BeforeEach
  public void setUp() {
    com.github.tomakehurst.wiremock.client.WireMock.reset();
    raw = (KrakenTradeServiceRaw) exchange.getTradeService();
  }

  private void stubEndpoint(String path, String bodyFileName) {
    stubFor(
        post(urlEqualTo(path))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBodyFile(bodyFileName)));
  }

  @Test
  void amend_order_sends_identifiers_and_parses_response() throws IOException {
    stubEndpoint("/0/private/AmendOrder", "0_private_amendorder-ok.json");

    KrakenAmendOrderResponse response =
        raw.amendKrakenOrder(
            "TXID1-KRAKEN-ORDER", null, new BigDecimal("0.5"), "50000.0", null, null, null);

    assertThat(response.getAmendId()).isEqualTo("AA12-BB34-CC56");
    assertThat(response.getOrderId()).isEqualTo("TXID1-KRAKEN-ORDER");
    assertThat(response.getStatus()).isEqualTo("ok");
    verify(
        postRequestedFor(urlEqualTo("/0/private/AmendOrder"))
            .withRequestBody(containing("order_id=TXID1-KRAKEN-ORDER"))
            .withRequestBody(containing("order_qty=0.5"))
            .withRequestBody(containing("limit_price=50000.0")));
  }

  @Test
  void amend_order_by_client_id() throws IOException {
    stubEndpoint("/0/private/AmendOrder", "0_private_amendorder-ok.json");

    raw.amendKrakenOrder(
        null, "my-client-id-1", new BigDecimal("0.5"), "50000.0", null, null, null);

    verify(
        postRequestedFor(urlEqualTo("/0/private/AmendOrder"))
            .withRequestBody(containing("cl_ord_id=my-client-id-1")));
  }

  @Test
  void amend_order_requires_an_identity() {
    assertThatExceptionOfType(ExchangeException.class)
        .isThrownBy(
            () ->
                raw.amendKrakenOrder(
                    null, null, new BigDecimal("0.5"), "50000.0", null, null, null))
        .withMessageContaining("order_id or cl_ord_id");
    verify(0, postRequestedFor(urlEqualTo("/0/private/AmendOrder")));
  }

  @Test
  void amend_order_error_is_structured_and_redacted() {
    stubEndpoint("/0/private/AmendOrder", "0_private_amendorder-error.json");

    KrakenException exception =
        org.assertj.core.api.Assertions.catchThrowableOfType(
            () ->
                raw.amendKrakenOrder("TXID1", null, new BigDecimal("0.5"), null, null, null, null),
            KrakenException.class);

    assertThat(exception).isNotNull();
    assertThat(exception.getOperation()).isEqualTo("amendKrakenOrder");
    assertThat(exception.getRetryClass()).isEqualTo(RetryClass.NON_RETRYABLE);
    assertThat(exception.getErrors()).containsExactly("EOrder:Unknown order");
  }

  @Test
  void cancel_all_orders_after_sends_timeout_and_parses_response() throws IOException {
    stubEndpoint("/0/private/CancelAllOrdersAfter", "0_private_cancelallordersafter-ok.json");

    KrakenCancelAllOrdersAfterResponse response = raw.cancelAllKrakenOrdersAfter(60);

    assertThat(response.getCurrentTime()).isEqualTo("2022-12-25T09:30:59.123456Z");
    assertThat(response.getTriggerTime()).isEqualTo("2022-12-25T09:31:59.123456Z");
    verify(
        postRequestedFor(urlEqualTo("/0/private/CancelAllOrdersAfter"))
            .withRequestBody(containing("timeout=60")));
  }

  @Test
  void batch_order_sends_json_and_parses_per_order_txids() throws IOException {
    stubEndpoint("/0/private/AddOrderBatch", "0_private_addorderbatch-ok.json");

    List<KrakenStandardOrder> orders =
        List.of(
            KrakenStandardOrder.getLimitOrderBuilder(
                    CurrencyPair.BTC_USD, KrakenType.BUY, "28300.0", new BigDecimal("0.803"))
                .buildOrder(),
            KrakenStandardOrder.getLimitOrderBuilder(
                    CurrencyPair.BTC_USD, KrakenType.SELL, "36000.0", new BigDecimal("0.105"))
                .buildOrder());

    KrakenAddOrderBatchResponse response = raw.placeKrakenOrdersBatch(orders);

    assertThat(response.getOrders()).hasSize(2);
    assertThat(response.getOrders().get(0).getTransactionId()).isEqualTo("O5OR23-ADFAD-Y2G61C");
    assertThat(response.getOrders().get(1).getTransactionId()).isEqualTo("9K6KFS-5H3PL-XBRC7A");
    assertThat(response.getOrders().get(0).getOrderDescription())
        .isEqualTo("buy 0.80300000 XBTUSD @ limit 28300.0");
    verify(
        postRequestedFor(urlEqualTo("/0/private/AddOrderBatch"))
            .withRequestBody(containing("orders="))
            .withRequestBody(containing("%22pair%22"))
            .withRequestBody(containing("%22ordertype%22")));
  }

  @Test
  void batch_order_requires_at_least_one_order() {
    assertThatExceptionOfType(ExchangeException.class)
        .isThrownBy(() -> raw.placeKrakenOrdersBatch(List.of()))
        .withMessageContaining("at least one order");
  }

  @Test
  void failed_placement_is_not_replayed() {
    stubEndpoint("/0/private/AddOrder", "0_private_addorder-error.json");

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                raw.placeKrakenLimitOrder(
                    new org.knowm.xchange.dto.trade.LimitOrder.Builder(
                            org.knowm.xchange.dto.Order.OrderType.BID, CurrencyPair.BTC_USD)
                        .limitPrice(new BigDecimal("50000.0"))
                        .originalAmount(new BigDecimal("0.002"))
                        .build()))
        .isInstanceOf(org.knowm.xchange.exceptions.FundsExceededException.class);

    // the failed placement must never be replayed automatically
    verify(1, postRequestedFor(urlEqualTo("/0/private/AddOrder")));
  }

  @Test
  void placement_with_user_reference_sends_userref() throws IOException {
    stubEndpoint(
        "/0/private/AddOrder", "0_private_addorder-69864b03-6284-40f6-9928-578407607328.json");

    raw.placeKrakenLimitOrder(
        new org.knowm.xchange.dto.trade.LimitOrder.Builder(
                org.knowm.xchange.dto.Order.OrderType.BID, CurrencyPair.BTC_USD)
            .userReference("MY-USER-REF")
            .limitPrice(new BigDecimal("50000.0"))
            .originalAmount(new BigDecimal("0.002"))
            .build());

    verify(
        postRequestedFor(urlEqualTo("/0/private/AddOrder"))
            .withRequestBody(containing("userref=MY-USER-REF")));
  }

  @Test
  void placement_with_client_order_id_param_sends_cl_ord_id() throws IOException {
    stubEndpoint(
        "/0/private/AddOrder", "0_private_addorder-69864b03-6284-40f6-9928-578407607328.json");

    raw.placeKrakenLimitOrder(
        new ClientIdLimitOrder(new BigDecimal("0.002"), new BigDecimal("50000.0"), "MY-CL-ORD-ID"));

    verify(
        postRequestedFor(urlEqualTo("/0/private/AddOrder"))
            .withRequestBody(containing("cl_ord_id=MY-CL-ORD-ID")));
  }

  /** LimitOrder that also carries a {@code cl_ord_id} via the PlaceOrderParams contract. */
  private static final class ClientIdLimitOrder extends org.knowm.xchange.dto.trade.LimitOrder
      implements org.knowm.xchange.service.trade.params.orders.PlaceOrderParams {

    private final String clientOrderId;

    ClientIdLimitOrder(BigDecimal amount, BigDecimal price, String clientOrderId) {
      super(
          org.knowm.xchange.dto.Order.OrderType.BID,
          amount,
          CurrencyPair.BTC_USD,
          null,
          null,
          price,
          null,
          null,
          null,
          null);
      this.clientOrderId = clientOrderId;
    }

    @Override
    public <T> T getOrderParam(String param, Class<T> type) {
      if (org.knowm.xchange.service.trade.params.orders.PlaceOrderKnownParams.CLIENT_ORDER_ID
          .equals(param)) {
        return type.cast(clientOrderId);
      }
      return null;
    }
  }
}
