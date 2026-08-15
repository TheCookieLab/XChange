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
import org.knowm.xchange.mexc.v3.MexcV3OrderFlags;
import org.knowm.xchange.mexc.v3.MexcV3Symbols;
import org.knowm.xchange.mexc.v3.client.MexcV3Exception;
import org.knowm.xchange.mexc.v3.client.ReplaySafety;
import org.knowm.xchange.mexc.v3.dto.trade.MexcV3MyTrade;
import org.knowm.xchange.mexc.v3.dto.trade.MexcV3Order;
import org.knowm.xchange.mexc.v3.dto.trade.MexcV3OrderResponse;
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
   * {@code price}; {@code newClientOrderId} carries the order's user reference when set, and a
   * generated correlation id otherwise so ambiguous placement outcomes stay reconcilable by client
   * order id. The provider order id is returned.
   */
  @Override
  public String placeLimitOrder(LimitOrder limitOrder) throws IOException {
    final String clientOrderId = resolveClientOrderId(limitOrder.getUserReference());
    return execute(
        () ->
            mexcV3Authenticated
                .placeOrder(
                    apiKey,
                    MexcV3Symbols.toMexcSymbol(limitOrder.getInstrument()),
                    limitOrder.getType() == OrderType.ASK
                        ? MexcV3OrderSide.SELL
                        : MexcV3OrderSide.BUY,
                    MexcV3OrderType.LIMIT,
                    limitOrder.getOriginalAmount().toPlainString(),
                    null,
                    limitOrder.getLimitPrice().toPlainString(),
                    clientOrderId,
                    recvWindowMs,
                    timestampFactory,
                    signatureCreator)
                .getOrderId(),
        ReplaySafety.PLACEMENT,
        clientOrderId);
  }

  /**
   * Places a market order.
   *
   * <p>Per the provider documentation a market order accepts either {@code quantity} (base asset
   * amount) or {@code quoteOrderQty} (quote asset amount). The XChange contract always prices
   * {@code originalAmount} in the base asset, so both sides send {@code quantity} by default;
   * setting {@link MexcV3OrderFlags#QUOTE_ORDER_QTY} on a market BUY switches it to a
   * quote-denominated spend ({@code quoteOrderQty}). MEXC only prices market SELL orders in base
   * quantity, so the flag is rejected on asks. The provider order id is returned.
   */
  @Override
  public String placeMarketOrder(MarketOrder marketOrder) throws IOException {
    boolean buy = marketOrder.getType() == OrderType.BID;
    boolean quoteDenominated =
        marketOrder.hasFlag(MexcV3OrderFlags.QUOTE_ORDER_QTY);
    if (quoteDenominated && !buy) {
      throw new IllegalArgumentException(
          "MEXC Spot v3 prices market SELL orders in base quantity; "
              + MexcV3OrderFlags.QUOTE_ORDER_QTY
              + " is only valid on market BUY orders");
    }
    final String clientOrderId = resolveClientOrderId(marketOrder.getUserReference());
    return execute(
        () ->
            mexcV3Authenticated
                .placeOrder(
                    apiKey,
                    MexcV3Symbols.toMexcSymbol(marketOrder.getInstrument()),
                    buy ? MexcV3OrderSide.BUY : MexcV3OrderSide.SELL,
                    MexcV3OrderType.MARKET,
                    quoteDenominated ? null : marketOrder.getOriginalAmount().toPlainString(),
                    quoteDenominated ? marketOrder.getOriginalAmount().toPlainString() : null,
                    null,
                    clientOrderId,
                    recvWindowMs,
                    timestampFactory,
                    signatureCreator)
                .getOrderId(),
        ReplaySafety.PLACEMENT,
        clientOrderId);
  }

  @Override
  public boolean cancelOrder(CancelOrderParams orderParams) throws IOException {
    String symbol = null;
    String orderId = null;
    String origClientOrderId = null;
    if (orderParams instanceof CancelOrderByInstrument) {
      Instrument instrument = ((CancelOrderByInstrument) orderParams).getInstrument();
      if (instrument instanceof CurrencyPair) {
        symbol = MexcV3Symbols.toMexcSymbol(instrument);
      }
    } else if (orderParams instanceof CancelOrderByCurrencyPair) {
      symbol =
          MexcV3Symbols.toMexcSymbol(
              ((CancelOrderByCurrencyPair) orderParams).getCurrencyPair());
    }
    if (orderParams instanceof CancelOrderByIdParams) {
      String candidateOrderId = ((CancelOrderByIdParams) orderParams).getOrderId();
      if (candidateOrderId != null) {
        orderId = candidateOrderId;
      } else if (orderParams instanceof CancelOrderByUserReferenceParams) {
        origClientOrderId =
            ((CancelOrderByUserReferenceParams) orderParams).getUserReference();
      }
    } else if (orderParams instanceof CancelOrderByUserReferenceParams) {
      origClientOrderId = ((CancelOrderByUserReferenceParams) orderParams).getUserReference();
    }
    if (symbol == null) {
      throw new IllegalArgumentException(
          "MEXC Spot v3 cancelOrder requires a currency pair/instrument");
    }
    if (orderId == null && origClientOrderId == null) {
      throw new IllegalArgumentException(
          "MEXC Spot v3 cancelOrder requires an orderId or user reference");
    }
    if (symbol == null) {
      throw new IllegalArgumentException(
          "MEXC Spot v3 cancelOrder requires a currency pair/instrument");
    }
    final String querySymbol = symbol;
    final String queryOrderId = orderId;
    final String queryOrigClientOrderId = origClientOrderId;
    return execute(
        () -> {
          MexcV3Order canceled =
              mexcV3Authenticated.cancelOrder(
                  apiKey, querySymbol, queryOrderId, queryOrigClientOrderId, null, recvWindowMs, timestampFactory, signatureCreator);
          return canceled != null && canceled.getOrderId() != null;
        },
        ReplaySafety.IDEMPOTENT_CANCELLATION);
  }

  @Override
  public Collection<String> cancelAllOrders(CancelAllOrders orderParams) throws IOException {
    if (!(orderParams instanceof DefaultCancelAllOrdersByInstrument)) {
      throw new IllegalArgumentException(
          "MEXC Spot v3 cancelAllOrders requires DefaultCancelAllOrdersByInstrument");
    }
    final String querySymbol = MexcV3Symbols.toMexcSymbol(((DefaultCancelAllOrdersByInstrument) orderParams).getInstrument());
    return execute(
        () -> {
          List<MexcV3Order> canceled =
              mexcV3Authenticated.cancelAllOpenOrders(apiKey, querySymbol, recvWindowMs, timestampFactory, signatureCreator);
          List<String> ids = new ArrayList<>(canceled.size());
          for (MexcV3Order order : canceled) {
            ids.add(order.getOrderId());
          }
          return ids;
        },
        ReplaySafety.IDEMPOTENT_CANCELLATION);
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
    final CurrencyPair queryPair = pair;
    return execute(
        () -> {
          List<MexcV3Order> raw =
              mexcV3Authenticated.openOrders(
                  apiKey, MexcV3Symbols.toMexcSymbol(queryPair), recvWindowMs, timestampFactory, signatureCreator);
          List<LimitOrder> limitOrders = new ArrayList<>(raw.size());
          List<Order> otherOrders = new ArrayList<>();
          for (MexcV3Order order : raw) {
            Order adapted =
                MexcV3Adapters.adaptOrder(
                    order, MexcV3Symbols.toCurrencyPair(order.getSymbol()));
            if (adapted instanceof LimitOrder) {
              limitOrders.add((LimitOrder) adapted);
            } else {
              otherOrders.add(adapted);
            }
          }
          return new OpenOrders(limitOrders, otherOrders);
        },
        ReplaySafety.READ);
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
      final String querySymbol = symbol;
      Order order =
          execute(
              () -> {
                MexcV3Order rawOrder =
                    mexcV3Authenticated.order(
                        apiKey, querySymbol, null, params.getOrderId(), recvWindowMs, timestampFactory, signatureCreator);
                return MexcV3Adapters.adaptOrder(
                    rawOrder, MexcV3Symbols.toCurrencyPair(rawOrder.getSymbol()));
              },
              ReplaySafety.READ);
      result.add(order);
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
    final CurrencyPair queryPair = pair;
    final String queryOrderId = orderId;
    final Date queryStartTime = startTime;
    final Date queryEndTime = endTime;
    final Integer queryLimit = limit;
    return execute(
        () -> {
          List<MexcV3MyTrade> raw =
              mexcV3Authenticated.myTrades(
                  apiKey,
                  MexcV3Symbols.toMexcSymbol(queryPair),
                  queryOrderId,
                  queryStartTime == null ? null : queryStartTime.getTime(),
                  queryEndTime == null ? null : queryEndTime.getTime(),
                  queryLimit,
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
                    .instrument(queryPair)
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
        },
        ReplaySafety.READ);
  }

  @Override
  public TradeHistoryParams createTradeHistoryParams() {
    return new MexcV3TradeHistoryParams();
  }

  @Override
  public OpenOrdersParams createOpenOrdersParams() {
    return new DefaultOpenOrdersParamCurrencyPair();
  }

  /**
   * Place-order test endpoint ({@code POST /api/v3/order/test}); returns the parsed provider
   * echo. The endpoint validates a new order without sending it to the matching engine.
   */
  public MexcV3OrderResponse placeOrderTest(
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

  /**
   * Resolves the client order id sent as {@code newClientOrderId}: the caller's user reference
   * when present, otherwise a fresh correlation id (32 hex characters) generated before the
   * placement round-trip so an ambiguous transport outcome can be reconciled by client order id.
   */
  private static String resolveClientOrderId(String userReference) {
    if (userReference != null && !userReference.isEmpty()) {
      return userReference;
    }
    byte[] random = new byte[16];
    new java.security.SecureRandom().nextBytes(random);
    StringBuilder hex = new StringBuilder(32);
    for (byte b : random) {
      hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
    }
    return hex.toString();
  }
}
