package org.knowm.xchange.okx.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.knowm.xchange.client.ResilienceRegistries;
import org.knowm.xchange.okx.OkxExchange;
import org.knowm.xchange.okx.dto.OkxException;
import org.knowm.xchange.okx.dto.OkxResponse;
import org.knowm.xchange.okx.dto.trade.OkxAlgoOrderDetails;
import org.knowm.xchange.okx.dto.trade.OkxFill;
import org.knowm.xchange.okx.dto.trade.OkxOrderDetails;
import org.knowm.xchange.okx.dto.trade.OkxOrderRequest;
import org.knowm.xchange.okx.dto.trade.OkxOrderResponse;
import org.knowm.xchange.okx.dto.trade.OkxPageParams;

/**
 * Offline tests for the Phase 4 trade endpoints and idempotent placement reconciliation. All HTTP
 * seams are stubbed in a subclass; nothing touches the network.
 */
public class OkxTradeServiceRawTest {

  private final ObjectMapper mapper = new ObjectMapper();

  /** Subclass that stubs every HTTP seam so pagination and reconciliation run offline. */
  static class StubTradeServiceRaw extends OkxTradeServiceRaw {

    final List<String> orderHistoryCursors = new ArrayList<>();
    final List<String> fillCursors = new ArrayList<>();
    final List<String> fillHistoryCursors = new ArrayList<>();
    final List<String> algoPendingCursors = new ArrayList<>();
    final List<String> algoHistoryCursors = new ArrayList<>();
    final List<String> orderLookups = new ArrayList<>();
    int placeSingleCalls;
    int placeBatchCalls;
    OkxOrderRequest lastPlacedSingle;
    List<OkxOrderRequest> lastPlacedBatch = new ArrayList<>();

    Map<String, List<OkxOrderDetails>> orderHistoryPages = new HashMap<>();
    Map<String, List<OkxFill>> fillPages = new HashMap<>();
    Map<String, List<OkxFill>> fillHistoryPages = new HashMap<>();
    Map<String, List<OkxAlgoOrderDetails>> algoPendingPages = new HashMap<>();
    Map<String, List<OkxAlgoOrderDetails>> algoHistoryPages = new HashMap<>();

    OkxResponse<List<OkxOrderDetails>> existingOrderResponse =
        new OkxResponse<>(null, "0", null, Collections.emptyList());

    /** When non-null, only this client order id reconciles as an existing order. */
    String existingClientOrderId;

    OkxResponse<List<OkxOrderResponse>> placeSingleResponse =
        new OkxResponse<>(null, "0", null, Collections.emptyList());
    OkxResponse<List<OkxOrderResponse>> placeBatchResponse =
        new OkxResponse<>(null, "0", null, Collections.emptyList());

    StubTradeServiceRaw(OkxExchange exchange) {
      super(exchange, new ResilienceRegistries());
    }

    @Override
    OkxResponse<List<OkxOrderDetails>> fetchOrderHistoryPage(
        String instrumentType,
        String instrumentId,
        String orderType,
        String after,
        String before,
        String limit) {
      orderHistoryCursors.add(after);
      return new OkxResponse<>(
          null, "0", null, orderHistoryPages.getOrDefault(after, Collections.emptyList()));
    }

    @Override
    OkxResponse<List<OkxFill>> fetchFillsPage(
        String instrumentType,
        String instrumentId,
        String orderId,
        String after,
        String before,
        String limit) {
      fillCursors.add(after);
      return new OkxResponse<>(
          null, "0", null, fillPages.getOrDefault(after, Collections.emptyList()));
    }

    @Override
    OkxResponse<List<OkxFill>> fetchFillsHistoryPage(
        String instrumentType,
        String instrumentId,
        String orderId,
        String after,
        String before,
        String limit) {
      fillHistoryCursors.add(after);
      return new OkxResponse<>(
          null, "0", null, fillHistoryPages.getOrDefault(after, Collections.emptyList()));
    }

    @Override
    OkxResponse<List<OkxAlgoOrderDetails>> fetchAlgoOrdersPendingPage(
        String instrumentType,
        String instrumentId,
        String orderType,
        String after,
        String before,
        String limit) {
      algoPendingCursors.add(after);
      return new OkxResponse<>(
          null, "0", null, algoPendingPages.getOrDefault(after, Collections.emptyList()));
    }

    @Override
    OkxResponse<List<OkxAlgoOrderDetails>> fetchAlgoOrdersHistoryPage(
        String instrumentType,
        String instrumentId,
        String orderType,
        String state,
        String after,
        String before,
        String limit) {
      algoHistoryCursors.add(after);
      return new OkxResponse<>(
          null, "0", null, algoHistoryPages.getOrDefault(after, Collections.emptyList()));
    }

    /**
     * When non-null, the lookup seam throws OKX 51603 (order does not exist) for this client id.
     */
    String notFoundClientOrderId;

    @Override
    OkxResponse<List<OkxOrderDetails>> fetchOrderDetails(
        String instrumentId, String orderId, String clientOrderId) throws IOException {
      orderLookups.add(clientOrderId);
      if (clientOrderId != null && clientOrderId.equals(notFoundClientOrderId)) {
        throw new OkxException("Order does not exist", 51603);
      }
      if (clientOrderId != null && clientOrderId.equals(existingClientOrderId)) {
        return existingOrderResponse;
      }
      return new OkxResponse<>(null, "0", null, Collections.emptyList());
    }

    @Override
    OkxResponse<List<OkxOrderResponse>> doPlaceOkxOrder(OkxOrderRequest order) {
      placeSingleCalls++;
      lastPlacedSingle = order;
      return placeSingleResponse;
    }

    @Override
    OkxResponse<List<OkxOrderResponse>> doPlaceBatchOkxOrder(List<OkxOrderRequest> orders) {
      placeBatchCalls++;
      lastPlacedBatch = new ArrayList<>(orders);
      return placeBatchResponse;
    }
  }

  private StubTradeServiceRaw service;

  @Before
  public void setUp() {
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    OkxExchange exchange = new OkxExchange();
    exchange.applySpecification(exchange.getDefaultExchangeSpecification());
    service = new StubTradeServiceRaw(exchange);
  }

  private OkxOrderDetails orderDetails(String orderId, String clientOrderId) throws Exception {
    return mapper.readValue(
        "{\"ordId\":\"" + orderId + "\",\"clOrdId\":\"" + clientOrderId + "\"}",
        new TypeReference<OkxOrderDetails>() {});
  }

  private OkxFill fill(String billId) throws Exception {
    return mapper.readValue("{\"billId\":\"" + billId + "\"}", new TypeReference<OkxFill>() {});
  }

  private OkxAlgoOrderDetails algoOrderDetails(String orderId) throws Exception {
    return mapper.readValue(
        "{\"ordId\":\"" + orderId + "\"}", new TypeReference<OkxAlgoOrderDetails>() {});
  }

  /** Real algo-order payloads identify records with {@code algoId}; {@code ordId} is absent. */
  private OkxAlgoOrderDetails algoOrderDetailsWithAlgoId(String algoId) throws Exception {
    return mapper.readValue(
        "{\"algoId\":\"" + algoId + "\"}", new TypeReference<OkxAlgoOrderDetails>() {});
  }

  // ---------- Order history pagination ----------

  @Test
  public void testGetOrderHistoryIteratesFullPagesThenStopsOnPartial() throws Exception {
    service.orderHistoryPages.put(null, List.of(orderDetails("1", "c1"), orderDetails("2", "c2")));
    service.orderHistoryPages.put("2", List.of(orderDetails("3", "c3"), orderDetails("4", "c4")));
    service.orderHistoryPages.put("4", List.of(orderDetails("5", "c5")));

    OkxResponse<List<OkxOrderDetails>> result =
        service.getOrderHistory("SPOT", "BTC-USDT", null, OkxPageParams.of(2));

    assertThat(result.getData())
        .extracting(OkxOrderDetails::getOrderId)
        .containsExactly("1", "2", "3", "4", "5");
    assertThat(service.orderHistoryCursors).containsExactly(null, "2", "4");
  }

  @Test
  public void testGetOrderHistoryStopsOnEmptyPage() throws Exception {
    service.orderHistoryPages.put(null, List.of(orderDetails("1", "c1"), orderDetails("2", "c2")));
    service.orderHistoryPages.put("2", Collections.emptyList());

    OkxResponse<List<OkxOrderDetails>> result =
        service.getOrderHistory("SPOT", "BTC-USDT", null, OkxPageParams.of(2));

    assertThat(result.getData()).extracting(OkxOrderDetails::getOrderId).containsExactly("1", "2");
    assertThat(service.orderHistoryCursors).containsExactly(null, "2");
  }

  @Test
  public void testGetOrderHistoryStopsOnNoProgress() throws Exception {
    service.orderHistoryPages.put(null, List.of(orderDetails("1", "c1"), orderDetails("2", "c2")));
    // Full page repeating the previous page's last id: no forward progress.
    service.orderHistoryPages.put("2", List.of(orderDetails("9", "c9"), orderDetails("2", "c2")));

    OkxResponse<List<OkxOrderDetails>> result =
        service.getOrderHistory("SPOT", "BTC-USDT", null, OkxPageParams.of(2));

    assertThat(result.getData()).extracting(OkxOrderDetails::getOrderId).containsExactly("1", "2");
    assertThat(service.orderHistoryCursors).containsExactly(null, "2");
  }

  // ---------- Fills history pagination ----------

  @Test
  public void testGetOkxFillReturnsSinglePage() throws Exception {
    service.fillPages.put(null, List.of(fill("b1"), fill("b2")));

    OkxResponse<List<OkxFill>> result =
        service.getOkxFill("SPOT", "BTC-USDT", "ord-1", null, null, null);

    assertThat(result.getData()).extracting(OkxFill::getBillId).containsExactly("b1", "b2");
    assertThat(service.fillCursors).containsExactly((String) null);
  }

  @Test
  public void testGetOkxFillsHistoryAccumulates() throws Exception {
    service.fillHistoryPages.put(null, List.of(fill("b1"), fill("b2")));
    service.fillHistoryPages.put("b2", List.of(fill("b3")));

    OkxResponse<List<OkxFill>> result =
        service.getOkxFillsHistory("SPOT", "BTC-USDT", null, OkxPageParams.of(2));

    assertThat(result.getData()).extracting(OkxFill::getBillId).containsExactly("b1", "b2", "b3");
    assertThat(service.fillHistoryCursors).containsExactly(null, "b2");
  }

  // ---------- Algo order history pagination ----------

  @Test
  public void testGetAlgoOrdersPendingAccumulates() throws Exception {
    service.algoPendingPages.put(null, List.of(algoOrderDetails("1"), algoOrderDetails("2")));
    service.algoPendingPages.put("2", List.of(algoOrderDetails("3")));

    OkxResponse<List<OkxAlgoOrderDetails>> result =
        service.getAlgoOrdersPending("SPOT", "BTC-USDT", "conditional", OkxPageParams.of(2));

    assertThat(result.getData())
        .extracting(OkxAlgoOrderDetails::getOrderId)
        .containsExactly("1", "2", "3");
    assertThat(service.algoPendingCursors).containsExactly(null, "2");
  }

  @Test
  public void testGetAlgoOrdersHistoryAccumulates() throws Exception {
    service.algoHistoryPages.put(null, List.of(algoOrderDetails("1"), algoOrderDetails("2")));
    service.algoHistoryPages.put("2", List.of(algoOrderDetails("3")));

    OkxResponse<List<OkxAlgoOrderDetails>> result =
        service.getAlgoOrdersHistory(
            "SPOT", "BTC-USDT", "conditional", "effective", OkxPageParams.of(2));

    assertThat(result.getData())
        .extracting(OkxAlgoOrderDetails::getOrderId)
        .containsExactly("1", "2", "3");
    assertThat(service.algoHistoryCursors).containsExactly(null, "2");
  }

  @Test
  public void testAlgoPendingPaginationAdvancesWithAlgoIdWhenOrdIdIsAbsent() throws Exception {
    service.algoPendingPages.put(
        null, List.of(algoOrderDetailsWithAlgoId("a-1"), algoOrderDetailsWithAlgoId("a-2")));
    service.algoPendingPages.put("a-2", List.of(algoOrderDetailsWithAlgoId("a-3")));

    OkxResponse<List<OkxAlgoOrderDetails>> result =
        service.getAlgoOrdersPending("SPOT", "BTC-USDT", "conditional", OkxPageParams.of(2));

    assertThat(result.getData())
        .extracting(OkxAlgoOrderDetails::getAlgoId)
        .containsExactly("a-1", "a-2", "a-3");
    assertThat(service.algoPendingCursors).containsExactly(null, "a-2");
  }

  @Test
  public void testAlgoHistoryPaginationAdvancesWithAlgoIdWhenOrdIdIsAbsent() throws Exception {
    service.algoHistoryPages.put(
        null, List.of(algoOrderDetailsWithAlgoId("a-1"), algoOrderDetailsWithAlgoId("a-2")));
    service.algoHistoryPages.put("a-2", List.of(algoOrderDetailsWithAlgoId("a-3")));

    OkxResponse<List<OkxAlgoOrderDetails>> result =
        service.getAlgoOrdersHistory(
            "SPOT", "BTC-USDT", "conditional", "effective", OkxPageParams.of(2));

    assertThat(result.getData())
        .extracting(OkxAlgoOrderDetails::getAlgoId)
        .containsExactly("a-1", "a-2", "a-3");
    assertThat(service.algoHistoryCursors).containsExactly(null, "a-2");
  }

  // ---------- Idempotent placement reconciliation ----------

  private OkxOrderRequest orderRequest(String clientOrderId) {
    return OkxOrderRequest.builder().instrumentId("BTC-USDT").clientOrderId(clientOrderId).build();
  }

  @Test
  public void testPlaceOrderReconcilesExistingClientOrderId() throws Exception {
    service.existingClientOrderId = "cl-1";
    service.existingOrderResponse =
        new OkxResponse<>(null, "0", null, List.of(orderDetails("ord-1", "cl-1")));

    OkxResponse<List<OkxOrderResponse>> result = service.placeOkxOrder(orderRequest("cl-1"));

    assertThat(service.placeSingleCalls).isZero();
    assertThat(service.orderLookups).containsExactly("cl-1");
    assertThat(result.getData()).hasSize(1);
    assertThat(result.getData().get(0).getOrderId()).isEqualTo("ord-1");
    assertThat(result.getData().get(0).getClientOrderId()).isEqualTo("cl-1");
    assertThat(result.isSuccess()).isTrue();
  }

  @Test
  public void testPlaceOrderPlacesWhenNoExistingOrder() throws Exception {
    service.placeSingleResponse =
        new OkxResponse<>(null, "0", null, List.of(new OkxOrderResponse()));

    OkxResponse<List<OkxOrderResponse>> result = service.placeOkxOrder(orderRequest("cl-new"));

    assertThat(service.placeSingleCalls).isEqualTo(1);
    assertThat(service.lastPlacedSingle.getClientOrderId()).isEqualTo("cl-new");
    assertThat(result.getData()).hasSize(1);
  }

  @Test
  public void testPlaceOrderTreatsMissingOrderLookupAsNotFound() throws Exception {
    // OKX returns 51603 ("Order does not exist") for a fresh clOrdId lookup; that must be treated
    // as an empty lookup so a brand-new replay-safe order can still be placed.
    service.notFoundClientOrderId = "cl-fresh";
    service.placeSingleResponse =
        new OkxResponse<>(null, "0", null, List.of(OkxOrderResponse.replay("ord-new", "cl-fresh")));

    OkxResponse<List<OkxOrderResponse>> result = service.placeOkxOrder(orderRequest("cl-fresh"));

    assertThat(service.orderLookups).containsExactly("cl-fresh");
    assertThat(service.placeSingleCalls).isEqualTo(1);
    assertThat(result.getData()).hasSize(1);
    assertThat(result.getData().get(0).getOrderId()).isEqualTo("ord-new");
  }

  @Test
  public void testPlaceBatchTreatsMissingOrderLookupAsNotFound() throws Exception {
    service.notFoundClientOrderId = "cl-2";
    service.existingClientOrderId = "cl-1";
    service.existingOrderResponse =
        new OkxResponse<>(null, "0", null, List.of(orderDetails("ord-existing", "cl-1")));
    service.placeBatchResponse =
        new OkxResponse<>(null, "0", null, List.of(OkxOrderResponse.replay("ord-new-2", "cl-2")));

    OkxResponse<List<OkxOrderResponse>> result =
        service.placeOkxOrder(List.of(orderRequest("cl-1"), orderRequest("cl-2")));

    // cl-1 is replayed; the 51603 lookup for cl-2 is an empty lookup, so cl-2 is placed.
    assertThat(service.placeBatchCalls).isEqualTo(1);
    assertThat(service.lastPlacedBatch)
        .extracting(OkxOrderRequest::getClientOrderId)
        .containsExactly("cl-2");
    assertThat(result.getData())
        .extracting(OkxOrderResponse::getOrderId)
        .containsExactly("ord-existing", "ord-new-2");
  }

  @Test
  public void testPlaceBatchReconcilesOnlyAlreadyExistingOrders() throws Exception {
    service.existingClientOrderId = "cl-1";
    service.existingOrderResponse =
        new OkxResponse<>(null, "0", null, List.of(orderDetails("ord-existing", "cl-1")));
    service.placeBatchResponse =
        new OkxResponse<>(null, "0", null, List.of(new OkxOrderResponse(), new OkxOrderResponse()));

    OkxResponse<List<OkxOrderResponse>> result =
        service.placeOkxOrder(
            List.of(orderRequest("cl-1"), orderRequest("cl-2"), orderRequest("cl-3")));

    // cl-1 already exists and is replayed; cl-2 and cl-3 go to the batch placement.
    assertThat(service.placeBatchCalls).isEqualTo(1);
    assertThat(service.lastPlacedBatch)
        .extracting(OkxOrderRequest::getClientOrderId)
        .containsExactly("cl-2", "cl-3");
    assertThat(service.orderLookups).containsExactly("cl-1", "cl-2", "cl-3");
    assertThat(result.getData()).hasSize(3);
    assertThat(result.getData().get(0).getOrderId()).isEqualTo("ord-existing");
  }

  @Test
  public void testPlaceBatchPreservesInputOrderWhenExistingOrderIsNotFirst() throws Exception {
    service.existingClientOrderId = "cl-existing";
    service.existingOrderResponse =
        new OkxResponse<>(null, "0", null, List.of(orderDetails("ord-existing", "cl-existing")));
    service.placeBatchResponse =
        new OkxResponse<>(null, "0", null, List.of(OkxOrderResponse.replay("ord-new", "cl-new")));

    OkxResponse<List<OkxOrderResponse>> result =
        service.placeOkxOrder(List.of(orderRequest("cl-new"), orderRequest("cl-existing")));

    assertThat(result.getData())
        .extracting(OkxOrderResponse::getClientOrderId)
        .containsExactly("cl-new", "cl-existing");
  }

  @Test
  public void testPlaceBatchKeepsResponsesAlignedWhenExistingOrderIsInTheMiddle() throws Exception {
    service.existingClientOrderId = "cl-2";
    service.existingOrderResponse =
        new OkxResponse<>(null, "0", null, List.of(orderDetails("ord-existing", "cl-2")));
    service.placeBatchResponse =
        new OkxResponse<>(
            null,
            "0",
            null,
            List.of(
                OkxOrderResponse.replay("ord-new-1", "cl-1"),
                OkxOrderResponse.replay("ord-new-3", "cl-3")));

    OkxResponse<List<OkxOrderResponse>> result =
        service.placeOkxOrder(
            List.of(orderRequest("cl-1"), orderRequest("cl-2"), orderRequest("cl-3")));

    assertThat(result.getData())
        .extracting(OkxOrderResponse::getClientOrderId)
        .containsExactly("cl-1", "cl-2", "cl-3");
    assertThat(result.getData())
        .extracting(OkxOrderResponse::getOrderId)
        .containsExactly("ord-new-1", "ord-existing", "ord-new-3");
  }
}
