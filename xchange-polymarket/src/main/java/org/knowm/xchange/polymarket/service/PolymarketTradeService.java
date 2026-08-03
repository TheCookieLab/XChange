package org.knowm.xchange.polymarket.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.account.OpenPositions;
import org.knowm.xchange.dto.marketdata.Trades;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.dto.trade.OpenOrders;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.dto.trade.UserTrades;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.exceptions.NotAvailableFromExchangeException;
import org.knowm.xchange.polymarket.PolymarketAdapters;
import org.knowm.xchange.polymarket.PolymarketExchange;
import org.knowm.xchange.polymarket.dto.trade.PolymarketOpenOrder;
import org.knowm.xchange.polymarket.dto.trade.PolymarketPostOrderResponse;
import org.knowm.xchange.polymarket.dto.trade.PolymarketUserTrade;
import org.knowm.xchange.service.trade.TradeService;
import org.knowm.xchange.service.trade.params.CancelOrderByIdParams;
import org.knowm.xchange.service.trade.params.CancelOrderParams;
import org.knowm.xchange.service.trade.params.TradeHistoryParamInstrument;
import org.knowm.xchange.service.trade.params.TradeHistoryParams;
import org.knowm.xchange.service.trade.params.orders.DefaultOpenOrdersParamInstrument;
import org.knowm.xchange.service.trade.params.orders.OpenOrdersParamInstrument;
import org.knowm.xchange.service.trade.params.orders.OpenOrdersParams;
import org.knowm.xchange.service.trade.params.orders.OrderQueryParams;

/**
 * Generic trade service for Polymarket; placement is limit-only, directly on the contract's
 * outcome token (never complemented).
 */
public class PolymarketTradeService extends PolymarketTradeServiceRaw implements TradeService {

  public PolymarketTradeService(PolymarketExchange exchange) {
    super(exchange);
  }

  @Override
  public OpenOrders getOpenOrders() throws IOException {
    return getOpenOrders(createOpenOrdersParams());
  }

  @Override
  public OpenOrders getOpenOrders(OpenOrdersParams params) throws IOException {
    String conditionId = null;
    String tokenId = null;
    if (params instanceof OpenOrdersParamInstrument instrumentParams
        && instrumentParams.getInstrument() != null) {
      conditionId = PolymarketAdapters.conditionId(instrumentParams.getInstrument());
      tokenId = PolymarketAdapters.tokenId(instrumentParams.getInstrument());
    }
    List<LimitOrder> openOrders = new ArrayList<>();
    for (PolymarketOpenOrder order : getPolymarketOrders(conditionId, tokenId)) {
      if ("live".equals(order.status())) {
        openOrders.add(PolymarketAdapters.adaptOrder(order));
      }
    }
    return new OpenOrders(openOrders);
  }

  @Override
  public OpenOrdersParams createOpenOrdersParams() {
    return new DefaultOpenOrdersParamInstrument();
  }

  @Override
  public String placeLimitOrder(LimitOrder limitOrder) throws IOException {
    PolymarketPostOrderResponse response = placePolymarketOrder(limitOrder);
    if (!Boolean.TRUE.equals(response.success())) {
      throw new ExchangeException(
          "Polymarket rejected the order: "
              + (response.errorMsg() == null ? "no reason given" : response.errorMsg()));
    }
    return response.orderId();
  }

  @Override
  public String placeMarketOrder(MarketOrder marketOrder) throws IOException {
    throw new NotAvailableFromExchangeException(
        "Polymarket CLOB placement requires a limit price; market orders are not supported.");
  }

  @Override
  public boolean cancelOrder(CancelOrderParams orderParams) throws IOException {
    if (!(orderParams instanceof CancelOrderByIdParams idParams)) {
      throw new IllegalArgumentException(
          "Polymarket cancel requires a CancelOrderByIdParams (provider order id).");
    }
    List<String> canceled = cancelPolymarketOrder(idParams.getOrderId()).canceled();
    return canceled != null && canceled.contains(idParams.getOrderId());
  }

  @Override
  public Collection<Order> getOrder(OrderQueryParams... orderQueryParams) throws IOException {
    List<Order> orders = new ArrayList<>();
    for (OrderQueryParams params : orderQueryParams) {
      orders.add(PolymarketAdapters.adaptOrder(getPolymarketOrder(params.getOrderId())));
    }
    return orders;
  }

  @Override
  public UserTrades getTradeHistory(TradeHistoryParams params) throws IOException {
    String conditionId = null;
    if (params instanceof TradeHistoryParamInstrument instrumentParams
        && instrumentParams.getInstrument() != null) {
      conditionId = PolymarketAdapters.conditionId(instrumentParams.getInstrument());
    }
    List<UserTrade> trades = new ArrayList<>();
    for (PolymarketUserTrade trade : getPolymarketUserTrades(conditionId)) {
      trades.add(PolymarketAdapters.adaptUserTrade(trade));
    }
    return new UserTrades(trades, Trades.TradeSortType.SortByTimestamp);
  }

  @Override
  public OpenPositions getOpenPositions() throws IOException {
    if (walletAddress == null || walletAddress.isBlank()) {
      throw new ExchangeException(
          "Polymarket open positions require a wallet address (spec userName or private key).");
    }
    return new OpenPositions(
        PolymarketAdapters.adaptPositions(dataPublic.getPositions(walletAddress, null, null)));
  }
}
