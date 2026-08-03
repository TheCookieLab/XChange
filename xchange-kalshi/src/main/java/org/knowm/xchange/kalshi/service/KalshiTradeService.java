package org.knowm.xchange.kalshi.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.account.OpenPositions;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.dto.trade.OpenOrders;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.dto.trade.UserTrades;
import org.knowm.xchange.exceptions.NotAvailableFromExchangeException;
import org.knowm.xchange.kalshi.KalshiAdapters;
import org.knowm.xchange.kalshi.KalshiExchange;
import org.knowm.xchange.kalshi.dto.trade.KalshiOrder;
import org.knowm.xchange.kalshi.dto.trade.KalshiFillsResponse.KalshiFill;
import org.knowm.xchange.service.trade.TradeService;
import org.knowm.xchange.service.trade.params.CancelOrderByIdParams;
import org.knowm.xchange.service.trade.params.CancelOrderParams;
import org.knowm.xchange.service.trade.params.InstrumentParam;
import org.knowm.xchange.service.trade.params.TradeHistoryParamInstrument;
import org.knowm.xchange.service.trade.params.TradeHistoryParams;
import org.knowm.xchange.service.trade.params.orders.DefaultOpenOrdersParamInstrument;
import org.knowm.xchange.service.trade.params.orders.OpenOrdersParamInstrument;
import org.knowm.xchange.service.trade.params.orders.OpenOrdersParams;
import org.knowm.xchange.service.trade.params.orders.OrderQueryParams;

/** Generic trade service for Kalshi; placement is limit-only on the YES leg. */
public class KalshiTradeService extends KalshiTradeServiceRaw implements TradeService {

  public KalshiTradeService(KalshiExchange exchange) {
    super(exchange);
  }

  @Override
  public OpenOrders getOpenOrders() throws IOException {
    return getOpenOrders(createOpenOrdersParams());
  }

  @Override
  public OpenOrders getOpenOrders(OpenOrdersParams params) throws IOException {
    String ticker = null;
    if (params instanceof OpenOrdersParamInstrument instrumentParams
        && instrumentParams.getInstrument() != null) {
      ticker = KalshiAdapters.marketTicker(instrumentParams.getInstrument());
    }
    List<LimitOrder> openOrders = new ArrayList<>();
    for (KalshiOrder order : getKalshiOrders(ticker, "resting", null, null).orders()) {
      openOrders.add(KalshiAdapters.adaptOrder(order));
    }
    return new OpenOrders(openOrders);
  }

  @Override
  public OpenOrdersParams createOpenOrdersParams() {
    return new DefaultOpenOrdersParamInstrument();
  }

  @Override
  public String placeLimitOrder(LimitOrder limitOrder) throws IOException {
    return placeKalshiOrder(KalshiAdapters.toCreateOrderRequest(limitOrder)).orderId();
  }

  @Override
  public String placeMarketOrder(MarketOrder marketOrder) throws IOException {
    throw new NotAvailableFromExchangeException(
        "Kalshi V2 event orders require a limit price; market orders are not supported.");
  }

  @Override
  public boolean cancelOrder(CancelOrderParams orderParams) throws IOException {
    if (!(orderParams instanceof CancelOrderByIdParams idParams)) {
      throw new IllegalArgumentException(
          "Kalshi cancel requires a CancelOrderByIdParams (provider order id).");
    }
    KalshiOrder order = cancelKalshiOrder(idParams.getOrderId()).order();
    return order != null && "canceled".equals(order.status());
  }

  @Override
  public Collection<Order> getOrder(OrderQueryParams... orderQueryParams) throws IOException {
    List<Order> orders = new ArrayList<>();
    for (OrderQueryParams params : orderQueryParams) {
      orders.add(KalshiAdapters.adaptOrder(getKalshiOrder(params.getOrderId()).order()));
    }
    return orders;
  }

  @Override
  public UserTrades getTradeHistory(TradeHistoryParams params) throws IOException {
    String ticker = null;
    if (params instanceof TradeHistoryParamInstrument instrumentParams) {
      ticker = KalshiAdapters.marketTicker(instrumentParams.getInstrument());
    }
    List<UserTrade> trades = new ArrayList<>();
    for (KalshiFill fill : getKalshiFills(ticker, null, null, null).fills()) {
      trades.add(KalshiAdapters.adaptFill(fill));
    }
    return new UserTrades(trades, org.knowm.xchange.dto.marketdata.Trades.TradeSortType.SortByTimestamp);
  }

  @Override
  public OpenPositions getOpenPositions() throws IOException {
    return new OpenPositions(
        KalshiAdapters.adaptPositions(
            kalshiAuthenticated
                .getPositions(apiKey, timestampFactory(), digest, null, null)
                .marketPositions()));
  }
}
