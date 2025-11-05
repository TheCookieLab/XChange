package org.knowm.xchange.examples.coinbase.trade;

import java.io.IOException;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseListOrdersResponse;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseOrderDetailResponse;
import org.knowm.xchange.coinbase.v3.dto.trade.CoinbaseTradeHistoryParams;
import org.knowm.xchange.coinbase.v3.service.CoinbaseTradeService;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.trade.OpenOrders;
import org.knowm.xchange.dto.trade.UserTrades;
import org.knowm.xchange.examples.coinbase.CoinbaseDemoUtils;
import org.knowm.xchange.service.trade.TradeService;

/**
 * @deprecated This example class is deprecated. For code examples and usage, refer to:
 * <ul>
 *   <li>{@link org.knowm.xchange.coinbase.v3.service.TradeServiceIntegration TradeServiceIntegration}</li>
 *   <li>{@link org.knowm.xchange.coinbase.v3.service.TradeServiceSandboxIntegration TradeServiceSandboxIntegration}</li>
 * </ul>
 * @author jamespedwards42
 */
@SuppressWarnings("JavadocReference")
@Deprecated
public class CoinbaseTradeDemo {

  public static void main(String[] args) throws IOException {

    Exchange coinbase = CoinbaseDemoUtils.createExchange();
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
    params.addCurrencyPair(CurrencyPair.BTC_USD); // Optional: filter by currency pair
    UserTrades trades = tradeService.getTradeHistory(params);
    System.out.println("Trade History: " + trades);
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
}
