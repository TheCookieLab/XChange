package org.knowm.xchange.mexc.v3.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.marketdata.Trades.TradeSortType;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.dto.trade.OpenOrders;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.dto.trade.UserTrades;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.mexc.v3.MexcV3Adapters;
import org.knowm.xchange.mexc.v3.MexcV3Symbols;
import org.knowm.xchange.mexc.v3.client.MexcV3Exception;
import org.knowm.xchange.mexc.v3.dto.trade.MexcV3MyTrade;
import org.knowm.xchange.mexc.v3.dto.trade.MexcV3Order;
import org.knowm.xchange.mexc.v3.dto.trade.MexcV3OrderSide;
import org.knowm.xchange.mexc.v3.dto.trade.MexcV3OrderType;
import org.knowm.xchange.service.trade.TradeService;
import org.knowm.xchange.service.trade.params.CancelAllOrders;
import org.knowm.xchange.service.trade.params.CancelOrderByIdParams;
import org.knowm.xchange.service.trade.params.CancelOrderByCurrencyPair;
import org.knowm.xchange.service.trade.params.CancelOrderByInstrument;
import org.knowm.xchange.service.trade.params.CancelOrderByUserReferenceParams;
import org.knowm.xchange.service.trade.params.CancelOrderParams;
import org.knowm.xchange.service.trade.params.DefaultCancelAllOrdersByInstrument;
import org.knowm.xchange.service.trade.params.TradeHistoryParamCurrencyPair;
import org.knowm.xchange.service.trade.params.TradeHistoryParamLimit;
import org.knowm.xchange.service.trade.params.TradeHistoryParamOrderId;
import org.knowm.xchange.service.trade.params.TradeHistoryParams;
import org.knowm.xchange.service.trade.params.TradeHistoryParamsTimeSpan;
import org.knowm.xchange.service.trade.params.orders.DefaultOpenOrdersParamCurrencyPair;
import org.knowm.xchange.service.trade.params.orders.OpenOrdersParamCurrencyPair;
import org.knowm.xchange.service.trade.params.orders.OpenOrdersParams;
import org.knowm.xchange.service.trade.params.orders.OrderQueryParamCurrencyPair;
import org.knowm.xchange.service.trade.params.orders.OrderQueryParamInstrument;
import org.knowm.xchange.service.trade.params.orders.OrderQueryParams;

/** Trade service over the authenticated MEXC Spot v3 REST surface. */
public class MexcV3TradeService extends MexcV3BaseService implements TradeService {

  public MexcV3TradeService(Exchange exchange) {
    super(exchange);
  }

  /**
   * Places a limit order.
   *
   * <p>The provider requires {@code symbol}, {@code side}, {@code type}, {@code quantity} and
   * {@code price}; {@code newClientOrderId} carries the order's user reference when set. The
   * provider order id is returned.
   */
  @Override
  public String placeLimitOrder(LimitOrder limitOrder) throws IOException {
    try {
      return mexcV3Authenticated
          .placeOrder(
              apiKey,
              MexcV3Symbols.toMexcSymbol(limitOrder.getInstrument()),
              limitOrder.getType() == OrderType.ASK ? MexcV3OrderSide.SELL : MexcV3OrderSide.BUY,
              MexcV3OrderType.LIMIT,
              limitOrder.getOriginalAmount().toPlainString(),
              null,
              limitOrder.getLimitPrice().toPlainString(),
              limitOrder.getUserReference(),
              recvWindowMs,
              timestampFactory,
              signatureCreator)
          .getOrderId();
    } catch (MexcV3Exception e) {
      throw e.adapt();
    }
  }

  /**
   * Places a market order.
   *
   * <p>Per the provider documentation a market BUY is priced by {@code quoteOrderQty} and a market
   * SELL by {@code quantity}; the other parameter stays unset. The provider order id is returned.
   */
  @Override
  public String placeMarketOrder(MarketOrder marketOrder) throws IOException {
    try {
      boolean buy = marketOrder.getType() == OrderType.BID;
      return mexcV3Authenticated
          .placeOrder(
              apiKey,
              MexcV3Symbols.toMexcSymbol(marketOrder.getInstrument()),
              buy ? MexcV3OrderSide.BUY : MexcV3OrderSide.SELL,
              MexcV3OrderType.MARKET,
              buy ? null : marketOrder.getOriginalAmount().toPlainString(),
              buy ? marketOrder.getOriginalAmount().toPlainString() : null,
              null,
              marketOrder.getUserReference(),
              recvWindowMs,
              timestampFactory,
              signatureCreator)
          .getOrderId();
    } catch (MexcV3Exception e) {
      throw e.adapt();
    }
  }

  @Override
  public boolean cancelOrder(CancelOrderParams orderParams) throws IOException {
    String symbol = null;
    String orderId = null;
    String origClientOrderId = null;
    if (orderParams instanceof CancelOrderByInstrument
        && orderParams instanceof CancelOrderByIdParams) {
      Instrument instrument = ((CancelOrderByInstrument) orderParams).getInstrument();
      if (instrument instanceof CurrencyPair) {
        symbol = MexcV3Symbols.toMexcSymbol(instrument);
      }
      orderId = ((CancelOrderByIdParams) orderParams).getOrderId();
    } else if (orderParams instanceof CancelOrderByCurrencyPair
        && orderParams instanceof CancelOrderByIdParams) {
      symbol =
          MexcV3Symbols.toMexcSymbol(
              ((CancelOrderByCurrencyPair) orderParams).getCurrencyPair());
      orderId = ((CancelOrderByIdParams) orderParams).getOrderId();
    } else if (orderParams instanceof CancelOrderByUserReferenceParams) {
      origClientOrderId = ((CancelOrderByUserReferenceParams) orderParams).getUserReference();
    } else {
      throw new IllegalArgumentException(
          "MEXC Spot v3 cancelOrder requires a symbol plus orderId or user reference");
    }
    if (symbol == null) {
      throw new IllegalArgumentException(
          "MEXC Spot v3 cancelOrder requires a currency pair/instrument");
    }
    try {
      MexcV3Order canceled =
          mexcV3Authenticated.cancelOrder(
              apiKey, symbol, orderId, origClientOrderId, null, recvWindowMs, timestampFactory, signatureCreator);
      return canceled != null && canceled.getOrderId() != null;
    } catch (MexcV3Exception e) {
      throw e.adapt();
    }
  }

  @Override
  public Collection<String> cancelAllOrders(CancelAllOrders orderParams) throws IOException {
    if (!(orderParams instanceof DefaultCancelAllOrdersByInstrument)) {
      throw new IllegalArgumentException(
          "MEXC Spot v3 cancelAllOrders requires DefaultCancelAllOrdersByInstrument");
    }
    String symbol = MexcV3Symbols.toMexcSymbol(((DefaultCancelAllOrdersByInstrument) orderParams).getInstrument());
    try {
      List<MexcV3Order> canceled =
          mexcV3Authenticated.cancelAllOpenOrders(apiKey, symbol, recvWindowMs, timestampFactory, signatureCreator);
      List<String> ids = new ArrayList<>(canceled.size());
      for (MexcV3Order order : canceled) {
        ids.add(order.getOrderId());
      }
      return ids;
    } catch (MexcV3Exception e) {
      throw e.adapt();
    }
  }

  @Override
  public OpenOrders getOpenOrders() throws IOException {
    throw new ExchangeException(
        "MEXC Spot v3 requires a symbol; use getOpenOrders(OpenOrdersParams)");
  }

  @Override
  public OpenOrders getOpenOrders(OpenOrdersParams params) throws IOException {
    CurrencyPair pair = null;
    if (params instanceof OpenOrdersParamCurrencyPair) {
      pair = ((OpenOrdersParamCurrencyPair) params).getCurrencyPair();
    }
    if (pair == null) {
      throw new IllegalArgumentException("MEXC Spot v3 open orders require a currency pair");
    }
    try {
      List<MexcV3Order> raw =
          mexcV3Authenticated.openOrders(
              apiKey, MexcV3Symbols.toMexcSymbol(pair), recvWindowMs, timestampFactory, signatureCreator);
      List<LimitOrder> orders = new ArrayList<>(raw.size());
      for (MexcV3Order order : raw) {
        orders.add(MexcV3Adapters.adaptOrder(order, MexcV3Symbols.toCurrencyPair(order.getSymbol())));
      }
      return new OpenOrders(orders);
    } catch (MexcV3Exception e) {
      throw e.adapt();
    }
  }

  @Override
  public Collection<Order> getOrder(OrderQueryParams... orderQueryParams) throws IOException {
    if (orderQueryParams == null || orderQueryParams.length == 0) {
      return Collections.emptyList();
    }
    List<Order> result = new ArrayList<>(orderQueryParams.length);
    for (OrderQueryParams params : orderQueryParams) {
      String symbol = null;
      if (params instanceof OrderQueryParamCurrencyPair) {
        symbol = MexcV3Symbols.toMexcSymbol(((OrderQueryParamCurrencyPair) params).getCurrencyPair());
      } else if (params instanceof OrderQueryParamInstrument) {
        symbol = MexcV3Symbols.toMexcSymbol(((OrderQueryParamInstrument) params).getInstrument());
      }
      try {
        MexcV3Order order =
            mexcV3Authenticated.order(
                apiKey, symbol, null, params.getOrderId(), recvWindowMs, timestampFactory, signatureCreator);
        result.add(
            MexcV3Adapters.adaptOrder(order, MexcV3Symbols.toCurrencyPair(order.getSymbol())));
      } catch (MexcV3Exception e) {
        throw e.adapt();
      }
    }
    return result;
  }

  @Override
  public UserTrades getTradeHistory(TradeHistoryParams params) throws IOException {
    CurrencyPair pair = null;
    String orderId = null;
    Date startTime = null;
    Date endTime = null;
    Integer limit = null;
    if (params instanceof TradeHistoryParamCurrencyPair) {
      pair = ((TradeHistoryParamCurrencyPair) params).getCurrencyPair();
    }
    if (params instanceof TradeHistoryParamOrderId) {
      orderId = ((TradeHistoryParamOrderId) params).getOrderId();
    }
    if (params instanceof TradeHistoryParamsTimeSpan) {
      startTime = ((TradeHistoryParamsTimeSpan) params).getStartTime();
      endTime = ((TradeHistoryParamsTimeSpan) params).getEndTime();
    }
    if (params instanceof TradeHistoryParamLimit) {
      limit = ((TradeHistoryParamLimit) params).getLimit();
    }
    if (pair == null) {
      throw new IllegalArgumentException("MEXC Spot v3 trade history requires a currency pair");
    }
    try {
      List<MexcV3MyTrade> raw =
          mexcV3Authenticated.myTrades(
              apiKey,
              MexcV3Symbols.toMexcSymbol(pair),
              orderId,
              startTime == null ? null : startTime.getTime(),
              endTime == null ? null : endTime.getTime(),
              limit,
              recvWindowMs,
              timestampFactory,
              signatureCreator);
      List<UserTrade> trades = new ArrayList<>(raw.size());
      for (MexcV3MyTrade trade : raw) {
        trades.add(
            UserTrade.builder()
                .id(String.valueOf(trade.getId()))
                .orderId(trade.getOrderId())
                .orderUserReference(trade.getClientOrderId())
                .instrument(pair)
                .originalAmount(new java.math.BigDecimal(trade.getQty()))
                .price(new java.math.BigDecimal(trade.getPrice()))
                .timestamp(new Date(trade.getTime()))
                .type(trade.isBuyer() ? OrderType.BID : OrderType.ASK)
                .feeAmount(new java.math.BigDecimal(trade.getCommission()))
                .feeCurrency(
                    org.knowm.xchange.currency.Currency.getInstance(trade.getCommissionAsset()))
                .build());
      }
      return new UserTrades(trades, TradeSortType.SortByID);
    } catch (MexcV3Exception e) {
      throw e.adapt();
    }
  }

  @Override
  public TradeHistoryParams createTradeHistoryParams() {
    return new MexcV3TradeHistoryParams();
  }

  @Override
  public OpenOrdersParams createOpenOrdersParams() {
    return new DefaultOpenOrdersParamCurrencyPair();
  }

  /** Place-order test endpoint ({@code POST /api/v3/order/test}); returns the provider echo. */
  public String placeOrderTest(
      CurrencyPair pair, MexcV3OrderSide side, MexcV3OrderType type, String quantity, String price)
      throws IOException, MexcV3Exception {
    return mexcV3Authenticated.placeOrderTest(
        apiKey,
        MexcV3Symbols.toMexcSymbol(pair),
        side,
        type,
        quantity,
        null,
        price,
        null,
        recvWindowMs,
        timestampFactory,
        signatureCreator);
  }
}
