package org.knowm.xchange.cryptocom.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.knowm.xchange.client.ResilienceRegistries;
import org.knowm.xchange.cryptocom.CryptoComAdapters;
import org.knowm.xchange.cryptocom.CryptoComExchange;
import org.knowm.xchange.cryptocom.dto.CryptoComUnknownOrderOutcomeException;
import org.knowm.xchange.cryptocom.dto.trade.CryptoComOrder;
import org.knowm.xchange.cryptocom.dto.trade.CryptoComOrderAck;
import org.knowm.xchange.cryptocom.dto.trade.CryptoComOrderPlacementResult;
import org.knowm.xchange.cryptocom.dto.trade.CryptoComOrderSide;
import org.knowm.xchange.cryptocom.dto.trade.CryptoComOrderType;
import org.knowm.xchange.cryptocom.dto.trade.CryptoComPlacementOutcome;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.dto.trade.OpenOrders;
import org.knowm.xchange.dto.trade.StopOrder;
import org.knowm.xchange.dto.trade.UserTrades;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.exceptions.NotAvailableFromExchangeException;
import org.knowm.xchange.service.trade.TradeService;
import org.knowm.xchange.service.trade.params.CancelOrderByIdParams;
import org.knowm.xchange.service.trade.params.CancelOrderParams;
import org.knowm.xchange.service.trade.params.TradeHistoryParamInstrument;
import org.knowm.xchange.service.trade.params.TradeHistoryParamLimit;
import org.knowm.xchange.service.trade.params.TradeHistoryParams;
import org.knowm.xchange.service.trade.params.TradeHistoryParamsTimeSpan;
import org.knowm.xchange.service.trade.params.orders.DefaultOpenOrdersParamInstrument;
import org.knowm.xchange.service.trade.params.orders.OpenOrdersParamInstrument;
import org.knowm.xchange.service.trade.params.orders.OpenOrdersParams;
import org.knowm.xchange.service.trade.params.orders.OrderQueryParams;

public class CryptoComTradeService extends CryptoComTradeServiceRaw implements TradeService {

  public CryptoComTradeService(
      CryptoComExchange exchange, ResilienceRegistries resilienceRegistries) {
    super(exchange, resilienceRegistries);
  }

  @Override
  public String placeMarketOrder(MarketOrder marketOrder) throws IOException {
    CryptoComOrderPlacementResult placement =
        createCryptoComOrder(
            CryptoComAdapters.toInstrumentName(marketOrder.getInstrument()),
            marketOrder.getType() == OrderType.BID
                ? CryptoComOrderSide.BUY
                : CryptoComOrderSide.SELL,
            CryptoComOrderType.MARKET,
            null,
            marketOrder.getOriginalAmount().toPlainString(),
            null,
            marketOrder.getUserReference());
    return requireOrderId(placement);
  }

  @Override
  public String placeLimitOrder(LimitOrder limitOrder) throws IOException {
    CryptoComOrderPlacementResult placement =
        createCryptoComOrder(
            CryptoComAdapters.toInstrumentName(limitOrder.getInstrument()),
            limitOrder.getType() == OrderType.BID
                ? CryptoComOrderSide.BUY
                : CryptoComOrderSide.SELL,
            CryptoComOrderType.LIMIT,
            limitOrder.getLimitPrice().toPlainString(),
            limitOrder.getOriginalAmount().toPlainString(),
            null,
            limitOrder.getUserReference());
    return requireOrderId(placement);
  }

  /**
   * Places a trigger order through the advanced order endpoint. The XChange stop intention maps
   * onto the provider trigger type (STOP_LOSS, STOP_LIMIT, TAKE_PROFIT, TAKE_PROFIT_LIMIT) and
   * {@code stopPrice} becomes the provider {@code trigger_price}; the limit price of
   * STOP_LOSS_LIMIT / TAKE_PROFIT_LIMIT orders becomes the resting limit price.
   */
  @Override
  public String placeStopOrder(StopOrder stopOrder) throws IOException {
    CryptoComOrderType type;
    String price = null;
    switch (stopOrder.getIntention()) {
      case STOP_LOSS:
        type = CryptoComOrderType.STOP_LOSS;
        break;
      case TAKE_PROFIT:
        type = CryptoComOrderType.TAKE_PROFIT;
        break;
      default:
        throw new NotAvailableFromExchangeException(
            "Unsupported stop order intention: " + stopOrder.getIntention());
    }
    if (stopOrder.getLimitPrice() != null) {
      type =
          type == CryptoComOrderType.STOP_LOSS
              ? CryptoComOrderType.STOP_LIMIT
              : CryptoComOrderType.TAKE_PROFIT_LIMIT;
      price = stopOrder.getLimitPrice().toPlainString();
    }
    CryptoComOrderPlacementResult placement =
        createCryptoComAdvancedOrder(
            CryptoComAdapters.toInstrumentName(stopOrder.getInstrument()),
            stopOrder.getType() == OrderType.BID
                ? CryptoComOrderSide.BUY
                : CryptoComOrderSide.SELL,
            type,
            price,
            stopOrder.getOriginalAmount().toPlainString(),
            null,
            stopOrder.getStopPrice().toPlainString(),
            null,
            stopOrder.getUserReference());
    return requireOrderId(placement);
  }

  /**
   * Extracts the provider order id from a placement result. {@link
   * CryptoComPlacementOutcome#NOT_FOUND} classifies the order as not surfaced within the bounded
   * reconciliation window (open orders plus recent history after a placement transport failure),
   * not as an authoritative provider rejection: an order not visible there may still exist. The
   * caller must verify order state manually before re-submitting; nothing is ever re-sent
   * automatically. An ambiguous outcome has already raised {@link
   * CryptoComUnknownOrderOutcomeException} at the raw layer.
   */
  private String requireOrderId(CryptoComOrderPlacementResult placement) {
    if (placement.getOrderId() == null) {
      throw new ExchangeException(
          "Crypto.com placement outcome is NOT_FOUND: the order was not surfaced in open orders "
              + "or the bounded reconciliation window (requestId="
              + placement.getRequestId()
              + ", clientOid="
              + placement.getClientOid()
              + "). This absence is unconfirmed beyond that window - verify the order state "
              + "manually before re-submitting to avoid duplicates; nothing was re-sent");
    }
    return placement.getOrderId();
  }

  @Override
  public boolean cancelOrder(String orderId) throws IOException {
    cancelCryptoComOrder(orderId);
    return true;
  }

  @Override
  public boolean cancelOrder(CancelOrderParams orderParams) throws IOException {
    if (orderParams instanceof CancelOrderByIdParams) {
      return cancelOrder(((CancelOrderByIdParams) orderParams).getOrderId());
    }
    throw new NotAvailableFromExchangeException("cancelOrder requires an order id");
  }

  @Override
  public OpenOrders getOpenOrders() throws IOException {
    return getOpenOrders(createOpenOrdersParams());
  }

  @Override
  public OpenOrders getOpenOrders(OpenOrdersParams params) throws IOException {
    String instrumentName =
        params instanceof OpenOrdersParamInstrument
            ? CryptoComAdapters.toInstrumentName(
                ((OpenOrdersParamInstrument) params).getInstrument())
            : null;
    List<CryptoComOrder> orders = getCryptoComOpenOrders(instrumentName);
    return CryptoComAdapters.adaptOpenOrders(orders);
  }

  @Override
  public OpenOrdersParams createOpenOrdersParams() {
    return new DefaultOpenOrdersParamInstrument();
  }

  @Override
  public UserTrades getTradeHistory(TradeHistoryParams params) throws IOException {
    String instrumentName =
        params instanceof TradeHistoryParamInstrument
            ? CryptoComAdapters.toInstrumentName(
                ((TradeHistoryParamInstrument) params).getInstrument())
            : null;
    Integer limit =
        params instanceof TradeHistoryParamLimit
            ? ((TradeHistoryParamLimit) params).getLimit()
            : null;
    Long startTime = null;
    Long endTime = null;
    if (params instanceof TradeHistoryParamsTimeSpan) {
      TradeHistoryParamsTimeSpan timeSpan = (TradeHistoryParamsTimeSpan) params;
      startTime = CryptoComAdapters.toEpochMillis(timeSpan.getStartTime());
      endTime = CryptoComAdapters.toEpochMillis(timeSpan.getEndTime());
    }

    return CryptoComAdapters.adaptUserTrades(
        getCryptoComUserTrades(instrumentName, startTime, endTime, limit));
  }

  @Override
  public TradeHistoryParams createTradeHistoryParams() {
    return new CryptoComTradeHistoryParams();
  }

  @Override
  public Collection<Order> getOrder(OrderQueryParams... orderQueryParams) throws IOException {
    List<Order> orders = new ArrayList<>();
    for (OrderQueryParams params : orderQueryParams) {
      CryptoComOrder order = getCryptoComOrderDetail(params.getOrderId());
      if (order != null) {
        orders.add(CryptoComAdapters.adaptOrder(order));
      }
    }
    return orders;
  }
}
