package org.knowm.xchange.examples.okx.v5.trade;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Date;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.derivative.FuturesContract;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.OpenOrders;
import org.knowm.xchange.okx.OkxExchange;
import org.knowm.xchange.okx.dto.trade.OkxTradeParams;
import org.knowm.xchange.okx.dto.trade.OkxTradeParams.OkxCancelOrderParams;
import org.knowm.xchange.service.trade.TradeService;

public class OkxOrdersDemo {

  public static void main(String[] args) throws IOException {

    ExchangeSpecification exSpec = new ExchangeSpecification(OkxExchange.class);
    exSpec.setSecretKey("");
    exSpec.setApiKey("");
    exSpec.setExchangeSpecificParametersItem("passphrase", "");
    Exchange okxExchange = ExchangeFactory.INSTANCE.createExchange(exSpec);

    generic(okxExchange);
  }

  private static void generic(Exchange okxExchange) throws IOException {

    TradeService tradeService = okxExchange.getTradeService();
    FuturesContract contract = new FuturesContract(CurrencyPair.BTC_USDT, "210924");

    OpenOrders openOrders = tradeService.getOpenOrders();
    System.out.println(openOrders);

    /*    OpenPositions futuresPosition = tradeService.getOpenPositions();

    List<OpenPosition> positions = futuresPosition.getOpenPositions();

    for (OpenPosition position : positions) {
      System.out.println(position);
    }*/
    // Place 1 lot buy limit order at price 200 for the BTC_UST Sept 24th Contact
    try {
      String placeLimitOrder =
          tradeService.placeLimitOrder(
              new LimitOrder(
                  OrderType.BID,
                  new BigDecimal("1"),
                  contract,
                  "0",
                  new Date(),
                  new BigDecimal("200")));
      System.out.println(placeLimitOrder);

      OkxCancelOrderParams req =
          new OkxTradeParams.OkxCancelOrderParams(contract, placeLimitOrder);

      boolean cancelOrder = tradeService.cancelOrder(req);
      System.out.println("Cancelled " + cancelOrder);
    } catch (Exception | Error ex) {
      System.out.println("Unable to place order due to  " + ex);
    }
  }
}
