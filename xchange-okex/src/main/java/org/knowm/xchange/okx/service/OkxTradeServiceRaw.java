package org.knowm.xchange.okx.service;

import static org.knowm.xchange.okx.OkxExchange.PARAM_PASSPHRASE;
import static org.knowm.xchange.okx.OkxExchange.PARAM_SIMULATED;

import java.io.IOException;
import java.util.Date;
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
import org.knowm.xchange.utils.DateUtils;

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
      return decorateApiCall(
              () ->
                  okxAuthenticated.getPendingOrders(
                      exchange.getExchangeSpecification().getApiKey(),
                      signatureCreator,
                      DateUtils.toUTCISODateString(new Date()),
                      (String)
                          exchange
                              .getExchangeSpecification()
                              .getExchangeSpecificParametersItem(PARAM_PASSPHRASE),
                      (String)
                          exchange
                              .getExchangeSpecification()
                              .getExchangeSpecificParametersItem(PARAM_SIMULATED),
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
      return decorateApiCall(
              () ->
                  okxAuthenticated.getPositions(
                      instrumentType,
                      instrumentId,
                      positionId,
                      exchange.getExchangeSpecification().getApiKey(),
                      signatureCreator,
                      DateUtils.toUTCISODateString(new Date()),
                      (String)
                          exchange
                              .getExchangeSpecification()
                              .getExchangeSpecificParametersItem(PARAM_PASSPHRASE),
                      (String)
                          exchange
                              .getExchangeSpecification()
                              .getExchangeSpecificParametersItem(PARAM_SIMULATED)))
          .withRateLimiter(rateLimiter(OkxAuthenticated.positionsPath))
          .call();
    } catch (OkxException e) {
      throw handleError(e);
    }
  }

  public OkxResponse<List<OkxOrderDetails>> getOkxOrder(String instrumentId, String orderId)
      throws IOException {
    try {
      return decorateApiCall(
              () ->
                  okxAuthenticated.getOrderDetails(
                      exchange.getExchangeSpecification().getApiKey(),
                      signatureCreator,
                      DateUtils.toUTCISODateString(new Date()),
                      (String)
                          exchange
                              .getExchangeSpecification()
                              .getExchangeSpecificParametersItem(PARAM_PASSPHRASE),
                      (String)
                          exchange
                              .getExchangeSpecification()
                              .getExchangeSpecificParametersItem(PARAM_SIMULATED),
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
                      exchange.getExchangeSpecification().getApiKey(),
                      signatureCreator,
                      DateUtils.toUTCISODateString(new Date()),
                      (String)
                          exchange
                              .getExchangeSpecification()
                              .getExchangeSpecificParametersItem(PARAM_PASSPHRASE),
                      (String)
                          exchange
                              .getExchangeSpecification()
                              .getExchangeSpecificParametersItem(PARAM_SIMULATED)))
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
      return decorateApiCall(
              () ->
                  okxAuthenticated.placeOrder(
                      exchange.getExchangeSpecification().getApiKey(),
                      signatureCreator,
                      DateUtils.toUTCISODateString(new Date()),
                      (String)
                          exchange
                              .getExchangeSpecification()
                              .getExchangeSpecificParametersItem(PARAM_PASSPHRASE),
                      (String)
                          exchange
                              .getExchangeSpecification()
                              .getExchangeSpecificParametersItem(PARAM_SIMULATED),
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
      return decorateApiCall(
              () ->
                  okxAuthenticated.placeBatchOrder(
                      exchange.getExchangeSpecification().getApiKey(),
                      signatureCreator,
                      DateUtils.toUTCISODateString(new Date()),
                      (String)
                          exchange
                              .getExchangeSpecification()
                              .getExchangeSpecificParametersItem(PARAM_PASSPHRASE),
                      (String)
                          exchange
                              .getExchangeSpecification()
                              .getExchangeSpecificParametersItem(PARAM_SIMULATED),
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
      return decorateApiCall(
              () ->
                  okxAuthenticated.cancelOrder(
                      exchange.getExchangeSpecification().getApiKey(),
                      signatureCreator,
                      DateUtils.toUTCISODateString(new Date()),
                      (String)
                          exchange
                              .getExchangeSpecification()
                              .getExchangeSpecificParametersItem(PARAM_PASSPHRASE),
                      (String)
                          exchange
                              .getExchangeSpecification()
                              .getExchangeSpecificParametersItem(PARAM_SIMULATED),
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
      return decorateApiCall(
              () ->
                  okxAuthenticated.cancelBatchOrder(
                      exchange.getExchangeSpecification().getApiKey(),
                      signatureCreator,
                      DateUtils.toUTCISODateString(new Date()),
                      (String)
                          exchange
                              .getExchangeSpecification()
                              .getExchangeSpecificParametersItem(PARAM_PASSPHRASE),
                      (String)
                          exchange
                              .getExchangeSpecification()
                              .getExchangeSpecificParametersItem(PARAM_SIMULATED),
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
      return decorateApiCall(
              () ->
                  okxAuthenticated.amendOrder(
                      exchange.getExchangeSpecification().getApiKey(),
                      signatureCreator,
                      DateUtils.toUTCISODateString(new Date()),
                      (String)
                          exchange
                              .getExchangeSpecification()
                              .getExchangeSpecificParametersItem(PARAM_PASSPHRASE),
                      (String)
                          exchange
                              .getExchangeSpecification()
                              .getExchangeSpecificParametersItem(PARAM_SIMULATED),
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
      return decorateApiCall(
              () ->
                  okxAuthenticated.amendBatchOrder(
                      exchange.getExchangeSpecification().getApiKey(),
                      signatureCreator,
                      DateUtils.toUTCISODateString(new Date()),
                      (String)
                          exchange
                              .getExchangeSpecification()
                              .getExchangeSpecificParametersItem(PARAM_PASSPHRASE),
                      (String)
                          exchange
                              .getExchangeSpecification()
                              .getExchangeSpecificParametersItem(PARAM_SIMULATED),
                      orders))
          .withRateLimiter(rateLimiter(OkxAuthenticated.amendBatchOrderPath))
          .call();
    } catch (OkxException e) {
      throw handleError(e);
    }
  }
}
