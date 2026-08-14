package org.knowm.xchange.okx.service;

import java.io.IOException;
import java.util.List;
import org.knowm.xchange.client.ResilienceRegistries;
import org.knowm.xchange.okx.OkxAuthenticated;
import org.knowm.xchange.okx.OkxExchange;
import org.knowm.xchange.okx.dto.OkxException;
import org.knowm.xchange.okx.dto.OkxResponse;
import org.knowm.xchange.okx.dto.account.OkxPosition;
import org.knowm.xchange.okx.dto.trade.OkxAmendOrderRequest;
import org.knowm.xchange.okx.dto.trade.OkxCancelOrderRequest;
import org.knowm.xchange.okx.dto.trade.OkxOrderDetails;
import org.knowm.xchange.okx.dto.trade.OkxOrderRequest;
import org.knowm.xchange.okx.dto.trade.OkxOrderResponse;

/** Author: Max Gao (gaamox@tutanota.com) Created: 08-06-2021 */
public class OkxTradeServiceRaw extends OkxBaseService {
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
    try {
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
                      null))
          .withRateLimiter((rateLimiter(OkxAuthenticated.orderDetailsPath)))
          .call();
    } catch (OkxException e) {
      throw handleError(e);
    }
  }

  public OkxResponse<List<OkxOrderDetails>> getOrderHistory(
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

  /** <a href="https://www.okx.com/docs-v5/en/#rest-api-trade-place-order">...</a> */
  public OkxResponse<List<OkxOrderResponse>> placeOkxOrder(OkxOrderRequest order)
      throws IOException {
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

  /** <a href="https://www.okx.com/docs-v5/en/#rest-api-trade-place-multiple-orders">...</a> */
  public OkxResponse<List<OkxOrderResponse>> placeOkxOrder(List<OkxOrderRequest> orders)
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
