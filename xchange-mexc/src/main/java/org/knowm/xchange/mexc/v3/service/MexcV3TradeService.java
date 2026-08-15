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
import org.knowm.xchange.mexc.v3.client.RetryClassification;
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
   * order id. The provider order id is returned. When the placement round-trip fails at the
   * transport layer (outcome unknown), a single bounded {@code GET /order} lookup by client order
   * id reconciles the outcome before surfacing a failure.
   */
  @Override
  public String placeLimitOrder(LimitOrder limitOrder) throws IOException {
    final String clientOrderId = resolveClientOrderId(limitOrder.getUserReference());
    final String symbol = MexcV3Symbols.toMexcSymbol(limitOrder.getInstrument());
    try {
      return execute(
          () ->
              mexcV3Authenticated
                  .placeOrder(
                      apiKey,
                      symbol,
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
    } catch (MexcV3Exception ambiguous) {
      // execute() adapts every provider error envelope; only the programmatic AMBIGUOUS failure
      // escapes it, so a MexcV3Exception landing here is the unknown-outcome placement.
      if (ambiguous.getRetryClassification() != RetryClassification.AMBIGUOUS) {
        throw ambiguous;
      }
      return reconcileAmbiguousPlacement(symbol, clientOrderId, ambiguous);
    }
  }

  /**
   * Places a market order.
   *
   * <p>Per the provider documentation a market order accepts either {@code quantity} (base asset
   * amount) or {@code quoteOrderQty} (quote asset amount). The XChange contract always prices
   * {@code originalAmount} in the base asset, so both sides send {@code quantity} by default;
   * setting {@link MexcV3OrderFlags#QUOTE_ORDER_QTY} on a market BUY switches it to a
   * quote-denominated spend ({@code quoteOrderQty}). MEXC only prices market SELL orders in base
   * quantity, so the flag is rejected on asks. The provider order id is returned; an ambiguous
   * transport outcome is reconciled by a single bounded client-order-id lookup.
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
    final String symbol = MexcV3Symbols.toMexcSymbol(marketOrder.getInstrument());
    try {
      return execute(
          () ->
              mexcV3Authenticated
                  .placeOrder(
                      apiKey,
                      symbol,
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
    } catch (MexcV3Exception ambiguous) {
      if (ambiguous.getRetryClassification() != RetryClassification.AMBIGUOUS) {
        throw ambiguous;
      }
      return reconcileAmbiguousPlacement(symbol, clientOrderId, ambiguous);
    }
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
          // Provider limits (mexc.com spot-v3 myTrades): limit is capped at 100 per request,
          // and one request may span at most the provider's queryable window (the last month
          // of records). Partition an explicit startTime..endTime span into provider-sized
          // windows and page forward within each; a request without startTime is a single
          // open-ended window.
          final int maxPageSize = 100; // MEXC v3 myTrades per-request cap (default 100, max 100)
          final long windowMs = 30L * 24 * 60 * 60 * 1000; // provider queryable window: 1 month
          int remaining = queryLimit == null ? Integer.MAX_VALUE : queryLimit;
          List<UserTrade> trades = new ArrayList<>();
          Long windowStart = queryStartTime == null ? null : queryStartTime.getTime();
          Long windowEnd = queryEndTime == null ? null : queryEndTime.getTime();
          while (remaining > 0) {
            if (windowStart != null && windowStart > System.currentTimeMillis()) {
              break; // nothing after the current time
            }
            boolean lastWindow;
            Long requestEnd;
            if (windowStart == null) {
              // No explicit start: a single open-ended window, as before.
              requestEnd = windowEnd;
              lastWindow = true;
            } else if (windowEnd == null || windowStart + windowMs >= windowEnd) {
              // No explicit end, or the remaining span fits in one provider window.
              requestEnd = windowEnd;
              lastWindow = true;
            } else {
              requestEnd = windowStart + windowMs - 1;
              lastWindow = false;
            }
            boolean windowExhausted = false; // short or empty page within this window
            while (remaining > 0 && !windowExhausted) {
              int pageLimit = Math.min(remaining, maxPageSize);
              List<MexcV3MyTrade> raw =
                  mexcV3Authenticated.myTrades(
                      apiKey,
                      MexcV3Symbols.toMexcSymbol(queryPair),
                      queryOrderId,
                      windowStart,
                      requestEnd,
                      pageLimit,
                      recvWindowMs,
                      timestampFactory,
                      signatureCreator);
              if (raw.isEmpty()) {
                windowExhausted = true;
                break;
              }
              int pageStartIndex = trades.size();
              long newest = Long.MIN_VALUE;
              for (MexcV3MyTrade trade : raw) {
                newest = Math.max(newest, trade.getTime());
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
                            org.knowm.xchange.currency.Currency.getInstance(
                                trade.getCommissionAsset()))
                        .build());
              }
              remaining -= raw.size();
              if (raw.size() < pageLimit) {
                windowExhausted = true;
                break; // short page: the window is exhausted
              }
              // Page forward past the newest trade seen. Stop when the window did not advance:
              // that means the provider ignored startTime (orderId-scoped queries) and repeated
              // the same full page, which would otherwise loop forever. The repeated page is
              // stale (all trades predate the current window), so drop it instead of returning
              // duplicates; later windows would repeat it too, so the whole span is done.
              long nextStart = newest + 1;
              if (windowStart != null && nextStart <= windowStart) {
                trades.subList(pageStartIndex, trades.size()).clear();
                return new UserTrades(trades, TradeSortType.SortByID);
              }
              windowStart = nextStart;
            }
            if (lastWindow) {
              break;
            }
            windowStart = requestEnd + 1;
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
   * Resolves an ambiguous placement outcome with exactly one bounded lookup.
   *
   * <p>The placement round-trip failed at the transport layer, so the exchange may or may not
   * have accepted the order. A single {@code GET /order} by {@code symbol} + {@code
   * origClientOrderId} decides the outcome: a match returns the provider order id; an explicit
   * unknown-order code (for example 20116 "order does not exist") proves the order is absent and
   * adapts to the exception hierarchy; any other lookup failure (rate limit, outage, transport)
   * leaves the outcome unknown, so the original ambiguous failure is rethrown (never a
   * misleading absence or a retryable-looking error).
   *
   * @param symbol the MEXC symbol the placement targeted
   * @param clientOrderId the {@code newClientOrderId} the placement carried
   * @param ambiguous the original ambiguous placement failure
   * @return the provider order id when the lookup proves the placement applied
   * @throws IOException when the lookup itself fails at the transport layer
   */
  private String reconcileAmbiguousPlacement(
      String symbol, String clientOrderId, MexcV3Exception ambiguous) throws IOException {
    try {
      MexcV3Order order =
          mexcV3Authenticated.order(
              apiKey,
              symbol,
              clientOrderId,
              null,
              recvWindowMs,
              timestampFactory,
              signatureCreator);
      // Found: the placement actually applied; surface the provider order id.
      return order.getOrderId();
    } catch (MexcV3Exception notFound) {
      // Only an explicit unknown-order code proves absence. A transient provider error (rate
      // limit, outage) does not: the order may still have been accepted, and adapting it would
      // make the caller retry safely and create a duplicate trade.
      if (notFound.getCode() == -2011 || notFound.getCode() == 20116) {
        throw notFound.adapt();
      }
      throw ambiguous;
    } catch (IOException lookupFailure) {
      // Inconclusive: the placement may still have applied; keep the original ambiguity.
      throw ambiguous;
    }
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
