package org.knowm.xchange.bitfinex.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Collection;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.bitfinex.BitfinexExchangeWiremock;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.dto.trade.OpenOrders;
import org.knowm.xchange.service.trade.TradeService;

@Disabled
class BitfinexTradeServiceTest extends BitfinexExchangeWiremock {

  TradeService tradeService = exchange.getTradeService();


  @Test
  void valid_market_sell_order() throws IOException {
    MarketOrder marketOrder = new MarketOrder.Builder(OrderType.ASK, new CurrencyPair("USDT/USD"))
        .userReference("t-valid-market-sell-order")
        .originalAmount(new BigDecimal("30"))
//        .limitPrice(new BigDecimal("1.0012"))
        .build();

    String actualResponse = tradeService.placeMarketOrder(marketOrder);
    assertThat(actualResponse).isEqualTo("376835979523");
  }

  @Test
  void order_details() throws IOException {
    OpenOrders openOrders = tradeService.getOpenOrders();
    Collection<Order> orders = tradeService.getOrder("126925785590");

    System.out.println(orders);
  }


}