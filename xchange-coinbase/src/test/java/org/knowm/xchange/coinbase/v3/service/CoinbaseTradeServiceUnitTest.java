package org.knowm.xchange.coinbase.v3.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNull;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Collection;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.coinbase.v3.CoinbaseAuthenticated;
import org.knowm.xchange.coinbase.v3.CoinbaseProductIdentity;
import org.knowm.xchange.coinbase.v3.CoinbaseExchange;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseCreateOrderResponse;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseOrderRequest;
import org.knowm.xchange.coinbase.v3.dto.products.CoinbaseProductResponse;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseClosePositionRequest;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseEditOrderRequest;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseEditOrderResponse;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseCancelOrderResult;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseCancelOrdersResponse;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseOrderDetail;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseOrderDetailResponse;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbasePreviewOrderResponse;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.dto.trade.StopOrder;
import org.knowm.xchange.derivative.FuturesContract;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.coinbase.v3.dto.trade.CoinbaseTradeHistoryParams;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.service.trade.TradeService;
import org.knowm.xchange.service.trade.params.DefaultCancelOrderParamId;
import org.knowm.xchange.service.trade.params.TradeHistoryParams;
import org.knowm.xchange.service.trade.params.orders.DefaultQueryOrderParam;
import org.knowm.xchange.service.trade.params.orders.OrderQueryParams;
import si.mazi.rescu.ParamsDigest;

/**
 * Unit tests for CoinbaseTradeService. Tests service instantiation and basic structure to prevent
 * regressions.
 */
public class CoinbaseTradeServiceUnitTest {

  private CoinbaseTradeService service;
  private CoinbaseAuthenticated api;
  private ParamsDigest digest;
  private Exchange exchange;

  @Before
  public void setUp() {
    exchange = mock(Exchange.class);
    api = mock(CoinbaseAuthenticated.class);
    digest = mock(ParamsDigest.class);
    service = new CoinbaseTradeService(exchange, api, digest);
  }

  @Test
  public void cancelOrderReflectsPerOrderProviderResult() throws Exception {
    CoinbaseCancelOrdersResponse accepted =
        new CoinbaseCancelOrdersResponse(
            Collections.singletonList(new CoinbaseCancelOrderResult(true, null, "order-1")));
    CoinbaseCancelOrdersResponse rejected =
        new CoinbaseCancelOrdersResponse(
            Collections.singletonList(
                new CoinbaseCancelOrderResult(false, "UNKNOWN_CANCEL_FAILURE_REASON", "order-2")));
    when(api.batchCancelOrders(any(ParamsDigest.class), any())).thenReturn(accepted, rejected);
    assertTrue(service.cancelOrder(new DefaultCancelOrderParamId("order-1")));
    assertFalse(service.cancelOrder(new DefaultCancelOrderParamId("order-2")));
  }

  @Test
  public void marketOrderRejectsSuccessfulHttpFailurePayload() throws Exception {
    when(api.createOrder(any(ParamsDigest.class), any()))
        .thenReturn(
            new CoinbaseCreateOrderResponse(
                false, null, new CoinbaseCreateOrderResponse.ErrorResponse("INVALID", "rejected")));
    try {
      service.placeMarketOrder(
          new MarketOrder(Order.OrderType.BID, BigDecimal.ONE, CurrencyPair.BTC_USD));
      fail("Expected a failed successful-HTTP response to be rejected");
    } catch (ExchangeException expected) {
      assertTrue(expected.getMessage().contains("rejected"));
    }
  }

  @Test
  public void marketOrderSubmissionUsesCatalogNativeProductId() throws Exception {
    String productId = "ETP-20DEC30-CDE";
    CoinbaseProductIdentity catalog =
        CoinbaseProductIdentity.build(
            Collections.singletonList(
                new CoinbaseProductResponse(
                    productId, null, null, null, null, null, "ETH", "USD", "FUTURE", "CDE", null)));
    FuturesContract instrument = (FuturesContract) catalog.instrument(productId);
    CoinbaseTradeService catalogService =
        new CoinbaseTradeService(exchange, api, digest, catalog);
    when(api.createOrder(any(ParamsDigest.class), any(CoinbaseOrderRequest.class)))
        .thenReturn(
            new CoinbaseCreateOrderResponse(
                true, new CoinbaseCreateOrderResponse.SuccessResponse("order-1"), null));

    String orderId =
        catalogService.placeMarketOrder(
            new MarketOrder.Builder(Order.OrderType.BID, instrument)
                .originalAmount(BigDecimal.ONE)
                .build());

    ArgumentCaptor<CoinbaseOrderRequest> request = ArgumentCaptor.forClass(CoinbaseOrderRequest.class);
    verify(api).createOrder(any(ParamsDigest.class), request.capture());
    assertEquals("order-1", orderId);
    assertEquals(productId, request.getValue().getProductId());
  }

  @Test
  public void limitOrderRejectsSuccessfulHttpFailurePayload() throws Exception {
    when(api.createOrder(any(ParamsDigest.class), any()))
        .thenReturn(
            new CoinbaseCreateOrderResponse(
                false, null, new CoinbaseCreateOrderResponse.ErrorResponse("INVALID", "rejected")));
    LimitOrder order =
        new LimitOrder(
            Order.OrderType.BID,
            BigDecimal.ONE,
            CurrencyPair.BTC_USD,
            null,
            null,
            BigDecimal.ONE);
    try {
      service.placeLimitOrder(order);
      fail("Expected a failed successful-HTTP response to be rejected");
    } catch (ExchangeException expected) {
      assertTrue(expected.getMessage().contains("rejected"));
    }
  }

  @Test
  public void stopOrderRejectsSuccessfulHttpFailurePayload() throws Exception {
    when(api.createOrder(any(ParamsDigest.class), any()))
        .thenReturn(
            new CoinbaseCreateOrderResponse(
                false, null, new CoinbaseCreateOrderResponse.ErrorResponse("INVALID", "rejected")));
    StopOrder order =
        new StopOrder(
            Order.OrderType.BID,
            BigDecimal.ONE,
            CurrencyPair.BTC_USD,
            null,
            null,
            BigDecimal.ONE);
    try {
      service.placeStopOrder(order);
      fail("Expected a failed successful-HTTP response to be rejected");
    } catch (ExchangeException expected) {
      assertTrue(expected.getMessage().contains("rejected"));
    }
  }

  @Test
  public void closePositionRejectsSuccessfulHttpFailurePayload() throws Exception {
    when(api.closePosition(any(ParamsDigest.class), any()))
        .thenReturn(
            new CoinbaseCreateOrderResponse(
                false, null, new CoinbaseCreateOrderResponse.ErrorResponse("INVALID", "rejected")));

    try {
      service.closePosition(
          new CoinbaseClosePositionRequest(
              "close-client-order", "ETP-20DEC30-CDE", BigDecimal.ONE));
      fail("Expected a failed successful-HTTP response to be rejected");
    } catch (ExchangeException expected) {
      assertTrue(expected.getMessage().contains("rejected"));
    }
  }

  @Test
  public void editOrderRejectsFalseSuccessResponse() throws Exception {
    when(api.editOrderCurrent(any(ParamsDigest.class), any()))
        .thenReturn(new CoinbaseEditOrderResponse(false, Collections.emptyList()));
    try {
      service.editOrderCurrent(new CoinbaseEditOrderRequest("order-1", null, null, null, null, null));
      fail("Expected a false success response to be rejected");
    } catch (ExchangeException expected) {
      assertTrue(expected.getMessage().contains("editOrder"));
    }
  }

  @Test
  public void previewEditOrderRejectsMissingSuccessResponse() throws Exception {
    when(api.previewEditOrderCurrent(any(ParamsDigest.class), any())).thenReturn(null);
    try {
      service.previewEditOrderCurrent(
          new CoinbaseEditOrderRequest("order-1", null, null, null, null, null));
      fail("Expected a missing response to be rejected");
    } catch (ExchangeException expected) {
      assertTrue(expected.getMessage().contains("previewEditOrder"));
    }
  }
  @Test
  public void verifyOrderRejectsMissingPreviewResponse() throws Exception {
    when(api.previewOrderCurrent(any(ParamsDigest.class), any())).thenReturn(null);
    LimitOrder order =
        new LimitOrder(
            Order.OrderType.BID,
            BigDecimal.ONE,
            CurrencyPair.BTC_USD,
            null,
            null,
            BigDecimal.ONE);
    try {
      service.verifyOrder(order);
      fail("Expected a missing preview response to be rejected");
    } catch (ExchangeException expected) {
      assertTrue(expected.getMessage().contains("preview"));
    }
  }
  @Test
  public void previewOrderDeserializesRequiredErrorsAndOptionalPreviewId() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    CoinbasePreviewOrderResponse accepted =
        mapper.readValue(
            "{\"errs\":[],\"preview_id\":\"preview-1\",\"order_total\":\"1.00\"}",
            CoinbasePreviewOrderResponse.class);
    assertTrue(accepted.isSuccessful());
    assertEquals("preview-1", accepted.getPreviewId());

    CoinbasePreviewOrderResponse missingErrors =
        mapper.readValue("{\"preview_id\":\"preview-2\"}", CoinbasePreviewOrderResponse.class);
    assertNull(missingErrors.getErrs());
    assertFalse(missingErrors.isSuccessful());
  }

  @Test
  public void verifyLimitAndMarketOrdersRejectPreviewFailuresWithProviderDetail() throws Exception {
    LimitOrder limitOrder =
        new LimitOrder(
            Order.OrderType.BID,
            BigDecimal.ONE,
            CurrencyPair.BTC_USD,
            null,
            null,
            BigDecimal.ONE);
    MarketOrder marketOrder =
        new MarketOrder(Order.OrderType.BID, BigDecimal.ONE, CurrencyPair.BTC_USD);

    when(api.previewOrderCurrent(any(ParamsDigest.class), any()))
        .thenReturn(
            new CoinbasePreviewOrderResponse(Collections.emptyList(), "limit-preview"),
            new CoinbasePreviewOrderResponse(
                Collections.singletonList("PREVIEW_INSUFFICIENT_FUNDS"), "market-preview"));
    service.verifyOrder(limitOrder);
    try {
      service.verifyOrder(marketOrder);
      fail("Expected a rejected market preview to fail closed");
    } catch (ExchangeException expected) {
      assertTrue(expected.getMessage().contains("PREVIEW_INSUFFICIENT_FUNDS"));
    }
  }

  @Test
  public void verifyMarketOrderAcceptsEmptyPreviewErrors() throws Exception {
    when(api.previewOrderCurrent(any(ParamsDigest.class), any()))
        .thenReturn(new CoinbasePreviewOrderResponse(Collections.emptyList(), "market-preview"));
    service.verifyOrder(new MarketOrder(Order.OrderType.BID, BigDecimal.ONE, CurrencyPair.BTC_USD));
  }

  @Test
  public void verifyLimitOrderRejectsPreviewErrorsWithProviderDetail() throws Exception {
    when(api.previewOrderCurrent(any(ParamsDigest.class), any()))
        .thenReturn(
            new CoinbasePreviewOrderResponse(
                Collections.singletonList("PREVIEW_INVALID_LIMIT_PRICE"), "limit-preview"));
    try {
      service.verifyOrder(
          new LimitOrder(
              Order.OrderType.BID,
              BigDecimal.ONE,
              CurrencyPair.BTC_USD,
              null,
              null,
              BigDecimal.ONE));
      fail("Expected a rejected limit preview to fail closed");
    } catch (ExchangeException expected) {
      assertTrue(expected.getMessage().contains("PREVIEW_INVALID_LIMIT_PRICE"));
    }
  }
  @Test
  public void verifyMarketOrderRejectsPreviewWithMissingErrors() throws Exception {
    when(api.previewOrderCurrent(any(ParamsDigest.class), any()))
        .thenReturn(new CoinbasePreviewOrderResponse(null, "market-preview"));
    try {
      service.verifyOrder(new MarketOrder(Order.OrderType.BID, BigDecimal.ONE, CurrencyPair.BTC_USD));
      fail("Expected a preview without errs to fail closed");
    } catch (ExchangeException expected) {
      assertTrue(expected.getMessage().contains("missing errs"));
    }
  }



  @Test
  public void testServiceCreationSucceeds() {
    Exchange exchange = ExchangeFactory.INSTANCE.createExchange(CoinbaseExchange.class);
    TradeService service = exchange.getTradeService();

    assertNotNull("Trade service should not be null", service);
  }

  @Test
  public void testServiceIsCorrectType() {
    Exchange exchange = ExchangeFactory.INSTANCE.createExchange(CoinbaseExchange.class);
    TradeService service = exchange.getTradeService();

    assertNotNull("Service should not be null", service);
    assert (service instanceof CoinbaseTradeService);
  }

  @Test
  public void testCreateTradeHistoryParamsReturnsCorrectType() {
    Exchange exchange = ExchangeFactory.INSTANCE.createExchange(CoinbaseExchange.class);
    CoinbaseTradeService service = (CoinbaseTradeService) exchange.getTradeService();

    TradeHistoryParams params = service.createTradeHistoryParams();

    assertNotNull("Trade history params should not be null", params);
    assert (params instanceof CoinbaseTradeHistoryParams);
  }

  /**
   * Test getOrder with a single valid order ID. Verifies that the method correctly retrieves an
   * order and adapts it.
   */
  @Test
  public void testGetOrderWithSingleValidOrderId() throws IOException {
    // Given: A mocked order response
    CoinbaseOrderDetail detail =
        new CoinbaseOrderDetail(
            "order-123",
            "client-123",
            "BUY",
            "BTC-USD",
            "FILLED",
            new BigDecimal("50000.00"),
            new BigDecimal("1.0"),
            new BigDecimal("10.00"),
            new BigDecimal("1.0"),
            new BigDecimal("50000.00"),
            "2024-01-01T00:00:00Z");
    CoinbaseOrderDetailResponse response = new CoinbaseOrderDetailResponse(detail);
    when(api.getOrder(digest, "order-123")).thenReturn(response);

    // When: Getting order with valid ID
    OrderQueryParams params = new DefaultQueryOrderParam("order-123");
    Collection<Order> orders = service.getOrder(params);

    // Then: Should return one order with correct ID
    assertNotNull("Orders collection should not be null", orders);
    assertEquals("Should return exactly one order", 1, orders.size());
    Order order = orders.iterator().next();
    assertEquals("Order ID should match", "order-123", order.getId());
    verify(api, times(1)).getOrder(digest, "order-123");
  }

  /**
   * Test getOrder with multiple valid order IDs. Verifies that the method processes all params and
   * returns all orders.
   */
  @Test
  public void testGetOrderWithMultipleValidOrderIds() throws IOException {
    // Given: Multiple mocked order responses
    CoinbaseOrderDetail detail1 =
        new CoinbaseOrderDetail(
            "order-1",
            "client-1",
            "BUY",
            "BTC-USD",
            "FILLED",
            new BigDecimal("50000"),
            new BigDecimal("1.0"),
            new BigDecimal("10"),
            new BigDecimal("1.0"),
            new BigDecimal("50000"),
            "2024-01-01T00:00:00Z");
    CoinbaseOrderDetail detail2 =
        new CoinbaseOrderDetail(
            "order-2",
            "client-2",
            "SELL",
            "ETH-USD",
            "OPEN",
            null,
            null,
            null,
            new BigDecimal("2.0"),
            new BigDecimal("3000"),
            "2024-01-02T00:00:00Z");
    CoinbaseOrderDetail detail3 =
        new CoinbaseOrderDetail(
            "order-3",
            "client-3",
            "BUY",
            "LTC-USD",
            "CANCELLED",
            null,
            null,
            null,
            new BigDecimal("5.0"),
            new BigDecimal("100"),
            "2024-01-03T00:00:00Z");

    when(api.getOrder(digest, "order-1")).thenReturn(new CoinbaseOrderDetailResponse(detail1));
    when(api.getOrder(digest, "order-2")).thenReturn(new CoinbaseOrderDetailResponse(detail2));
    when(api.getOrder(digest, "order-3")).thenReturn(new CoinbaseOrderDetailResponse(detail3));

    // When: Getting multiple orders
    OrderQueryParams param1 = new DefaultQueryOrderParam("order-1");
    OrderQueryParams param2 = new DefaultQueryOrderParam("order-2");
    OrderQueryParams param3 = new DefaultQueryOrderParam("order-3");
    Collection<Order> orders = service.getOrder(param1, param2, param3);

    // Then: Should return three orders
    assertNotNull("Orders collection should not be null", orders);
    assertEquals("Should return exactly three orders", 3, orders.size());
    verify(api, times(1)).getOrder(digest, "order-1");
    verify(api, times(1)).getOrder(digest, "order-2");
    verify(api, times(1)).getOrder(digest, "order-3");
  }

  /**
   * Test getOrder with null order ID. Verifies that params with null order IDs are skipped without
   * throwing exceptions.
   */
  @Test
  public void testGetOrderWithNullOrderId() throws IOException {
    // Given: A param with null order ID
    OrderQueryParams params = new DefaultQueryOrderParam(null);

    // When: Getting order with null ID
    Collection<Order> orders = service.getOrder(params);

    // Then: Should return empty collection and not call API
    assertNotNull("Orders collection should not be null", orders);
    assertTrue("Orders collection should be empty", orders.isEmpty());
    verify(api, never()).getOrder(digest, null);
  }

  /**
   * Test getOrder with mixed valid and null order IDs. Verifies that only valid IDs are processed,
   * nulls are skipped.
   */
  @Test
  public void testGetOrderWithMixedValidAndNullOrderIds() throws IOException {
    // Given: Multiple params, some with null IDs
    CoinbaseOrderDetail detail1 =
        new CoinbaseOrderDetail(
            "order-1",
            "client-1",
            "BUY",
            "BTC-USD",
            "FILLED",
            new BigDecimal("50000"),
            new BigDecimal("1.0"),
            new BigDecimal("10"),
            new BigDecimal("1.0"),
            new BigDecimal("50000"),
            "2024-01-01T00:00:00Z");
    CoinbaseOrderDetail detail3 =
        new CoinbaseOrderDetail(
            "order-3",
            "client-3",
            "SELL",
            "ETH-USD",
            "OPEN",
            null,
            null,
            null,
            new BigDecimal("2.0"),
            new BigDecimal("3000"),
            "2024-01-03T00:00:00Z");

    when(api.getOrder(digest, "order-1")).thenReturn(new CoinbaseOrderDetailResponse(detail1));
    when(api.getOrder(digest, "order-3")).thenReturn(new CoinbaseOrderDetailResponse(detail3));

    // When: Getting orders with mixed valid/null IDs
    OrderQueryParams param1 = new DefaultQueryOrderParam("order-1");
    OrderQueryParams param2 = new DefaultQueryOrderParam(null);
    OrderQueryParams param3 = new DefaultQueryOrderParam("order-3");
    Collection<Order> orders = service.getOrder(param1, param2, param3);

    // Then: Should return only the two valid orders
    assertNotNull("Orders collection should not be null", orders);
    assertEquals("Should return exactly two orders", 2, orders.size());
    verify(api, times(1)).getOrder(digest, "order-1");
    verify(api, times(1)).getOrder(digest, "order-3");
    verify(api, never()).getOrder(digest, null);
  }

  /**
   * Test getOrder with empty params array. Verifies that an empty array returns an empty
   * collection.
   */
  @Test
  public void testGetOrderWithEmptyParamsArray() throws IOException {
    // When: Getting orders with no params
    Collection<Order> orders = service.getOrder(new OrderQueryParams[0]);

    // Then: Should return empty collection
    assertNotNull("Orders collection should not be null", orders);
    assertTrue("Orders collection should be empty", orders.isEmpty());
  }

  /**
   * Test getOrder with DefaultQueryOrderParam (the type that had redundant code). Verifies that
   * DefaultQueryOrderParam works correctly after removing redundant code.
   */
  @Test
  public void testGetOrderWithDefaultQueryOrderParam() throws IOException {
    // Given: A DefaultQueryOrderParam instance
    CoinbaseOrderDetail detail =
        new CoinbaseOrderDetail(
            "order-xyz",
            "client-xyz",
            "BUY",
            "BTC-USD",
            "OPEN",
            null,
            null,
            null,
            new BigDecimal("0.5"),
            new BigDecimal("50000"),
            "2024-01-01T00:00:00Z");
    CoinbaseOrderDetailResponse response = new CoinbaseOrderDetailResponse(detail);
    when(api.getOrder(digest, "order-xyz")).thenReturn(response);

    // When: Using DefaultQueryOrderParam specifically
    DefaultQueryOrderParam param = new DefaultQueryOrderParam("order-xyz");
    Collection<Order> orders = service.getOrder(param);

    // Then: Should work correctly
    assertNotNull("Orders collection should not be null", orders);
    assertEquals("Should return exactly one order", 1, orders.size());
    Order order = orders.iterator().next();
    assertEquals("Order ID should match", "order-xyz", order.getId());
    verify(api, times(1)).getOrder(digest, "order-xyz");
  }
}
