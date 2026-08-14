package org.knowm.xchange.okx.service;

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

  @Override
  public OpenPositions getOpenPositions() throws IOException {
    return OkxAdapters.adaptOpenPositions(
        getPositions(null, null, null).getData(), exchange.getExchangeMetaData());
  }

  @Override
  public UserTrades getTradeHistory(TradeHistoryParams params) throws IOException {
    if (params instanceof TradeHistoryParamInstrument) {
      Instrument instrument = ((TradeHistoryParamInstrument) params).getInstrument();

      String instrumentType = SPOT.name();
      if (instrument instanceof FuturesContract) {
        instrumentType = SWAP.name();
      } else if (instrument instanceof OptionsContract) {
        instrumentType = OPTION.name();
      }

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
                  OkxAdapters.adaptInstrument(
                      ((OpenOrdersParamInstrument) params).getInstrument()),
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

  @Override
  public String placeMarketOrder(MarketOrder marketOrder) throws IOException {
    OkxResponse<List<OkxOrderResponse>> okxResponse =
        placeOkxOrder(
            OkxAdapters.adaptOrder(
                marketOrder, exchange.getExchangeMetaData(), exchange.accountLevel));

    if (okxResponse.isSuccess()) return okxResponse.getData().get(0).getOrderId();
    else
      throw new OkxException(
          okxResponse.getData().get(0).getMessage(),
          Integer.parseInt(okxResponse.getData().get(0).getCode()));
  }

  @Override
  public String placeLimitOrder(LimitOrder limitOrder) throws IOException, FundsExceededException {
    OkxResponse<List<OkxOrderResponse>> okxResponse =
        placeOkxOrder(
            OkxAdapters.adaptOrder(
                limitOrder, exchange.getExchangeMetaData(), exchange.accountLevel));

    if (okxResponse.isSuccess()) return okxResponse.getData().get(0).getOrderId();
    else
      throw new OkxException(
          okxResponse.getData().get(0).getMessage(),
          Integer.parseInt(okxResponse.getData().get(0).getCode()));
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
    if (okxResponse.isSuccess()) return okxResponse.getData().get(0).getOrderId();
    else
      throw new OkxException(
          okxResponse.getData().get(0).getMessage(),
          Integer.parseInt(okxResponse.getData().get(0).getCode()));
  }

  public List<String> changeOrder(List<LimitOrder> limitOrders)
      throws IOException, FundsExceededException {
    return amendOkxOrder(
            limitOrders.stream()
                .map(order -> OkxAdapters.adaptAmendOrder(order, exchange.getExchangeMetaData()))
                .collect(Collectors.toList()))
        .getData()
        .stream()
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
      if (okxResponse.isSuccess()) return true;
      else
        throw new OkxException(
            okxResponse.getData().get(0).getMessage(),
            Integer.parseInt(okxResponse.getData().get(0).getCode()));
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
    return cancelOkxOrder(
            params.stream()
                .map(
                    param ->
                        OkxCancelOrderRequest.builder()
                            .orderId(((CancelOrderByIdParams) param).getOrderId())
                            .instrumentId(
                                OkxAdapters.adaptInstrument(
                                    ((CancelOrderByInstrument) param).getInstrument()))
                            .build())
                .collect(Collectors.toList()))
        .getData()
        .stream()
        .map(result -> "0".equals(result.getCode()))
        .collect(Collectors.toList());
  }
}
