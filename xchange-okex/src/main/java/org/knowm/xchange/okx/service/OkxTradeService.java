package org.knowm.xchange.okx.service;

import static org.knowm.xchange.okx.dto.OkxInstType.FUTURES;
import static org.knowm.xchange.okx.dto.OkxInstType.OPTION;
import static org.knowm.xchange.okx.dto.OkxInstType.SPOT;
import static org.knowm.xchange.okx.dto.OkxInstType.SWAP;

import jakarta.ws.rs.NotSupportedException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.knowm.xchange.client.ResilienceRegistries;
import org.knowm.xchange.derivative.FuturesContract;
import org.knowm.xchange.derivative.OptionsContract;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.account.OpenPositions;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.dto.trade.OpenOrders;
import org.knowm.xchange.dto.trade.UserTrades;
import org.knowm.xchange.exceptions.FundsExceededException;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.okx.OkxAdapters;
import org.knowm.xchange.okx.OkxAuthenticated;
import org.knowm.xchange.okx.OkxExchange;
import org.knowm.xchange.okx.dto.OkxException;
import org.knowm.xchange.okx.dto.OkxResponse;
import org.knowm.xchange.okx.dto.trade.OkxCancelOrderRequest;
import org.knowm.xchange.okx.dto.trade.OkxOrderDetails;
import org.knowm.xchange.okx.dto.trade.OkxOrderResponse;
import org.knowm.xchange.okx.dto.trade.OkxTradeParams;
import org.knowm.xchange.service.trade.TradeService;
import org.knowm.xchange.service.trade.params.CancelOrderByIdParams;
import org.knowm.xchange.service.trade.params.CancelOrderByInstrument;
import org.knowm.xchange.service.trade.params.CancelOrderByUserReferenceParams;
import org.knowm.xchange.service.trade.params.CancelOrderParams;
import org.knowm.xchange.service.trade.params.TradeHistoryParamInstrument;
import org.knowm.xchange.service.trade.params.TradeHistoryParams;
import org.knowm.xchange.service.trade.params.orders.OpenOrdersParamInstrument;
import org.knowm.xchange.service.trade.params.orders.OpenOrdersParams;
import org.knowm.xchange.service.trade.params.orders.OrderQueryParamInstrument;
import org.knowm.xchange.service.trade.params.orders.OrderQueryParams;

/** Author: Max Gao (gaamox@tutanota.com) Created: 08-06-2021 */
public class OkxTradeService extends OkxTradeServiceRaw implements TradeService {
  public OkxTradeService(OkxExchange exchange, ResilienceRegistries resilienceRegistries) {
    super(exchange, resilienceRegistries);
  }

  /**
   * Builds a structured business error from a failed order-placement response, carrying the
   * provider code, the API domain and endpoint, and the failed order's client order id (falling
   * back to the exchange order id) as the safe request identity.
   *
   * @param response the non-success order response
   * @param endpoint the endpoint path that produced the failure, e.g. {@link
   *     OkxAuthenticated#placeOrderPath}
   */
  /**
   * Maps an instrument to the {@code instType} OKX expects for order-history queries: SPOT/MARGIN
   * pairs query {@code SPOT}, perpetual swaps query {@code SWAP}, dated futures query {@code
   * FUTURES}, and options query {@code OPTION}.
   *
   * @param instrument the instrument to map
   * @return the OKX instrument type name
   */
  static String historyInstrumentType(Instrument instrument) {
    if (instrument instanceof FuturesContract) {
      return ((FuturesContract) instrument).isPerpetual() ? SWAP.name() : FUTURES.name();
    }
    if (instrument instanceof OptionsContract) {
      return OPTION.name();
    }
    return SPOT.name();
  }

  /** Testable seam backing order-placement error mapping (see {@link OkxTradeServiceTest}). */
  OkxException orderException(OkxResponse<List<OkxOrderResponse>> response, String endpoint) {
    String code;
    String message;
    String requestId = null;
    List<OkxOrderResponse> data = response.getData();
    if (data != null && !data.isEmpty()) {
      OkxOrderResponse failed = data.get(0);
      code = failed.getCode();
      message = failed.getMessage();
      requestId =
          failed.getClientOrderId() != null ? failed.getClientOrderId() : failed.getOrderId();
    } else {
      // Top-level failure (authentication, request-wide validation): no per-order entry.
      code = response.getCode();
      message = response.getMsg();
    }
    return OkxException.builder()
        .message(redact(message))
        .code(OkxException.parseCode(code))
        .domain("trade")
        .endpoint(endpoint)
        .requestId(requestId)
        .transportState(OkxException.TransportState.BUSINESS_ERROR)
        .retryClassification(OkxException.classify(message, code))
        .build();
  }

  @Override
  public OpenPositions getOpenPositions() throws IOException {
    return OkxAdapters.adaptOpenPositions(
        getPositions(null, null, null).getData(), exchange.getExchangeMetaData());
  }

  @Override
  public UserTrades getTradeHistory(TradeHistoryParams params) throws IOException {
    if (params instanceof TradeHistoryParamInstrument) {
      Instrument instrument = ((TradeHistoryParamInstrument) params).getInstrument();

      String instrumentType = historyInstrumentType(instrument);

      return OkxAdapters.adaptUserTrades(
          getOrderHistory(
                  instrumentType,
                  OkxAdapters.adaptInstrument(
                      ((TradeHistoryParamInstrument) params).getInstrument()),
                  null,
                  null,
                  null,
                  null)
              .getData(),
          exchange.getExchangeMetaData());
    } else {
      throw new NotSupportedException(
          "TradeHistoryParams must implement " + TradeHistoryParamInstrument.class.getSimpleName());
    }
  }

  @Override
  public OpenOrders getOpenOrders() throws IOException {
    return OkxAdapters.adaptOpenOrders(
        getOkxPendingOrder(null, null, null, null, null, null, null, null).getData(),
        exchange.getExchangeMetaData());
  }

  @Override
  public OpenOrders getOpenOrders(OpenOrdersParams params) throws IOException {
    if (params instanceof OpenOrdersParamInstrument) {
      return OkxAdapters.adaptOpenOrders(
          getOkxPendingOrder(
                  null,
                  null,
                  OkxAdapters.adaptInstrument(((OpenOrdersParamInstrument) params).getInstrument()),
                  null,
                  null,
                  null,
                  null,
                  null)
              .getData(),
          exchange.getExchangeMetaData());
    } else {
      throw new NotSupportedException(
          "OpenOrdersParam must implement " + OpenOrdersParamInstrument.class.getSimpleName());
    }
  }

  @Override
  public Class getRequiredOrderQueryParamClass() {
    return OrderQueryParamInstrument.class;
  }

  public Order getOrder(OrderQueryParams orderQueryParams) throws IOException {
    Order result = null;
    if (orderQueryParams instanceof OrderQueryParamInstrument) {
      Instrument instrument = ((OrderQueryParamInstrument) orderQueryParams).getInstrument();
      String orderId = orderQueryParams.getOrderId();

      List<OkxOrderDetails> orderResults =
          getOkxOrder(OkxAdapters.adaptInstrument(instrument), orderId).getData();

      if (!orderResults.isEmpty()) {
        result = OkxAdapters.adaptOrder(orderResults.get(0), exchange.getExchangeMetaData());
      }
    } else {
      throw new IOException("OrderQueryParams must implement OrderQueryParamInstrument interface.");
    }
    return result;
  }

  @Override
  public Collection<Order> getOrder(OrderQueryParams... orderQueryParams) throws IOException {
    ArrayList<Order> result = new ArrayList<>();
    for (OrderQueryParams orderQueryParam : orderQueryParams) {
      Order order = getOrder(orderQueryParam);
      if (order != null) {
        result.add(order);
      }
    }
    return result;
  }

  /**
   * Returns whether a single-order operation actually succeeded: the envelope must carry a
   * per-order entry whose {@code sCode} is {@code "0"}. OKX can return a top-level code of {@code
   * 0} while rejecting the individual order with a nonzero {@code sCode}, in which case the
   * response must not be treated as a successful placement.
   *
   * @param response the order-operation response envelope
   * @return {@code true} when the first per-order entry reports success
   */
  static boolean perOrderSucceeded(OkxResponse<List<OkxOrderResponse>> response) {
    List<OkxOrderResponse> data = response.getData();
    return data != null && !data.isEmpty() && "0".equals(data.get(0).getCode());
  }

  @Override
  public String placeMarketOrder(MarketOrder marketOrder) throws IOException {
    OkxResponse<List<OkxOrderResponse>> okxResponse =
        placeOkxOrder(
            OkxAdapters.adaptOrder(
                marketOrder, exchange.getExchangeMetaData(), exchange.accountLevel));

    if (okxResponse.isSuccess() && perOrderSucceeded(okxResponse))
      return okxResponse.getData().get(0).getOrderId();
    else throw orderException(okxResponse, OkxAuthenticated.placeOrderPath);
  }

  @Override
  public String placeLimitOrder(LimitOrder limitOrder) throws IOException, FundsExceededException {
    OkxResponse<List<OkxOrderResponse>> okxResponse =
        placeOkxOrder(
            OkxAdapters.adaptOrder(
                limitOrder, exchange.getExchangeMetaData(), exchange.accountLevel));

    if (okxResponse.isSuccess() && perOrderSucceeded(okxResponse))
      return okxResponse.getData().get(0).getOrderId();
    else throw orderException(okxResponse, OkxAuthenticated.placeOrderPath);
  }

  public List<String> placeLimitOrder(List<LimitOrder> limitOrders)
      throws IOException, FundsExceededException {
    return placeOkxOrder(
            limitOrders.stream()
                .map(
                    order ->
                        OkxAdapters.adaptOrder(
                            order, exchange.getExchangeMetaData(), exchange.accountLevel))
                .collect(Collectors.toList()))
        .getData()
        .stream()
        .map(OkxOrderResponse::getOrderId)
        .collect(Collectors.toList());
  }

  @Override
  public String changeOrder(LimitOrder limitOrder) throws IOException, FundsExceededException {
    OkxResponse<List<OkxOrderResponse>> okxResponse =
        amendOkxOrder(OkxAdapters.adaptAmendOrder(limitOrder, exchange.getExchangeMetaData()));
    if (okxResponse.isSuccess() && perOrderSucceeded(okxResponse))
      return okxResponse.getData().get(0).getOrderId();
    else throw orderException(okxResponse, OkxAuthenticated.amendOrderPath);
  }

  public List<String> changeOrder(List<LimitOrder> limitOrders)
      throws IOException, FundsExceededException {
    OkxResponse<List<OkxOrderResponse>> okxResponse =
        amendOkxOrder(
            limitOrders.stream()
                .map(order -> OkxAdapters.adaptAmendOrder(order, exchange.getExchangeMetaData()))
                .collect(Collectors.toList()));
    if (!okxResponse.isSuccess())
      throw orderException(okxResponse, OkxAuthenticated.amendOrderPath);
    return okxResponse.getData().stream()
        .map(OkxOrderResponse::getOrderId)
        .collect(Collectors.toList());
  }

  @Override
  public boolean cancelOrder(CancelOrderParams params) throws IOException {
    if (params instanceof OkxTradeParams.OkxCancelOrderParams) {
      Instrument instrument = ((CancelOrderByInstrument) params).getInstrument();
      if (instrument == null) {
        throw new UnsupportedOperationException(
            "Instrument and (orderId or userReference) required");
      }
      String orderId = ((CancelOrderByIdParams) params).getOrderId();
      String userReference = ((CancelOrderByUserReferenceParams) params).getUserReference();
      if ((orderId == null || orderId.isEmpty())
          && (userReference == null || userReference.isEmpty())) {
        throw new UnsupportedOperationException("OrderId or userReference is required");
      }
      String id = ((CancelOrderByIdParams) params).getOrderId();
      String instrumentId =
          OkxAdapters.adaptInstrument(((CancelOrderByInstrument) params).getInstrument());

      OkxCancelOrderRequest req =
          OkxCancelOrderRequest.builder()
              .instrumentId(instrumentId)
              .orderId(id)
              .clientOrderId(userReference)
              .build();
      OkxResponse<List<OkxOrderResponse>> okxResponse = cancelOkxOrder(req);
      if (okxResponse.isSuccess() && perOrderSucceeded(okxResponse)) return true;
      else throw orderException(okxResponse, OkxAuthenticated.cancelOrderPath);
    } else {
      throw new IOException(
          "CancelOrderParams must implement (CancelOrderByIdParams or CancelOrderByUserReferenceParams) and CancelOrderByInstrument interface.");
    }
  }

  @Override
  public Class[] getRequiredCancelOrderParamClasses() {
    return new Class[] {CancelOrderByIdParams.class, CancelOrderByInstrument.class};
  }

  public List<Boolean> cancelOrder(List<CancelOrderParams> params) throws IOException {
    OkxResponse<List<OkxOrderResponse>> okxResponse =
        cancelOkxOrder(
            params.stream()
                .map(
                    param ->
                        OkxCancelOrderRequest.builder()
                            .orderId(((CancelOrderByIdParams) param).getOrderId())
                            .instrumentId(
                                OkxAdapters.adaptInstrument(
                                    ((CancelOrderByInstrument) param).getInstrument()))
                            .build())
                .collect(Collectors.toList()));
    if (!okxResponse.isSuccess())
      throw orderException(okxResponse, OkxAuthenticated.cancelOrderPath);
    return okxResponse.getData().stream()
        .map(result -> "0".equals(result.getCode()))
        .collect(Collectors.toList());
  }
}
