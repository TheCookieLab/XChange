package org.knowm.xchange.coinbasederivatives.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.knowm.xchange.coinbasederivatives.CoinbaseDerivativesAdapters;
import org.knowm.xchange.coinbasederivatives.CoinbaseDerivativesExchange;
import org.knowm.xchange.coinbasederivatives.CoinbaseDerivativesPlacementResult;
import org.knowm.xchange.coinbasederivatives.dto.trade.CoinbaseDerivativesOrder;
import org.knowm.xchange.coinbasederivatives.dto.trade.CoinbaseDerivativesOrderFlags;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.account.OpenPositions;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.dto.trade.OpenOrders;
import org.knowm.xchange.dto.trade.StopOrder;
import org.knowm.xchange.dto.trade.UserTrades;
import org.knowm.xchange.service.trade.TradeService;
import org.knowm.xchange.service.trade.params.CancelAllOrders;
import org.knowm.xchange.service.trade.params.CancelOrderByIdParams;
import org.knowm.xchange.service.trade.params.CancelOrderByInstrument;
import org.knowm.xchange.service.trade.params.CancelOrderParams;
import org.knowm.xchange.service.trade.params.InstrumentParam;
import org.knowm.xchange.service.trade.params.TradeHistoryParamLimit;
import org.knowm.xchange.service.trade.params.TradeHistoryParams;
import org.knowm.xchange.service.trade.params.TradeHistoryParamsAll;
import org.knowm.xchange.service.trade.params.orders.DefaultOpenOrdersParamInstrument;
import org.knowm.xchange.service.trade.params.orders.OpenOrdersParamInstrument;
import org.knowm.xchange.service.trade.params.orders.OpenOrdersParams;
import org.knowm.xchange.service.trade.params.orders.OrderQueryParams;

/** Generic XChange trading facade. */
public class CoinbaseDerivativesTradeService extends CoinbaseDerivativesTradeServiceRaw
    implements TradeService {
  public CoinbaseDerivativesTradeService(CoinbaseDerivativesExchange exchange) {
    super(exchange);
  }

  @Override
  public String placeMarketOrder(MarketOrder order) throws IOException {
    return placeRaw(order, "market", null, null).primaryOrderId();
  }

  @Override
  public String placeLimitOrder(LimitOrder order) throws IOException {
    return placeRaw(order, "limit", order.getLimitPrice(), null).primaryOrderId();
  }

  @Override
  public String placeStopOrder(StopOrder order) throws IOException {
    String type = order.getLimitPrice() == null ? "stop_market" : "stop_limit";
    return placeRaw(order, type, order.getLimitPrice(), order.getStopPrice()).primaryOrderId();
  }

  /**
   * Places an order once and exposes every provider identifier.
   *
   * <p>The user reference is sent as a non-unique, non-idempotent label. This method never retries
   * an ambiguous placement.
   */
  public CoinbaseDerivativesPlacementResult placeRaw(
      Order order, String type, BigDecimal price, BigDecimal triggerPrice) throws IOException {
    return super.placeOrder(
        order.getType() == Order.OrderType.BID ? "buy" : "sell",
        CoinbaseDerivativesAdapters.toNativeName(order.getInstrument()),
        order.getOriginalAmount(),
        type,
        order.getUserReference(),
        price,
        order.getOrderFlags().contains(CoinbaseDerivativesOrderFlags.REDUCE_ONLY),
        triggerPrice);
  }

  @Override
  public OpenOrders getOpenOrders() throws IOException {
    return adaptOpenOrders(super.getOpenOrders(null));
  }

  @Override
  public OpenOrders getOpenOrders(OpenOrdersParams params) throws IOException {
    String instrumentName = null;
    if (params instanceof OpenOrdersParamInstrument byInstrument
        && byInstrument.getInstrument() != null) {
      instrumentName = CoinbaseDerivativesAdapters.toNativeName(byInstrument.getInstrument());
    }
    return adaptOpenOrders(super.getOpenOrders(instrumentName));
  }

  @Override
  public OpenOrdersParams createOpenOrdersParams() {
    return new DefaultOpenOrdersParamInstrument();
  }

  private OpenOrders adaptOpenOrders(List<CoinbaseDerivativesOrder> providerOrders) {
    List<LimitOrder> limits = new ArrayList<>();
    List<Order> other = new ArrayList<>();
    providerOrders.stream()
        .map(CoinbaseDerivativesAdapters::adaptOrder)
        .forEach(
            order -> {
              if (order instanceof LimitOrder limitOrder) {
                limits.add(limitOrder);
              } else {
                other.add(order);
              }
            });
    return new OpenOrders(limits, other);
  }

  @Override
  public OpenPositions getOpenPositions() throws IOException {
    CoinbaseDerivativesAccountServiceRaw accountService =
        (CoinbaseDerivativesAccountServiceRaw) exchange.getAccountService();
    return new OpenPositions(
        accountService.getPositions(null, null).stream()
            .map(CoinbaseDerivativesAdapters::adaptPosition)
            .toList());
  }

  @Override
  public boolean cancelOrder(String orderId) throws IOException {
    CoinbaseDerivativesOrder cancelled = super.cancel(orderId);
    return "cancelled".equalsIgnoreCase(cancelled.orderState())
        || "canceled".equalsIgnoreCase(cancelled.orderState());
  }

  @Override
  public boolean cancelOrder(CancelOrderParams params) throws IOException {
    if (params instanceof CancelOrderByIdParams byId) {
      return cancelOrder(byId.getOrderId());
    }
    if (params instanceof CancelOrderByInstrument byInstrument) {
      return super.cancelAllByInstrument(
              CoinbaseDerivativesAdapters.toNativeName(byInstrument.getInstrument()))
          != null;
    }
    return false;
  }

  @Override
  public Collection<String> cancelAllOrders(CancelAllOrders params) throws IOException {
    if (params instanceof CancelOrderByInstrument byInstrument) {
      super.cancelAllByInstrument(
          CoinbaseDerivativesAdapters.toNativeName(byInstrument.getInstrument()));
      return List.of();
    }
    return List.of();
  }

  @Override
  public UserTrades getTradeHistory(TradeHistoryParams params) throws IOException {
    String instrumentName = null;
    if (params instanceof InstrumentParam instrumentParam
        && instrumentParam.getInstrument() != null) {
      instrumentName = CoinbaseDerivativesAdapters.toNativeName(instrumentParam.getInstrument());
    }
    Integer count =
        params instanceof TradeHistoryParamLimit limitParams ? limitParams.getLimit() : null;
    return new UserTrades(
        super.getUserTrades(
                instrumentName,
                CoinbaseDerivativesAccountService.DEFAULT_COLLATERAL_CURRENCY,
                count)
            .trades()
            .stream()
            .map(CoinbaseDerivativesAdapters::adaptUserTrade)
            .toList(),
        org.knowm.xchange.dto.marketdata.Trades.TradeSortType.SortByTimestamp);
  }

  @Override
  public TradeHistoryParams createTradeHistoryParams() {
    return new TradeHistoryParamsAll();
  }

  @Override
  public Collection<Order> getOrder(OrderQueryParams... queries) throws IOException {
    List<Order> orders = new ArrayList<>();
    for (OrderQueryParams query : queries) {
      orders.add(CoinbaseDerivativesAdapters.adaptOrder(super.getOrderState(query.getOrderId())));
    }
    return orders;
  }
}
