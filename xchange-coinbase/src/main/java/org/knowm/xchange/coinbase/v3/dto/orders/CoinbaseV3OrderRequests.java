package org.knowm.xchange.coinbase.v3.dto.orders;

import java.util.HashMap;
import java.util.Map;
import org.knowm.xchange.coinbase.CoinbaseAdapters;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.dto.trade.StopOrder;

public final class CoinbaseV3OrderRequests {

  private CoinbaseV3OrderRequests() {}

  public static Object marketOrderRequest(MarketOrder order) {
    Map<String, Object> root = commonRoot(order);
    Map<String, Object> config = new HashMap<>();
    Map<String, Object> market = new HashMap<>();
    
    // For BUY orders, use quote_size (amount of quote currency to spend)
    // For SELL orders, use base_size (amount of base currency to sell)
    if (order.getType() == Order.OrderType.BID) {
      market.put("quote_size", order.getOriginalAmount());
    } else {
      market.put("base_size", order.getOriginalAmount());
    }
    
    config.put("market_market_ioc", market);
    root.put("order_configuration", config);
    return root;
  }

  public static Object limitOrderRequest(LimitOrder order) {
    Map<String, Object> root = commonRoot(order);
    Map<String, Object> config = new HashMap<>();
    Map<String, Object> limit = new HashMap<>();
    limit.put("base_size", order.getOriginalAmount());
    limit.put("limit_price", order.getLimitPrice());
    limit.put("post_only", Boolean.FALSE);
    config.put("limit_limit_gtc", limit);
    root.put("order_configuration", config);
    return root;
  }

  public static Object editLimitOrderRequest(LimitOrder order) {
    Map<String, Object> root = new HashMap<>();
    root.put("order_id", order.getId());
    Map<String, Object> editConfig = new HashMap<>();
    Map<String, Object> limit = new HashMap<>();
    if (order.getOriginalAmount() != null) {
      limit.put("base_size", order.getOriginalAmount());
    }
    if (order.getLimitPrice() != null) {
      limit.put("limit_price", order.getLimitPrice());
    }
    editConfig.put("limit_limit_gtc", limit);
    root.put("order_configuration", editConfig);
    return root;
  }

  /**
   * Creates a stop-limit order request for Coinbase Advanced Trade API.
   * 
   * <p>Note: Advanced Trade API infers stop direction from the order side, so stop_direction
   * is not explicitly set. For BUY orders, the stop triggers when price rises above stop_price.
   * For SELL orders, the stop triggers when price falls below stop_price.
   * 
   * @param order the stop order containing size, limit price, and stop price
   * @return the request payload as a Map
   */
  public static Object stopOrderRequest(StopOrder order) {
    Map<String, Object> root = commonRoot(order);
    Map<String, Object> config = new HashMap<>();
    Map<String, Object> stop = new HashMap<>();
    stop.put("base_size", order.getOriginalAmount());
    stop.put("limit_price", order.getLimitPrice());
    stop.put("stop_price", order.getStopPrice());
    config.put("stop_limit_stop_limit_gtc", stop);
    root.put("order_configuration", config);
    return root;
  }

  private static Map<String, Object> commonRoot(Order order) {
    Map<String, Object> root = new HashMap<>();
    root.put("client_order_id", order.getUserReference());
    root.put("product_id", CoinbaseAdapters.adaptProductId(order.getInstrument()));
    root.put("side", order.getType() == Order.OrderType.BID ? "BUY" : "SELL");
    return root;
  }
}


