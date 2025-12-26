package org.knowm.xchange.coinbase.v3.dto.orders;

import org.knowm.xchange.coinbase.CoinbaseAdapters;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.dto.trade.StopOrder;

public final class CoinbaseV3OrderRequests {

  private CoinbaseV3OrderRequests() {}

  public static CoinbaseOrderRequest marketOrderRequest(MarketOrder order) {
    CoinbaseMarketMarketIoc configPayload = order.getType() == Order.OrderType.BID
        ? new CoinbaseMarketMarketIoc(order.getOriginalAmount(), null)
        : new CoinbaseMarketMarketIoc(null, order.getOriginalAmount());

    CoinbaseOrderConfiguration config = CoinbaseOrderConfiguration.marketMarketIoc(configPayload);
    return commonOrderRequest(order, config, true);
  }

  public static CoinbaseOrderRequest limitOrderRequest(LimitOrder order) {
    CoinbaseLimitLimitGtc limit = new CoinbaseLimitLimitGtc(
        null,
        order.getOriginalAmount(),
        order.getLimitPrice(),
        Boolean.FALSE);
    CoinbaseOrderConfiguration config = CoinbaseOrderConfiguration.limitLimitGtc(limit);
    return commonOrderRequest(order, config, true);
  }

  public static CoinbaseEditOrderRequest editLimitOrderRequest(LimitOrder order) {
    return new CoinbaseEditOrderRequest(
        order.getId(),
        order.getLimitPrice(),
        order.getOriginalAmount(),
        null,
        null,
        null);
  }

  /**
   * Creates a stop-limit order request for Coinbase Advanced Trade API.
   * 
   * <p>Note: Advanced Trade API infers stop direction from the order side, so stop_direction
   * is not explicitly set. For BUY orders, the stop triggers when price rises above stop_price.
   * For SELL orders, the stop triggers when price falls below stop_price.
   * 
   * @param order the stop order containing size, limit price, and stop price
   * @return the request payload
   */
  public static CoinbaseOrderRequest stopOrderRequest(StopOrder order) {
    CoinbaseStopLimitStopLimitGtc stop = new CoinbaseStopLimitStopLimitGtc(
        order.getOriginalAmount(),
        order.getLimitPrice(),
        order.getStopPrice(),
        null);
    CoinbaseOrderConfiguration config = CoinbaseOrderConfiguration.stopLimitStopLimitGtc(stop);
    return commonOrderRequest(order, config, true);
  }

  public static CoinbaseOrderRequest previewMarketOrderRequest(MarketOrder order) {
    CoinbaseMarketMarketIoc configPayload = order.getType() == Order.OrderType.BID
        ? new CoinbaseMarketMarketIoc(order.getOriginalAmount(), null)
        : new CoinbaseMarketMarketIoc(null, order.getOriginalAmount());

    CoinbaseOrderConfiguration config = CoinbaseOrderConfiguration.marketMarketIoc(configPayload);
    return commonOrderRequest(order, config, false);
  }

  public static CoinbaseOrderRequest previewLimitOrderRequest(LimitOrder order) {
    CoinbaseLimitLimitGtc limit = new CoinbaseLimitLimitGtc(
        null,
        order.getOriginalAmount(),
        order.getLimitPrice(),
        Boolean.FALSE);
    CoinbaseOrderConfiguration config = CoinbaseOrderConfiguration.limitLimitGtc(limit);
    return commonOrderRequest(order, config, false);
  }

  public static CoinbaseOrderRequest previewStopOrderRequest(StopOrder order) {
    CoinbaseStopLimitStopLimitGtc stop = new CoinbaseStopLimitStopLimitGtc(
        order.getOriginalAmount(),
        order.getLimitPrice(),
        order.getStopPrice(),
        null);
    CoinbaseOrderConfiguration config = CoinbaseOrderConfiguration.stopLimitStopLimitGtc(stop);
    return commonOrderRequest(order, config, false);
  }

  private static CoinbaseOrderRequest commonOrderRequest(
      Order order, CoinbaseOrderConfiguration config, boolean includeClientOrderId) {
    String clientOrderId = includeClientOrderId ? order.getUserReference() : null;
    CoinbaseOrderSide side =
        order.getType() == Order.OrderType.BID ? CoinbaseOrderSide.BUY : CoinbaseOrderSide.SELL;
    return new CoinbaseOrderRequest(
        clientOrderId,
        CoinbaseAdapters.adaptProductId(order.getInstrument()),
        side,
        config,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }
}
