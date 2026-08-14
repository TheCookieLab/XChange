package org.knowm.xchange.okx.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.knowm.xchange.client.ResilienceRegistries;
import org.knowm.xchange.okx.OkxAuthenticated;
import org.knowm.xchange.okx.OkxExchange;
import org.knowm.xchange.okx.dto.OkxException;
import org.knowm.xchange.okx.dto.OkxResponse;
import org.knowm.xchange.okx.dto.account.OkxPosition;
import org.knowm.xchange.okx.dto.trade.OkxAlgoOrderDetails;
import org.knowm.xchange.okx.dto.trade.OkxAlgoOrderRequest;
import org.knowm.xchange.okx.dto.trade.OkxAlgoOrderResponse;
import org.knowm.xchange.okx.dto.trade.OkxAmendAlgoRequest;
import org.knowm.xchange.okx.dto.trade.OkxAmendOrderRequest;
import org.knowm.xchange.okx.dto.trade.OkxCancelAlgoRequest;
import org.knowm.xchange.okx.dto.trade.OkxCancelOrderRequest;
import org.knowm.xchange.okx.dto.trade.OkxFill;
import org.knowm.xchange.okx.dto.trade.OkxOrderDetails;
import org.knowm.xchange.okx.dto.trade.OkxOrderRequest;
import org.knowm.xchange.okx.dto.trade.OkxOrderResponse;
import org.knowm.xchange.okx.dto.trade.OkxPageParams;

/** Author: Max Gao (gaamox@tutanota.com) Created: 08-06-2021 */
public class OkxTradeServiceRaw extends OkxBaseService {

  /** OKX business error for the order lookup when the order does not exist. */
  private static final int ORDER_NOT_FOUND_CODE = 51603;

  public OkxTradeServiceRaw(OkxExchange exchange, ResilienceRegistries resilienceRegistries) {
    super(exchange, resilienceRegistries);
  }

  public OkxResponse<List<OkxOrderDetails>> getOkxPendingOrder(
      String instrumentType,
      String underlying,
      String instrumentId,
      String orderType,
      String state,
      String after,
      String before,
      String limit)
      throws IOException {
    try {
      OkxAuthParams auth = authParams();
      return decorateApiCall(
              () ->
                  okxAuthenticated.getPendingOrders(
                      auth.apiKey(),
                      auth.signature(),
                      auth.timestamp(),
                      auth.passphrase(),
                      auth.simulatedTrading(),
                      instrumentType,
                      underlying,
                      instrumentId,
                      orderType,
                      state,
                      after,
                      before,
                      limit))
          .call();
    } catch (OkxException e) {
      throw handleError(e);
    }
  }

  public OkxResponse<List<OkxPosition>> getPositions(
      String instrumentType, String instrumentId, String positionId)
      throws OkxException, IOException {
    try {
      OkxAuthParams auth = authParams();
      return decorateApiCall(
              () ->
                  okxAuthenticated.getPositions(
                      instrumentType,
                      instrumentId,
                      positionId,
                      auth.apiKey(),
                      auth.signature(),
                      auth.timestamp(),
                      auth.passphrase(),
                      auth.simulatedTrading()))
          .withRateLimiter(rateLimiter(OkxAuthenticated.positionsPath))
          .call();
    } catch (OkxException e) {
      throw handleError(e);
    }
  }

  public OkxResponse<List<OkxOrderDetails>> getOkxOrder(String instrumentId, String orderId)
      throws IOException {
    return getOkxOrderByClientOrderId(instrumentId, orderId, null);
  }

  /**
   * Looks up an order by its client order id. OKX reports a missing order as error {@code 51603}
   * ("Order does not exist"); that result is returned as an empty lookup so replay-safe placement
   * can distinguish "never placed" from a real failure.
   *
   * @param instrumentId the instrument id, e.g. {@code BTC-USDT}
   * @param clientOrderId the client-supplied order id ({@code clOrdId})
   * @return the order details, or an empty data list when no order exists
   */
  OkxResponse<List<OkxOrderDetails>> getOkxOrderByClientOrderId(
      String instrumentId, String clientOrderId) throws IOException {
    return getOkxOrderByClientOrderId(instrumentId, null, clientOrderId);
  }

  private OkxResponse<List<OkxOrderDetails>> getOkxOrderByClientOrderId(
      String instrumentId, String orderId, String clientOrderId) throws IOException {
    try {
      return fetchOrderDetails(instrumentId, orderId, clientOrderId);
    } catch (OkxException e) {
      if (e.getCode() == ORDER_NOT_FOUND_CODE) {
        return new OkxResponse<>(null, "0", null, Collections.emptyList());
      }
      throw handleError(e);
    }
  }

  /**
   * HTTP seam for the order lookup ({@code GET /api/v5/trade/order}) so offline tests can stub the
   * response and the not-found handling can be exercised without a live account.
   *
   * @param instrumentId the instrument id, e.g. {@code BTC-USDT}
   * @param orderId the exchange order id ({@code ordId}), or {@code null} when looking up by client
   *     order id
   * @param clientOrderId the client-supplied order id ({@code clOrdId}), or {@code null} when
   *     looking up by exchange order id
   * @return the raw order lookup response
   * @throws IOException on transport failure
   */
  OkxResponse<List<OkxOrderDetails>> fetchOrderDetails(
      String instrumentId, String orderId, String clientOrderId) throws IOException {
    OkxAuthParams auth = authParams();
    return decorateApiCall(
            () ->
                okxAuthenticated.getOrderDetails(
                    auth.apiKey(),
                    auth.signature(),
                    auth.timestamp(),
                    auth.passphrase(),
                    auth.simulatedTrading(),
                    instrumentId,
                    orderId,
                    clientOrderId))
        .withRateLimiter((rateLimiter(OkxAuthenticated.orderDetailsPath)))
        .call();
  }

  public OkxResponse<List<OkxOrderDetails>> getOrderHistory(
      String instrumentType,
      String instrumentId,
      String orderType,
      String after,
      String before,
      String limit)
      throws IOException {
    return fetchOrderHistoryPage(instrumentType, instrumentId, orderType, after, before, limit);
  }

  /**
   * Fetches filled order history for the given instrument, iterating pages of bounded size until a
   * partial page, an empty page, or no forward progress is observed.
   *
   * <p>Only filled orders ({@code state=filled}) are returned, matching {@link
   * #getOrderHistory(String, String, String, String, String, String)}.
   *
   * @param instrumentType the instrument type, e.g. {@code SPOT}, or {@code null} for all
   * @param instrumentId the instrument id, e.g. {@code BTC-USDT}, or {@code null} for all
   * @param orderType the order type, or {@code null} for all
   * @param pagination typed pagination bounds (the limit is capped at 100)
   */
  public OkxResponse<List<OkxOrderDetails>> getOrderHistory(
      String instrumentType, String instrumentId, String orderType, OkxPageParams pagination)
      throws IOException {
    List<OkxOrderDetails> items =
        OkxPageIterator.fetchAll(
            page ->
                fetchOrderHistoryPage(
                        instrumentType,
                        instrumentId,
                        orderType,
                        page.getAfter(),
                        page.getBefore(),
                        String.valueOf(page.getLimit()))
                    .getData(),
            OkxOrderDetails::getOrderId,
            pagination);
    return new OkxResponse<>(null, "0", null, items);
  }

  OkxResponse<List<OkxOrderDetails>> fetchOrderHistoryPage(
      String instrumentType,
      String instrumentId,
      String orderType,
      String after,
      String before,
      String limit)
      throws IOException {
    try {
      OkxAuthParams auth = authParams();
      return decorateApiCall(
              () ->
                  okxAuthenticated.getOrderHistory(
                      instrumentType,
                      instrumentId,
                      orderType,
                      "filled",
                      after,
                      before,
                      limit,
                      auth.apiKey(),
                      auth.signature(),
                      auth.timestamp(),
                      auth.passphrase(),
                      auth.simulatedTrading()))
          .withRateLimiter((rateLimiter(OkxAuthenticated.orderDetailsPath)))
          .call();
    } catch (OkxException e) {
      throw handleError(e);
    }
  }

  /**
   * Returns the most recent fills for an instrument or order via {@code /trade/fills} (last three
   * days).
   *
   * @param instrumentType the instrument type, or {@code null}
   * @param instrumentId the instrument id, or {@code null}
   * @param orderId the exchange order id to correlate fills for, or {@code null}
   * @param after records earlier than this {@code billId}, or {@code null}
   * @param before records newer than this {@code billId}, or {@code null}
   * @param limit page size (capped at 100 by the caller when non-null), or {@code null}
   */
  public OkxResponse<List<OkxFill>> getOkxFill(
      String instrumentType,
      String instrumentId,
      String orderId,
      String after,
      String before,
      String limit)
      throws IOException {
    return fetchFillsPage(instrumentType, instrumentId, orderId, after, before, limit);
  }

  OkxResponse<List<OkxFill>> fetchFillsPage(
      String instrumentType,
      String instrumentId,
      String orderId,
      String after,
      String before,
      String limit)
      throws IOException {
    try {
      OkxAuthParams auth = authParams();
      return decorateApiCall(
              () ->
                  okxAuthenticated.getFills(
                      instrumentType,
                      instrumentId,
                      orderId,
                      after,
                      before,
                      limit,
                      auth.apiKey(),
                      auth.signature(),
                      auth.timestamp(),
                      auth.passphrase(),
                      auth.simulatedTrading()))
          .withRateLimiter(rateLimiter(OkxAuthenticated.fillsPath))
          .call();
    } catch (OkxException e) {
      throw handleError(e);
    }
  }

  /**
   * Returns fill history via {@code /trade/fills-history} (last three months), iterating pages of
   * bounded size until a partial page, an empty page, or no forward progress is observed.
   *
   * @param instrumentType the instrument type, or {@code null}
   * @param instrumentId the instrument id, or {@code null}
   * @param orderId the exchange order id to correlate fills for, or {@code null}
   * @param pagination typed pagination bounds (the limit is capped at 100)
   */
  public OkxResponse<List<OkxFill>> getOkxFillsHistory(
      String instrumentType, String instrumentId, String orderId, OkxPageParams pagination)
      throws IOException {
    List<OkxFill> items =
        OkxPageIterator.fetchAll(
            page ->
                fetchFillsHistoryPage(
                        instrumentType,
                        instrumentId,
                        orderId,
                        page.getAfter(),
                        page.getBefore(),
                        String.valueOf(page.getLimit()))
                    .getData(),
            OkxFill::getBillId,
            pagination);
    return new OkxResponse<>(null, "0", null, items);
  }

  OkxResponse<List<OkxFill>> fetchFillsHistoryPage(
      String instrumentType,
      String instrumentId,
      String orderId,
      String after,
      String before,
      String limit)
      throws IOException {
    try {
      OkxAuthParams auth = authParams();
      return decorateApiCall(
              () ->
                  okxAuthenticated.getFillsHistory(
                      instrumentType,
                      instrumentId,
                      orderId,
                      after,
                      before,
                      limit,
                      auth.apiKey(),
                      auth.signature(),
                      auth.timestamp(),
                      auth.passphrase(),
                      auth.simulatedTrading()))
          .withRateLimiter(rateLimiter(OkxAuthenticated.fillsHistoryPath))
          .call();
    } catch (OkxException e) {
      throw handleError(e);
    }
  }

  /** <a href="https://www.okx.com/docs-v5/en/#rest-api-trade-place-algo-order">...</a> */
  public OkxResponse<List<OkxAlgoOrderResponse>> placeOkxAlgoOrder(OkxAlgoOrderRequest order)
      throws IOException {
    try {
      OkxAuthParams auth = authParams();
      return decorateApiCall(
              () ->
                  okxAuthenticated.placeAlgoOrder(
                      auth.apiKey(),
                      auth.signature(),
                      auth.timestamp(),
                      auth.passphrase(),
                      auth.simulatedTrading(),
                      order))
          .withRateLimiter(rateLimiter(OkxAuthenticated.orderAlgoPath))
          .call();
    } catch (OkxException e) {
      throw handleError(e);
    }
  }

  /** <a href="https://www.okx.com/docs-v5/en/#rest-api-trade-cancel-algo-order">...</a> */
  public OkxResponse<List<OkxAlgoOrderResponse>> cancelOkxAlgoOrder(
      List<OkxCancelAlgoRequest> orders) throws IOException {
    try {
      OkxAuthParams auth = authParams();
      return decorateApiCall(
              () ->
                  okxAuthenticated.cancelAlgoOrders(
                      auth.apiKey(),
                      auth.signature(),
                      auth.timestamp(),
                      auth.passphrase(),
                      auth.simulatedTrading(),
                      orders))
          .withRateLimiter(rateLimiter(OkxAuthenticated.cancelAlgosPath))
          .call();
    } catch (OkxException e) {
      throw handleError(e);
    }
  }

  /** <a href="https://www.okx.com/docs-v5/en/#rest-api-trade-amend-algo-order">...</a> */
  public OkxResponse<List<OkxAlgoOrderResponse>> amendOkxAlgoOrder(List<OkxAmendAlgoRequest> orders)
      throws IOException {
    try {
      OkxAuthParams auth = authParams();
      return decorateApiCall(
              () ->
                  okxAuthenticated.amendAlgoOrders(
                      auth.apiKey(),
                      auth.signature(),
                      auth.timestamp(),
                      auth.passphrase(),
                      auth.simulatedTrading(),
                      orders))
          .withRateLimiter(rateLimiter(OkxAuthenticated.amendAlgosPath))
          .call();
    } catch (OkxException e) {
      throw handleError(e);
    }
  }

  /**
   * Returns pending algo orders via {@code /trade/orders-algo-pending}, iterating pages of bounded
   * size until a partial page, an empty page, or no forward progress is observed.
   *
   * @param instrumentType the instrument type, or {@code null}
   * @param instrumentId the instrument id, or {@code null}
   * @param orderType the algo order type, or {@code null}
   * @param pagination typed pagination bounds (the limit is capped at 100)
   */
  public OkxResponse<List<OkxAlgoOrderDetails>> getAlgoOrdersPending(
      String instrumentType, String instrumentId, String orderType, OkxPageParams pagination)
      throws IOException {
    List<OkxAlgoOrderDetails> items =
        OkxPageIterator.fetchAll(
            page ->
                fetchAlgoOrdersPendingPage(
                        instrumentType,
                        instrumentId,
                        orderType,
                        page.getAfter(),
                        page.getBefore(),
                        String.valueOf(page.getLimit()))
                    .getData(),
            OkxAlgoOrderDetails::getOrderId,
            pagination);
    return new OkxResponse<>(null, "0", null, items);
  }

  OkxResponse<List<OkxAlgoOrderDetails>> fetchAlgoOrdersPendingPage(
      String instrumentType,
      String instrumentId,
      String orderType,
      String after,
      String before,
      String limit)
      throws IOException {
    try {
      OkxAuthParams auth = authParams();
      return decorateApiCall(
              () ->
                  okxAuthenticated.getAlgoOrdersPending(
                      instrumentType,
                      instrumentId,
                      orderType,
                      after,
                      before,
                      limit,
                      auth.apiKey(),
                      auth.signature(),
                      auth.timestamp(),
                      auth.passphrase(),
                      auth.simulatedTrading()))
          .withRateLimiter(rateLimiter(OkxAuthenticated.ordersAlgoPendingPath))
          .call();
    } catch (OkxException e) {
      throw handleError(e);
    }
  }

  /**
   * Returns algo order history via {@code /trade/orders-algo-history}, iterating pages of bounded
   * size until a partial page, an empty page, or no forward progress is observed.
   *
   * @param instrumentType the instrument type, or {@code null}
   * @param instrumentId the instrument id, or {@code null}
   * @param orderType the algo order type, or {@code null}
   * @param state the order state, or {@code null}
   * @param pagination typed pagination bounds (the limit is capped at 100)
   */
  public OkxResponse<List<OkxAlgoOrderDetails>> getAlgoOrdersHistory(
      String instrumentType,
      String instrumentId,
      String orderType,
      String state,
      OkxPageParams pagination)
      throws IOException {
    List<OkxAlgoOrderDetails> items =
        OkxPageIterator.fetchAll(
            page ->
                fetchAlgoOrdersHistoryPage(
                        instrumentType,
                        instrumentId,
                        orderType,
                        state,
                        page.getAfter(),
                        page.getBefore(),
                        String.valueOf(page.getLimit()))
                    .getData(),
            OkxAlgoOrderDetails::getOrderId,
            pagination);
    return new OkxResponse<>(null, "0", null, items);
  }

  OkxResponse<List<OkxAlgoOrderDetails>> fetchAlgoOrdersHistoryPage(
      String instrumentType,
      String instrumentId,
      String orderType,
      String state,
      String after,
      String before,
      String limit)
      throws IOException {
    try {
      OkxAuthParams auth = authParams();
      return decorateApiCall(
              () ->
                  okxAuthenticated.getAlgoOrdersHistory(
                      instrumentType,
                      instrumentId,
                      orderType,
                      state,
                      after,
                      before,
                      limit,
                      auth.apiKey(),
                      auth.signature(),
                      auth.timestamp(),
                      auth.passphrase(),
                      auth.simulatedTrading()))
          .withRateLimiter(rateLimiter(OkxAuthenticated.ordersAlgoHistoryPath))
          .call();
    } catch (OkxException e) {
      throw handleError(e);
    }
  }

  /**
   * Places a single order, but first reconciles against any existing order with the same client
   * order id ({@code clOrdId}) to avoid blind re-submission of an ambiguous (already-submitted)
   * order.
   *
   * <p>Idempotent placement: when the request carries a non-null {@code clOrdId} and an order with
   * that client order id already exists, the existing order is returned instead of placing a new
   * one. Callers that supply a fresh {@code clOrdId} per logical order therefore never create
   * duplicates on retry after an ambiguous timeout.
   *
   * @param order the order to place
   * @return the placement response, or the existing order when it was already placed under the same
   *     client order id
   */
  public OkxResponse<List<OkxOrderResponse>> placeOkxOrder(OkxOrderRequest order)
      throws IOException {
    return placeOkxOrderWithReconciliation(order);
  }

  /** Testable seam backing {@link #placeOkxOrder(OkxOrderRequest)}. */
  OkxResponse<List<OkxOrderResponse>> placeOkxOrderWithReconciliation(OkxOrderRequest order)
      throws IOException {
    String clOrdId = order.getClientOrderId();
    if (clOrdId != null && !clOrdId.isEmpty() && order.getInstrumentId() != null) {
      List<OkxOrderDetails> existing =
          getOkxOrderByClientOrderId(order.getInstrumentId(), clOrdId).getData();
      if (existing != null && !existing.isEmpty()) {
        return idempotentReplay(existing.get(0));
      }
    }
    return doPlaceOkxOrder(order);
  }

  /** Testable seam backing the actual single-order placement HTTP call. */
  OkxResponse<List<OkxOrderResponse>> doPlaceOkxOrder(OkxOrderRequest order) throws IOException {
    try {
      OkxAuthParams auth = authParams();
      return decorateApiCall(
              () ->
                  okxAuthenticated.placeOrder(
                      auth.apiKey(),
                      auth.signature(),
                      auth.timestamp(),
                      auth.passphrase(),
                      auth.simulatedTrading(),
                      order))
          .withRateLimiter(rateLimiter(OkxAuthenticated.placeOrderPath))
          .call();
    } catch (OkxException e) {
      throw handleError(e);
    }
  }

  /**
   * Places a batch of orders, reconciling each order independently against any existing order with
   * the same client order id. Already-placed orders are returned from their existing record rather
   * than re-submitted.
   *
   * <p>The returned response list is aligned with the input order list.
   *
   * @param orders the orders to place
   * @return one placement (or replay) response per input order
   */
  public OkxResponse<List<OkxOrderResponse>> placeOkxOrder(List<OkxOrderRequest> orders)
      throws IOException {
    return placeOkxOrderWithReconciliation(orders);
  }

  /** Testable seam backing {@link #placeOkxOrder(List)}. */
  OkxResponse<List<OkxOrderResponse>> placeOkxOrderWithReconciliation(List<OkxOrderRequest> orders)
      throws IOException {
    List<OkxOrderRequest> toPlace = new ArrayList<>();
    List<Integer> toPlaceIndexes = new ArrayList<>();
    List<OkxOrderResponse> responses = new ArrayList<>(Collections.nCopies(orders.size(), null));
    for (int index = 0; index < orders.size(); index++) {
      OkxOrderRequest order = orders.get(index);
      String clOrdId = order.getClientOrderId();
      if (clOrdId != null && !clOrdId.isEmpty() && order.getInstrumentId() != null) {
        List<OkxOrderDetails> existing =
            getOkxOrderByClientOrderId(order.getInstrumentId(), clOrdId).getData();
        if (existing != null && !existing.isEmpty()) {
          OkxOrderDetails found = existing.get(0);
          responses.set(
              index, OkxOrderResponse.replay(found.getOrderId(), found.getClientOrderId()));
          continue;
        }
      }
      toPlace.add(order);
      toPlaceIndexes.add(index);
    }

    if (!toPlace.isEmpty()) {
      List<OkxOrderResponse> placed = doPlaceBatchOkxOrder(toPlace).getData();
      for (int index = 0; index < toPlaceIndexes.size(); index++) {
        responses.set(toPlaceIndexes.get(index), placed.get(index));
      }
    }
    return new OkxResponse<>(null, "0", null, responses);
  }

  /** Testable seam backing the actual batch placement HTTP call. */
  OkxResponse<List<OkxOrderResponse>> doPlaceBatchOkxOrder(List<OkxOrderRequest> orders)
      throws IOException {
    try {
      OkxAuthParams auth = authParams();
      return decorateApiCall(
              () ->
                  okxAuthenticated.placeBatchOrder(
                      auth.apiKey(),
                      auth.signature(),
                      auth.timestamp(),
                      auth.passphrase(),
                      auth.simulatedTrading(),
                      orders))
          .withRateLimiter(rateLimiter(OkxAuthenticated.placeBatchOrderPath))
          .call();
    } catch (OkxException e) {
      throw handleError(e);
    }
  }

  private OkxResponse<List<OkxOrderResponse>> idempotentReplay(OkxOrderDetails details) {
    return new OkxResponse<>(
        null,
        "0",
        null,
        Collections.singletonList(
            OkxOrderResponse.replay(details.getOrderId(), details.getClientOrderId())));
  }

  /** <a href="https://www.okx.com/docs-v5/en/#rest-api-trade-cancel-order">...</a> */
  public OkxResponse<List<OkxOrderResponse>> cancelOkxOrder(OkxCancelOrderRequest order)
      throws IOException {
    try {
      OkxAuthParams auth = authParams();
      return decorateApiCall(
              () ->
                  okxAuthenticated.cancelOrder(
                      auth.apiKey(),
                      auth.signature(),
                      auth.timestamp(),
                      auth.passphrase(),
                      auth.simulatedTrading(),
                      order))
          .withRateLimiter(rateLimiter(OkxAuthenticated.cancelOrderPath))
          .call();
    } catch (OkxException e) {
      throw handleError(e);
    }
  }

  /** <a href="https://www.okx.com/docs-v5/en/#rest-api-trade-cancel-multiple-orders">...</a> */
  public OkxResponse<List<OkxOrderResponse>> cancelOkxOrder(List<OkxCancelOrderRequest> orders)
      throws IOException {
    try {
      OkxAuthParams auth = authParams();
      return decorateApiCall(
              () ->
                  okxAuthenticated.cancelBatchOrder(
                      auth.apiKey(),
                      auth.signature(),
                      auth.timestamp(),
                      auth.passphrase(),
                      auth.simulatedTrading(),
                      orders))
          .withRateLimiter(rateLimiter(OkxAuthenticated.cancelBatchOrderPath))
          .call();
    } catch (OkxException e) {
      throw handleError(e);
    }
  }

  /** <a href="https://www.okx.com/docs-v5/en/#rest-api-trade-amend-order">...</a> */
  public OkxResponse<List<OkxOrderResponse>> amendOkxOrder(OkxAmendOrderRequest order)
      throws IOException {
    try {
      OkxAuthParams auth = authParams();
      return decorateApiCall(
              () ->
                  okxAuthenticated.amendOrder(
                      auth.apiKey(),
                      auth.signature(),
                      auth.timestamp(),
                      auth.passphrase(),
                      auth.simulatedTrading(),
                      order))
          .withRateLimiter(rateLimiter(OkxAuthenticated.amendOrderPath))
          .call();
    } catch (OkxException e) {
      throw handleError(e);
    }
  }

  /** <a href="https://www.okx.com/docs-v5/en/#rest-api-trade-amend-multiple-orders">...</a> */
  public OkxResponse<List<OkxOrderResponse>> amendOkxOrder(List<OkxAmendOrderRequest> orders)
      throws IOException {
    try {
      OkxAuthParams auth = authParams();
      return decorateApiCall(
              () ->
                  okxAuthenticated.amendBatchOrder(
                      auth.apiKey(),
                      auth.signature(),
                      auth.timestamp(),
                      auth.passphrase(),
                      auth.simulatedTrading(),
                      orders))
          .withRateLimiter(rateLimiter(OkxAuthenticated.amendBatchOrderPath))
          .call();
    } catch (OkxException e) {
      throw handleError(e);
    }
  }
}
