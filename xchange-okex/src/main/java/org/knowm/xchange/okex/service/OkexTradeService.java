package org.knowm.xchange.okex.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.knowm.xchange.client.ResilienceRegistries;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.account.OpenPositions;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.dto.trade.OpenOrders;
import org.knowm.xchange.dto.trade.UserTrades;
import org.knowm.xchange.exceptions.FundsExceededException;
import org.knowm.xchange.okex.OkexExchange;
import org.knowm.xchange.okex.dto.OkexException;
import org.knowm.xchange.okex.dto.trade.OkexTradeParams;
import org.knowm.xchange.okx.dto.OkxException;
import org.knowm.xchange.okx.dto.trade.OkxTradeParams;
import org.knowm.xchange.okx.service.OkxTradeService;
import org.knowm.xchange.service.trade.TradeService;
import org.knowm.xchange.service.trade.params.CancelOrderParams;
import org.knowm.xchange.service.trade.params.TradeHistoryParams;
import org.knowm.xchange.service.trade.params.orders.OpenOrdersParams;
import org.knowm.xchange.service.trade.params.orders.OrderQueryParams;

/**
 * @deprecated use {@link org.knowm.xchange.okx.service.OkxTradeService} instead.
 */
@Deprecated
public class OkexTradeService extends OkexTradeServiceRaw implements TradeService {

  private final OkxTradeService delegate;

  public OkexTradeService(OkexExchange exchange, ResilienceRegistries resilienceRegistries) {
    super(exchange, resilienceRegistries);
    this.delegate = new OkxTradeService(exchange, resilienceRegistries);
  }

  @Override
  public OpenPositions getOpenPositions() throws IOException {
    try {
      return delegate.getOpenPositions();
    } catch (OkxException e) {
      throw new OkexException(e);
    }
  }

  @Override
  public UserTrades getTradeHistory(TradeHistoryParams params) throws IOException {
    try {
      return delegate.getTradeHistory(params);
    } catch (OkxException e) {
      throw new OkexException(e);
    }
  }

  @Override
  public OpenOrders getOpenOrders() throws IOException {
    try {
      return delegate.getOpenOrders();
    } catch (OkxException e) {
      throw new OkexException(e);
    }
  }

  @Override
  public OpenOrders getOpenOrders(OpenOrdersParams params) throws IOException {
    try {
      return delegate.getOpenOrders(params);
    } catch (OkxException e) {
      throw new OkexException(e);
    }
  }

  @Override
  public Class getRequiredOrderQueryParamClass() {
    return delegate.getRequiredOrderQueryParamClass();
  }

  public Order getOrder(OrderQueryParams orderQueryParams) throws IOException {
    try {
      return delegate.getOrder(orderQueryParams);
    } catch (OkxException e) {
      throw new OkexException(e);
    }
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
    try {
      return delegate.placeMarketOrder(marketOrder);
    } catch (OkxException e) {
      throw new OkexException(e);
    }
  }

  @Override
  public String placeLimitOrder(LimitOrder limitOrder) throws IOException, FundsExceededException {
    try {
      return delegate.placeLimitOrder(limitOrder);
    } catch (OkxException e) {
      throw new OkexException(e);
    }
  }

  public List<String> placeLimitOrder(List<LimitOrder> limitOrders)
      throws IOException, FundsExceededException {
    try {
      return delegate.placeLimitOrder(limitOrders);
    } catch (OkxException e) {
      throw new OkexException(e);
    }
  }

  @Override
  public String changeOrder(LimitOrder limitOrder) throws IOException, FundsExceededException {
    try {
      return delegate.changeOrder(limitOrder);
    } catch (OkxException e) {
      throw new OkexException(e);
    }
  }

  public List<String> changeOrder(List<LimitOrder> limitOrders)
      throws IOException, FundsExceededException {
    try {
      return delegate.changeOrder(limitOrders);
    } catch (OkxException e) {
      throw new OkexException(e);
    }
  }

  @Override
  public boolean cancelOrder(CancelOrderParams params) throws IOException {
    try {
      if (params instanceof OkexTradeParams.OkexCancelOrderParams) {
        OkexTradeParams.OkexCancelOrderParams okexParams =
            (OkexTradeParams.OkexCancelOrderParams) params;
        return delegate.cancelOrder(
            new OkxTradeParams.OkxCancelOrderParams(
                okexParams.instrument, okexParams.orderId, okexParams.userReference));
      }
      return delegate.cancelOrder(params);
    } catch (OkxException e) {
      throw new OkexException(e);
    }
  }

  @Override
  public Class[] getRequiredCancelOrderParamClasses() {
    return delegate.getRequiredCancelOrderParamClasses();
  }

  public List<Boolean> cancelOrder(List<CancelOrderParams> params) throws IOException {
    try {
      return delegate.cancelOrder(
          params.stream()
              .map(
                  param -> {
                    if (param instanceof OkexTradeParams.OkexCancelOrderParams) {
                      OkexTradeParams.OkexCancelOrderParams okexParams =
                          (OkexTradeParams.OkexCancelOrderParams) param;
                      return new OkxTradeParams.OkxCancelOrderParams(
                          okexParams.instrument, okexParams.orderId, okexParams.userReference);
                    }
                    return (CancelOrderParams) param;
                  })
              .collect(Collectors.toList()));
    } catch (OkxException e) {
      throw new OkexException(e);
    }
  }
}
