package org.knowm.xchange.okex.service;

import java.io.IOException;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.knowm.xchange.client.ResilienceRegistries;
import org.knowm.xchange.okex.OkexExchange;
import org.knowm.xchange.okex.dto.OkexException;
import org.knowm.xchange.okex.dto.OkexResponse;
import org.knowm.xchange.okex.dto.account.OkexPosition;
import org.knowm.xchange.okex.dto.trade.OkexAmendOrderRequest;
import org.knowm.xchange.okex.dto.trade.OkexCancelOrderRequest;
import org.knowm.xchange.okex.dto.trade.OkexOrderDetails;
import org.knowm.xchange.okex.dto.trade.OkexOrderRequest;
import org.knowm.xchange.okex.dto.trade.OkexOrderResponse;
import org.knowm.xchange.okx.dto.OkxException;
import org.knowm.xchange.okx.dto.OkxResponse;
import org.knowm.xchange.okx.service.OkxBaseService;
import org.knowm.xchange.okx.service.OkxTradeServiceRaw;

/**
 * @deprecated use {@link org.knowm.xchange.okx.service.OkxTradeServiceRaw} instead.
 */
@Deprecated
public class OkexTradeServiceRaw extends OkxBaseService {

  private final OkxTradeServiceRaw delegate;

  public OkexTradeServiceRaw(OkexExchange exchange, ResilienceRegistries resilienceRegistries) {
    super(exchange, resilienceRegistries);
    this.delegate = new OkxTradeServiceRaw(exchange, resilienceRegistries);
  }

  private static <S, T> OkexResponse<List<T>> wrap(
      OkxResponse<List<S>> response, Function<S, T> mapper) {
    return new OkexResponse<>(
        new OkxResponse<>(
            response.getId(),
            response.getCode(),
            response.getMsg(),
            response.getData().stream().map(mapper).collect(Collectors.toList())));
  }

  public OkexResponse<List<OkexOrderDetails>> getOkexPendingOrder(
      String instrumentType,
      String underlying,
      String instrumentId,
      String orderType,
      String state,
      String after,
      String before,
      String limit)
      throws IOException {
    return wrap(
        delegate.getOkxPendingOrder(
            instrumentType, underlying, instrumentId, orderType, state, after, before, limit),
        OkexOrderDetails::new);
  }

  public OkexResponse<List<OkexPosition>> getPositions(
      String instrumentType, String instrumentId, String positionId)
      throws OkexException, IOException {
    try {
      return wrap(
          delegate.getPositions(instrumentType, instrumentId, positionId), OkexPosition::new);
    } catch (OkxException e) {
      throw new OkexException(e);
    }
  }

  public OkexResponse<List<OkexOrderDetails>> getOkexOrder(String instrumentId, String orderId)
      throws IOException {
    return wrap(delegate.getOkxOrder(instrumentId, orderId), OkexOrderDetails::new);
  }

  public OkexResponse<List<OkexOrderDetails>> getOrderHistory(
      String instrumentType,
      String instrumentId,
      String orderType,
      String after,
      String before,
      String limit)
      throws IOException {
    return wrap(
        delegate.getOrderHistory(instrumentType, instrumentId, orderType, after, before, limit),
        OkexOrderDetails::new);
  }

  public OkexResponse<List<OkexOrderResponse>> placeOkexOrder(OkexOrderRequest order)
      throws IOException {
    return wrap(delegate.placeOkxOrder(order.to()), OkexOrderResponse::new);
  }

  public OkexResponse<List<OkexOrderResponse>> placeOkexOrder(List<OkexOrderRequest> orders)
      throws IOException {
    return wrap(
        delegate.placeOkxOrder(
            orders.stream().map(OkexOrderRequest::to).collect(Collectors.toList())),
        OkexOrderResponse::new);
  }

  public OkexResponse<List<OkexOrderResponse>> cancelOkexOrder(OkexCancelOrderRequest order)
      throws IOException {
    return wrap(delegate.cancelOkxOrder(order.to()), OkexOrderResponse::new);
  }

  public OkexResponse<List<OkexOrderResponse>> cancelOkexOrder(List<OkexCancelOrderRequest> orders)
      throws IOException {
    return wrap(
        delegate.cancelOkxOrder(
            orders.stream().map(OkexCancelOrderRequest::to).collect(Collectors.toList())),
        OkexOrderResponse::new);
  }

  public OkexResponse<List<OkexOrderResponse>> amendOkexOrder(OkexAmendOrderRequest order)
      throws IOException {
    return wrap(delegate.amendOkxOrder(order.to()), OkexOrderResponse::new);
  }

  public OkexResponse<List<OkexOrderResponse>> amendOkexOrder(List<OkexAmendOrderRequest> orders)
      throws IOException {
    return wrap(
        delegate.amendOkxOrder(
            orders.stream().map(OkexAmendOrderRequest::to).collect(Collectors.toList())),
        OkexOrderResponse::new);
  }
}
