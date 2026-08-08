package org.knowm.xchange.uniswap.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.dto.trade.StopOrder;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.exceptions.NotAvailableFromExchangeException;
import org.knowm.xchange.service.trade.TradeService;
import org.knowm.xchange.service.trade.params.CancelOrderParams;
import org.knowm.xchange.service.trade.params.TradeHistoryParams;
import org.knowm.xchange.service.trade.params.orders.OrderQueryParams;
import org.knowm.xchange.uniswap.UniswapExchange;
import org.knowm.xchange.uniswap.dto.UniswapOrder;

/**
 * Standard XChange trade service over the raw execution engine.
 *
 * <p>Market orders are supported (ASK = exact-input base, BID = exact-output base); limit orders,
 * stop orders, cancellation, open-order listing, and trade history are explicitly unavailable.
 */
public class UniswapTradeService extends UniswapTradeServiceRaw implements TradeService {

  public UniswapTradeService(UniswapExchange exchange) {
    super(exchange);
  }

  @Override
  public String placeMarketOrder(MarketOrder marketOrder) throws IOException {
    return submitMarketOrder(marketOrder).orderId();
  }

  @Override
  public String placeLimitOrder(LimitOrder limitOrder) {
    throw new NotAvailableFromExchangeException("limit orders are not supported");
  }

  @Override
  public String placeStopOrder(StopOrder stopOrder) {
    throw new NotAvailableFromExchangeException("stop orders are not supported");
  }

  @Override
  public boolean cancelOrder(CancelOrderParams orderParams) {
    throw new NotAvailableFromExchangeException("on-chain swaps cannot be cancelled");
  }

  @Override
  public org.knowm.xchange.dto.trade.OpenOrders getOpenOrders(
      org.knowm.xchange.service.trade.params.orders.OpenOrdersParams params) {
    throw new NotAvailableFromExchangeException("getOpenOrders");
  }

  @Override
  public org.knowm.xchange.dto.trade.UserTrades getTradeHistory(TradeHistoryParams params) {
    throw new NotAvailableFromExchangeException("getTradeHistory");
  }

  @Override
  public TradeHistoryParams createTradeHistoryParams() {
    throw new NotAvailableFromExchangeException("createTradeHistoryParams");
  }

  @Override
  public Collection<Order> getOrder(OrderQueryParams... orderQueryParams) throws IOException {
    List<Order> orders = new ArrayList<>();
    for (String orderId : TradeService.toOrderIds(orderQueryParams)) {
      UniswapOrder order = getOrderStatus(orderId);
      if (order.instrument() == null) {
        throw new ExchangeException(
            "cannot determine the instrument of order " + orderId + "; it was not placed by this process");
      }
      orders.add(order.toMarketOrder());
    }
    return orders;
  }
}
