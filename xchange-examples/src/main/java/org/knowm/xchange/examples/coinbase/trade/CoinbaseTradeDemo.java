package org.knowm.xchange.examples.coinbase.trade;

import java.io.IOException;
import java.math.BigDecimal;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseListOrdersResponse;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseOrderDetailResponse;
import org.knowm.xchange.coinbase.v3.dto.trade.CoinbaseTradeHistoryParams;
import org.knowm.xchange.coinbase.v3.service.CoinbaseTradeService;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.dto.trade.OpenOrders;
import org.knowm.xchange.dto.trade.UserTrades;
import org.knowm.xchange.examples.coinbase.CoinbaseDemoUtils;
import org.knowm.xchange.service.trade.TradeService;
import org.knowm.xchange.service.trade.params.DefaultCancelOrderParamId;

/**
 * Example demonstrating Coinbase Advanced Trade API v3 trade operations.
 *
 * <p>This example shows how to:
 * <ul>
 *   <li>Get open orders</li>
 *   <li>Get trade history</li>
 *   <li>Preview orders using the order verification endpoints</li>
 *   <li>Place and cancel a limit order (optional, guarded)</li>
 *   <li>List orders (raw API access)</li>
 *   <li>Get order details</li>
 * </ul>
 *
 * <p><b>Note:</b> Trade operations require API authentication.
 *
 * @author jamespedwards42
 */
public class CoinbaseTradeDemo {

  private static final CurrencyPair PAIR = CurrencyPair.BTC_USD;
  private static final BigDecimal DEFAULT_SIZE = new BigDecimal("0.001");

  public static void main(String[] args) throws IOException {

    Exchange coinbase = CoinbaseDemoUtils.createExchange();
    if (!CoinbaseDemoUtils.isAuthConfigured(coinbase)) {
      System.out.println("No API credentials found (secret.keys). Trade examples require auth.");
      System.out.println("Add a secret.keys file or set API credentials before running.");
      return;
    }
    TradeService tradeService = coinbase.getTradeService();

    generic(tradeService);
    raw((CoinbaseTradeService) tradeService);
  }

  public static void generic(TradeService tradeService) throws IOException {

    // Get open orders
    OpenOrders openOrders = tradeService.getOpenOrders();
    System.out.println("Open Orders: " + openOrders);

    // Get trade history
    CoinbaseTradeHistoryParams params = new CoinbaseTradeHistoryParams();
    params.setLimit(10); // Limit to 10 trades
    params.addCurrencyPair(PAIR); // Optional: filter by currency pair
    UserTrades trades = tradeService.getTradeHistory(params);
    System.out.println("Trade History: " + trades);

    // Preview market and limit orders (safe, does not place orders)
    previewOrders(tradeService);

    // Optionally place and cancel a limit order
    placeAndCancelLimitOrder(tradeService);
  }

  public static void raw(CoinbaseTradeService tradeService) throws IOException {

    // List orders (raw response)
    CoinbaseListOrdersResponse ordersResponse = tradeService.listOrders();
    System.out.println("Orders Response: " + ordersResponse);

    // If there are orders, get details for the first one
    if (ordersResponse != null && !ordersResponse.getOrders().isEmpty()) {
      String orderId = ordersResponse.getOrders().get(0).getOrderId();
      CoinbaseOrderDetailResponse orderDetail = tradeService.getOrder(orderId);
      System.out.println("Order Detail: " + orderDetail);
    }
  }

  private static void previewOrders(TradeService tradeService) {
    try {
      MarketOrder marketOrder =
          new MarketOrder.Builder(Order.OrderType.BID, PAIR)
              .originalAmount(DEFAULT_SIZE)
              .userReference("preview-market")
              .build();
      tradeService.verifyOrder(marketOrder);
      System.out.println("Market order preview succeeded.");
    } catch (Exception e) {
      System.out.println("Market order preview failed: " + e.getMessage());
    }

    BigDecimal limitPrice = readDecimal("coinbase.limitPrice", "COINBASE_LIMIT_PRICE");
    if (limitPrice == null) {
      System.out.println("Skipping limit order preview: set coinbase.limitPrice or COINBASE_LIMIT_PRICE.");
      return;
    }

    try {
      LimitOrder limitOrder =
          new LimitOrder.Builder(Order.OrderType.BID, PAIR)
              .limitPrice(limitPrice)
              .originalAmount(DEFAULT_SIZE)
              .userReference("preview-limit")
              .build();
      tradeService.verifyOrder(limitOrder);
      System.out.println("Limit order preview succeeded.");
    } catch (Exception e) {
      System.out.println("Limit order preview failed: " + e.getMessage());
    }
  }

  private static void placeAndCancelLimitOrder(TradeService tradeService) throws IOException {
    if (!isEnabled("coinbase.placeOrders", "COINBASE_PLACE_ORDERS")) {
      System.out.println("Skipping live order placement. Set coinbase.placeOrders or COINBASE_PLACE_ORDERS to enable.");
      return;
    }

    BigDecimal limitPrice = readDecimal("coinbase.limitPrice", "COINBASE_LIMIT_PRICE");
    if (limitPrice == null) {
      System.out.println("Live order placement requires coinbase.limitPrice or COINBASE_LIMIT_PRICE.");
      return;
    }

    String clientOrderId = "xchange-demo-limit-" + System.currentTimeMillis();
    LimitOrder limitOrder =
        new LimitOrder.Builder(Order.OrderType.BID, PAIR)
            .limitPrice(limitPrice)
            .originalAmount(DEFAULT_SIZE)
            .userReference(clientOrderId)
            .build();
    String orderId = tradeService.placeLimitOrder(limitOrder);
    System.out.println("Placed limit order: " + orderId);

    boolean canceled = tradeService.cancelOrder(new DefaultCancelOrderParamId(orderId));
    System.out.println("Cancel result: " + canceled);
  }

  private static boolean isEnabled(String propertyKey, String envKey) {
    String propertyValue = System.getProperty(propertyKey);
    if (propertyValue == null || propertyValue.trim().isEmpty()) {
      propertyValue = System.getenv(envKey);
    }
    if (propertyValue == null) {
      return false;
    }
    String normalized = propertyValue.trim().toLowerCase();
    return "true".equals(normalized) || "1".equals(normalized) || "yes".equals(normalized);
  }

  private static BigDecimal readDecimal(String propertyKey, String envKey) {
    String value = System.getProperty(propertyKey);
    if (value == null || value.trim().isEmpty()) {
      value = System.getenv(envKey);
    }
    if (value == null || value.trim().isEmpty()) {
      return null;
    }
    return new BigDecimal(value.trim());
  }
}
