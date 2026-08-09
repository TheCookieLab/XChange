package org.knowm.xchange.kraken.service;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.account.OpenPositions;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.dto.trade.OpenOrders;
import org.knowm.xchange.dto.trade.UserTrades;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.kraken.KrakenAdapters;
import org.knowm.xchange.kraken.KrakenUtils;
import org.knowm.xchange.kraken.dto.trade.KrakenAmendOrderResponse;
import org.knowm.xchange.kraken.dto.trade.KrakenCancelAllOrdersAfterResponse;
import org.knowm.xchange.kraken.dto.trade.KrakenOrder;
import org.knowm.xchange.kraken.dto.trade.KrakenTrade;
import org.knowm.xchange.service.trade.TradeService;
import org.knowm.xchange.service.trade.params.CancelAllOrders;
import org.knowm.xchange.service.trade.params.orders.PlaceOrderKnownParams;
import org.knowm.xchange.service.trade.params.CancelOrderByIdParams;
import org.knowm.xchange.service.trade.params.CancelOrderByUserReferenceParams;
import org.knowm.xchange.service.trade.params.CancelOrderParams;
import org.knowm.xchange.service.trade.params.CurrencyPairParam;
import org.knowm.xchange.service.trade.params.InstrumentParam;
import org.knowm.xchange.service.trade.params.TradeHistoryParamCurrencyPair;
import org.knowm.xchange.service.trade.params.TradeHistoryParamOffset;
import org.knowm.xchange.service.trade.params.TradeHistoryParams;
import org.knowm.xchange.service.trade.params.TradeHistoryParamsIdSpan;
import org.knowm.xchange.service.trade.params.TradeHistoryParamsTimeSpan;
import org.knowm.xchange.service.trade.params.orders.DefaultOpenOrdersParamCurrencyPair;
import org.knowm.xchange.service.trade.params.orders.OpenOrdersParams;
import org.knowm.xchange.service.trade.params.orders.OrderQueryParams;
import org.knowm.xchange.utils.DateUtils;

public class KrakenTradeService extends KrakenTradeServiceRaw implements TradeService {

  /**
   * Constructor
   *
   * @param exchange
   */
  public KrakenTradeService(Exchange exchange) {

    super(exchange);
  }

  @Override
  public OpenOrders getOpenOrders() throws IOException {
    return getOpenOrders(null);
  }

  @Override
  public OpenOrders getOpenOrders(OpenOrdersParams params) throws IOException {
    Map<String, KrakenOrder> krakenOrders = getKrakenOpenOrders();
    CurrencyPair currencyPair = null;
    if (params instanceof CurrencyPairParam) {
      currencyPair = ((CurrencyPairParam) params).getCurrencyPair();
    }
    if (params instanceof InstrumentParam) {
      Instrument instrument = ((InstrumentParam) params).getInstrument();
      currencyPair = new CurrencyPair(instrument.getBase(), instrument.getCounter());
    }

    if (currencyPair != null) {
      Map<String, KrakenOrder> filteredKrakenOrders =
          KrakenUtils.filterOpenOrdersByCurrencyPair(krakenOrders, currencyPair);
      return KrakenAdapters.adaptOpenOrders(filteredKrakenOrders);
    }
    return KrakenAdapters.adaptOpenOrders(krakenOrders);
  }

  @Override
  public String placeMarketOrder(MarketOrder marketOrder) throws IOException {

    return KrakenAdapters.adaptOrderId(super.placeKrakenMarketOrder(marketOrder));
  }

  @Override
  public OpenPositions getOpenPositions() throws IOException {
    return KrakenAdapters.adaptOpenPositions(super.getKrakenOpenPositions());
  }

  @Override
  public String placeLimitOrder(LimitOrder limitOrder) throws IOException {

    return KrakenAdapters.adaptOrderId(super.placeKrakenLimitOrder(limitOrder));
  }

  @Override
  public boolean cancelOrder(String orderId) throws IOException {

    return super.cancelKrakenOrder(orderId).getCount() > 0;
  }

  /**
   * Atomically amends a live order via the Kraken AmendOrder endpoint.
   *
   * <p>The default XChange implementation cancels and re-places the order, which is not atomic;
   * Kraken supports in-place amendment, so this override uses {@code AmendOrder}. The amended
   * order keeps its Kraken identifiers where possible. An ambiguous outcome is never replayed:
   * on transport failure the caller must reconcile by order id or {@code cl_ord_id}.
   *
   * @param limitOrder order with {@code id} (Kraken txid) or {@code userReference}
   *     (userref/cl_ord_id) identifying the live order and the new price/volume
   * @return the amended order's Kraken id (new id when the amend replaced the order)
   */
  @Override
  public String changeOrder(LimitOrder limitOrder) throws IOException {

    String clientOrderId = getClientOrderId(limitOrder).orElse(limitOrder.getUserReference());
    KrakenAmendOrderResponse response =
        super.amendKrakenOrder(
            limitOrder.getId(),
            clientOrderId,
            limitOrder.getOriginalAmount(),
            limitOrder.getLimitPrice() == null ? null : limitOrder.getLimitPrice().toPlainString(),
            null,
            null,
            null);
    if (response.getOrderId() != null) {
      return response.getOrderId();
    }
    if (response.getNewOrderId() != null) {
      return response.getNewOrderId();
    }
    return response.getAmendId();
  }

  /**
   * Arms or disarms the cancel-all-after (dead-man) timer for the Spot account.
   *
   * <p>All open orders are cancelled when the timer expires unless it is re-armed. A timeout of
   * zero disables the timer. This can cancel all open orders; enable it deliberately.
   *
   * @param timeoutSeconds timer length in seconds ({@code 0} disables, max 86400)
   * @return typed provider result with current and trigger times
   */
  public KrakenCancelAllOrdersAfterResponse cancelAllOrdersAfter(long timeoutSeconds)
      throws IOException {

    return super.cancelAllKrakenOrdersAfter(timeoutSeconds);
  }

  @Override
  public boolean cancelOrder(CancelOrderParams orderParams) throws IOException {
    if (orderParams instanceof CancelOrderByIdParams) {
      return cancelOrder(((CancelOrderByIdParams) orderParams).getOrderId());
    }
    if (orderParams instanceof CancelOrderByUserReferenceParams) {
      return cancelOrder(((CancelOrderByUserReferenceParams) orderParams).getUserReference());
    }
    return false;
  }

  @Override
  public Collection<String> cancelAllOrders(CancelAllOrders orderParams) throws IOException {
    return Collections.singletonList(String.valueOf(super.cancelAllKrakenOrders().getCount()));
  }

  @Override
  public Class[] getRequiredCancelOrderParamClasses() {
    return new Class[] {CancelOrderByIdParams.class, CancelOrderByUserReferenceParams.class};
  }

  /**
   * @param params Can optionally implement {@link TradeHistoryParamOffset} and {@link
   *     TradeHistoryParamsTimeSpan} and {@link TradeHistoryParamsIdSpan} All other
   *     TradeHistoryParams types will be ignored.
   */
  @Override
  public UserTrades getTradeHistory(TradeHistoryParams params)
      throws ExchangeException, IOException {

    String start = null;
    String end = null;

    Long offset = null;

    CurrencyPair currencyPair = null;

    if (params instanceof TradeHistoryParamOffset) {
      offset = ((TradeHistoryParamOffset) params).getOffset();
    }

    if (params instanceof TradeHistoryParamsIdSpan) {
      TradeHistoryParamsIdSpan idSpan = (TradeHistoryParamsIdSpan) params;
      start = idSpan.getStartId();
      end = idSpan.getEndId();
    }

    if (params instanceof TradeHistoryParamsTimeSpan) {
      TradeHistoryParamsTimeSpan timeSpan = (TradeHistoryParamsTimeSpan) params;
      start =
          DateUtils.toUnixTimeOptional(timeSpan.getStartTime()).map(Object::toString).orElse(start);

      end = DateUtils.toUnixTimeOptional(timeSpan.getEndTime()).map(Object::toString).orElse(end);
    }

    Map<String, KrakenTrade> krakenTradeHistory;
    boolean includeTrades = false;
    Boolean consolidateTrades = null;
    if (params instanceof KrakenTradeHistoryParams) {
      KrakenTradeHistoryParams krakenParams = (KrakenTradeHistoryParams) params;
      includeTrades = Boolean.TRUE.equals(krakenParams.getIncludeTrades());
      consolidateTrades = krakenParams.getConsolidateTrades();
    }
    if (offset == null) {
      // no explicit cursor: fetch the full history with bounded pagination
      krakenTradeHistory =
          getKrakenTradeHistoryAll(null, includeTrades, start, end, consolidateTrades).getTrades();
    } else {
      krakenTradeHistory =
          getKrakenTradeHistory(null, includeTrades, start, end, offset, consolidateTrades)
              .getTrades();
    }

    if (params instanceof TradeHistoryParamCurrencyPair
        && ((TradeHistoryParamCurrencyPair) params).getCurrencyPair() != null) {
      krakenTradeHistory =
          KrakenUtils.filterTradeHistoryByCurrencyPair(
              krakenTradeHistory, ((TradeHistoryParamCurrencyPair) params).getCurrencyPair());
    }

    return KrakenAdapters.adaptTradesHistory(krakenTradeHistory);
  }

  @Override
  public TradeHistoryParams createTradeHistoryParams() {

    return new KrakenTradeHistoryParams();
  }

  @Override
  public OpenOrdersParams createOpenOrdersParams() {
    return new DefaultOpenOrdersParamCurrencyPair();
  }

  @Override
  public Collection<Order> getOrder(OrderQueryParams... orderQueryParams) throws IOException {
    return KrakenAdapters.adaptOrders(super.getOrders(TradeService.toOrderIds(orderQueryParams)));
  }
}
