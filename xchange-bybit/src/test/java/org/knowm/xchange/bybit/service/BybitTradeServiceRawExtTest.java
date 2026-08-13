package org.knowm.xchange.bybit.service;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import jakarta.ws.rs.core.Response.Status;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.Test;
import org.knowm.xchange.bybit.dto.BybitCategory;
import org.knowm.xchange.bybit.dto.BybitResult;
import org.knowm.xchange.bybit.dto.trade.BybitOrderStatus;
import org.knowm.xchange.bybit.dto.trade.BybitPreCheckPayload;
import org.knowm.xchange.bybit.dto.trade.BybitPreCheckResult;
import org.knowm.xchange.bybit.dto.trade.batch.BybitBatchAmendOrderRequest;
import org.knowm.xchange.bybit.dto.trade.batch.BybitBatchAmendPayload;
import org.knowm.xchange.bybit.dto.trade.batch.BybitBatchCancelOrderRequest;
import org.knowm.xchange.bybit.dto.trade.batch.BybitBatchCancelPayload;
import org.knowm.xchange.bybit.dto.trade.batch.BybitBatchOrderResult;
import org.knowm.xchange.bybit.dto.trade.batch.BybitBatchPlaceOrderRequest;
import org.knowm.xchange.bybit.dto.trade.batch.BybitBatchPlacePayload;
import org.knowm.xchange.bybit.dto.trade.batch.BybitBatchResult;
import org.knowm.xchange.bybit.dto.trade.details.BybitOrderDetail;
import org.knowm.xchange.bybit.dto.trade.details.BybitOrderDetails;
import org.knowm.xchange.bybit.dto.trade.execution.BybitExecution;
import org.knowm.xchange.bybit.dto.trade.execution.BybitExecutions;
import org.knowm.xchange.bybit.dto.trade.history.BybitOrderHistoryDetail;
import org.knowm.xchange.bybit.dto.trade.history.BybitOrderHistoryDetails;

public class BybitTradeServiceRawExtTest extends BaseWiremockTest {

  private BybitTradeServiceRaw rawTradeService() throws IOException {
    return (BybitTradeServiceRaw) createExchange().getTradeService();
  }

  @Test
  public void orderHistoryMapsFullRejectionFieldsAndCursor() throws IOException {
    initGetStub("/v5/order/history", "/getOrderHistory.json5");

    BybitResult<BybitOrderHistoryDetails> result =
        rawTradeService().getOrderHistory(
            BybitCategory.LINEAR, "BTCUSDT", null, null, null, null, null, null, null, null);

    assertTrue(result.isSuccess());
    assertEquals("linear", result.getResult().getCategory());
    assertEquals(
        "page_args%3D0f1e2d3c-4b5a-6978-8a9b-0c1d2e3f4a5b%26",
        result.getResult().getNextPageCursor());
    List<BybitOrderHistoryDetail> list = result.getResult().getList();
    assertEquals(2, list.size());

    BybitOrderHistoryDetail cancelled = list.get(0);
    assertEquals("CANCEL_BY_USER", cancelled.getCancelType());
    assertEquals("EC_NoError", cancelled.getRejectReason());
    assertEquals("0.500000", cancelled.getQty());
    assertEquals("30245.0000", cancelled.getCumExecValue());
    assertEquals("15.12250", cancelled.getCumExecFee());
    assertEquals("60490.0000", cancelled.getAvgPrice());
    assertEquals("0.000000", cancelled.getLeavesQty());
    assertEquals("0.0000", cancelled.getLeavesValue());
    assertEquals("link-history-001", cancelled.getOrderLinkId());
    assertEquals("1691047611000", cancelled.getCreatedTime());
    assertEquals("1691047611123", cancelled.getUpdatedTime());
    assertEquals(new BigDecimal("60490.0000"), new BigDecimal(cancelled.getAvgPrice()));

    BybitOrderHistoryDetail rejected = list.get(1);
    assertEquals(BybitOrderStatus.REJECTED, rejected.getOrderStatus());
    assertEquals("EC_OrderCannotBeFilled", rejected.getRejectReason());
    assertEquals("0.010000", rejected.getLeavesQty());
    assertEquals("550.0000", rejected.getLeavesValue());
  }

  @Test
  public void executionsPreserveExactDecimalsAndMakerFlag() throws IOException {
    initGetStub("/v5/execution/list", "/getExecutions.json5");

    BybitResult<BybitExecutions> result =
        rawTradeService().getExecutions(
            BybitCategory.LINEAR, "BTCUSDT", null, null, null, null, null, null, null);

    assertTrue(result.isSuccess());
    assertEquals("linear", result.getResult().getCategory());
    assertEquals(
        "page_args%3D6e7f8a9b-0c1d-2e3f-4a5b-6c7d8e9f0a1b%26",
        result.getResult().getNextPageCursor());
    List<BybitExecution> list = result.getResult().getList();
    assertEquals(2, list.size());

    BybitExecution taker = list.get(0);
    assertEquals("0.0000001", taker.getExecFee());
    assertEquals("60490.0000", taker.getExecPrice());
    assertEquals("0.100000", taker.getExecQty());
    assertEquals("-0.0000001", taker.getClosedPnl());
    assertEquals("0.0001", taker.getFeeRate());
    assertEquals(Boolean.FALSE, taker.getIsMaker());
    assertEquals("194256583000", taker.getSeq());
    assertEquals("link-exec-001", taker.getOrderLinkId());
    assertEquals("1691047611000", taker.getExecTime());
    assertEquals(new BigDecimal("0.0000001"), new BigDecimal(taker.getExecFee()));

    BybitExecution maker = list.get(1);
    assertEquals(Boolean.TRUE, maker.getIsMaker());
    assertEquals("55100.0000", maker.getExecPrice());
    assertEquals("0", maker.getClosedPnl());
  }

  @Test
  public void createBatchPreservesPerItemFailures() throws IOException {
    initPostStub("/v5/order/create-batch", "/createBatch.json5");

    BybitBatchPlaceOrderRequest okRequest =
        BybitBatchPlaceOrderRequest.builder()
            .symbol("BTCUSDT")
            .side("Buy")
            .orderType("Market")
            .qty("0.10")
            .orderLinkId("link-batch-001")
            .build();
    BybitBatchPlaceOrderRequest failingRequest =
        BybitBatchPlaceOrderRequest.builder()
            .symbol("ETHUSDT")
            .side("Sell")
            .orderType("Market")
            .qty("0.10")
            .orderLinkId("link-batch-002")
            .build();
    BybitBatchPlacePayload payload =
        BybitBatchPlacePayload.builder()
            .category("linear")
            .request(List.of(okRequest, failingRequest))
            .build();

    BybitBatchResult result = rawTradeService().createBatch(payload);

    assertTrue(result.isSuccess());
    List<BybitBatchOrderResult> list = result.getResult().getList();
    assertEquals(2, list.size());

    BybitBatchOrderResult ok = list.get(0);
    assertEquals("8e7d6c5b-4a3e-2f1d-0c9b-8a7b6c5d4e3f", ok.getOrderId());
    assertEquals("link-batch-001", ok.getOrderLinkId());
    assertEquals("BTCUSDT", ok.getSymbol());
    assertEquals("1691047611000", ok.getCreateAt());

    // overall retCode 0 but one item failed: per-item error must be preserved
    BybitBatchOrderResult failed = list.get(1);
    assertEquals("", failed.getOrderId());
    assertEquals("link-batch-002", failed.getOrderLinkId());
    assertEquals(Integer.valueOf(110007), result.getRetExtInfo().getList().get(1).getCode());
    assertEquals(
        "The order is not found or does not exist",
        result.getRetExtInfo().getList().get(1).getMsg());
    assertEquals(Integer.valueOf(0), result.getRetExtInfo().getList().get(0).getCode());
    assertEquals("OK", result.getRetExtInfo().getList().get(0).getMsg());
  }

  @Test
  public void amendBatchReturnsUpdatedOrders() throws IOException {
    initPostStub("/v5/order/amend-batch", "/amendBatch.json5");

    BybitBatchAmendOrderRequest request =
        BybitBatchAmendOrderRequest.builder()
            .symbol("BTCUSDT")
            .orderId("8e7d6c5b-4a3e-2f1d-0c9b-8a7b6c5d4e3f")
            .orderLinkId("link-batch-001")
            .price("60200.0000")
            .build();
    BybitBatchAmendPayload payload =
        BybitBatchAmendPayload.builder().category("linear").request(List.of(request)).build();

    BybitBatchResult result = rawTradeService().amendBatch(payload);

    assertTrue(result.isSuccess());
    assertEquals(1, result.getResult().getList().size());
    BybitBatchOrderResult amended = result.getResult().getList().get(0);
    assertEquals("8e7d6c5b-4a3e-2f1d-0c9b-8a7b6c5d4e3f", amended.getOrderId());
    assertEquals("BTCUSDT", amended.getSymbol());
    assertEquals("linear", amended.getCategory());
    assertEquals(Integer.valueOf(0), result.getRetExtInfo().getList().get(0).getCode());
  }

  @Test
  public void cancelBatchPreservesPerItemFailures() throws IOException {
    initPostStub("/v5/order/cancel-batch", "/cancelBatch.json5");

    BybitBatchCancelOrderRequest okRequest =
        BybitBatchCancelOrderRequest.builder()
            .symbol("BTCUSDT")
            .orderLinkId("link-batch-001")
            .build();
    BybitBatchCancelOrderRequest failingRequest =
        BybitBatchCancelOrderRequest.builder()
            .symbol("ETHUSDT")
            .orderLinkId("link-batch-002")
            .build();
    BybitBatchCancelPayload payload =
        BybitBatchCancelPayload.builder()
            .category("linear")
            .request(List.of(okRequest, failingRequest))
            .build();

    BybitBatchResult result = rawTradeService().cancelBatch(payload);

    assertTrue(result.isSuccess());
    List<BybitBatchOrderResult> list = result.getResult().getList();
    assertEquals(2, list.size());
    assertEquals("link-batch-001", list.get(0).getOrderLinkId());
    assertEquals(Integer.valueOf(0), result.getRetExtInfo().getList().get(0).getCode());
    assertEquals("link-batch-002", list.get(1).getOrderLinkId());
    assertEquals(Integer.valueOf(20001), result.getRetExtInfo().getList().get(1).getCode());
    assertEquals("Order not exists", result.getRetExtInfo().getList().get(1).getMsg());
  }

  @Test
  public void preCheckReportsValidOrder() throws IOException {
    initPostStub("/v5/order/pre-check", "/preCheckValid.json5");

    BybitPreCheckPayload payload =
        BybitPreCheckPayload.builder()
            .category("linear")
            .symbol("BTCUSDT")
            .side("Buy")
            .orderType("Market")
            .qty("0.10")
            .build();

    BybitResult<BybitPreCheckResult> result = rawTradeService().preCheck(payload);

    assertTrue(result.isSuccess());
    assertEquals(Boolean.TRUE, result.getResult().getIsValid());
    assertEquals("OK", result.getResult().getMessage());
  }

  @Test
  public void preCheckReportsInvalidOrder() throws IOException {
    initPostStub("/v5/order/pre-check", "/preCheckInvalid.json5");

    BybitPreCheckPayload payload =
        BybitPreCheckPayload.builder()
            .category("linear")
            .symbol("BTCUSDT")
            .side("Buy")
            .orderType("Limit")
            .qty("1000.00")
            .price("1.00")
            .build();

    BybitResult<BybitPreCheckResult> result = rawTradeService().preCheck(payload);

    assertTrue(result.isSuccess());
    assertEquals(Boolean.FALSE, result.getResult().getIsValid());
    assertEquals("Risk limit is not sufficient", result.getResult().getMessage());
  }

  @Test
  public void ambiguousPlacementReconcilesByLinkIdWithoutBlindReplay() throws IOException {
    // placement outcome is ambiguous: HTTP 500 without an order id
    stubFor(
        post(urlPathEqualTo("/v5/order/create"))
            .willReturn(
                aResponse()
                    .withStatus(Status.INTERNAL_SERVER_ERROR.getStatusCode())
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"retCode\":50001,\"retMsg\":\"internal error\",\"result\":{},\"retExtInfo\":{},\"time\":1}")));
    // reconciliation source: order lookup by client-supplied link id
    initGetStub(
        "/v5/order/realtime",
        "/getOrdersByLinkId.json5",
        "orderLinkId",
        equalTo("link-ambig-001"));

    BybitTradeServiceRaw raw = rawTradeService();
    assertThrows(
        BybitException.class,
        () ->
            raw.placeOrder(
                new org.knowm.xchange.bybit.dto.trade.BybitPlaceOrderPayload(
                    BybitCategory.LINEAR,
                    "ETHUSDT",
                    org.knowm.xchange.bybit.dto.trade.BybitSide.BUY,
                    org.knowm.xchange.bybit.dto.trade.BybitOrderType.MARKET,
                    new BigDecimal("0.10"),
                    "link-ambig-001"),
                BybitCategory.LINEAR));

    // reconcile: prove the order by client identity, never by replaying the placement
    BybitResult<BybitOrderDetails<BybitOrderDetail>> reconciled =
        raw.getBybitOrderByLinkId(BybitCategory.LINEAR, "ETHUSDT", "link-ambig-001");

    assertTrue(reconciled.isSuccess());
    assertEquals(1, reconciled.getResult().getList().size());
    assertEquals(
        "fd4300ae-7847-404e-b947-b46980a4d140",
        reconciled.getResult().getList().get(0).getOrderId());
    assertEquals(
        BybitOrderStatus.NEW, reconciled.getResult().getList().get(0).getOrderStatus());

    // exactly one create attempt: no blind replay
    verify(1, postRequestedFor(urlPathEqualTo("/v5/order/create")));
  }
}
