package org.knowm.xchange.kucoin.examples;

import java.math.BigDecimal;
import java.util.List;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.kucoin.KucoinApiMode;
import org.knowm.xchange.kucoin.KucoinExchange;
import org.knowm.xchange.kucoin.uta.UtaAccountService;
import org.knowm.xchange.kucoin.uta.UtaMarketDataService;
import org.knowm.xchange.kucoin.uta.UtaTradeService;
import org.knowm.xchange.kucoin.uta.dto.UtaAccountBalance;
import org.knowm.xchange.kucoin.uta.dto.UtaOrderPlaceRequest;
import org.knowm.xchange.kucoin.uta.dto.UtaOrderResult;
import org.knowm.xchange.kucoin.uta.dto.UtaPosition;

/**
 * CF-449 migration example: UTA mode setup, public metadata, unified balances, positions, and a
 * placement-safe order.
 *
 * <p>Requires KuCoin UTA (UNIFIED account) API credentials. Never run with real funds unless you
 * understand the no-blind-replay semantics: a placement whose outcome is unknown is never
 * automatically resubmitted.
 */
public final class UtaExample {

  public static void main(String[] args) throws Exception {
    KucoinExchange exchange =
        ExchangeFactory.INSTANCE.createExchangeWithoutSpecification(KucoinExchange.class);
    ExchangeSpecification spec = exchange.getDefaultExchangeSpecification();
    spec.setApiKey(System.getenv("KUCKOIN_API_KEY"));
    spec.setSecretKey(System.getenv("KUCKOIN_API_SECRET"));
    spec.setExchangeSpecificParametersItem("passphrase", System.getenv("KUCKOIN_API_PASSPHRASE"));
    spec.setExchangeSpecificParametersItem(KucoinExchange.API_MODE_PARAMETER, KucoinApiMode.UTA);
    exchange.applySpecification(spec);

    // Public metadata: no credentials required.
    exchange.remoteInit();

    UtaMarketDataService marketData = exchange.getUtaMarketDataService();
    Ticker ticker = marketData.getTicker(CurrencyPair.BTC_USDT);
    System.out.println("BTC-USDT last: " + ticker.getLast());

    UtaAccountService account = exchange.getUtaAccountService();
    account.verifyUnifiedMode(); // fails actionably when the credentials are not UNIFIED
    UtaAccountBalance balance = account.getUtaAccountBalance();
    System.out.println("UTA balances: " + balance.getAccounts());

    UtaTradeService trade = exchange.getUtaTradeService();
    List<UtaPosition> positions = trade.getOpenPositionsRaw(null);
    System.out.println("Open positions: " + positions.size());

    UtaOrderPlaceRequest order =
        UtaOrderPlaceRequest.builder()
            .tradeType("SPOT")
            .symbol("BTC-USDT")
            .clientOid("example-" + System.currentTimeMillis())
            .side("BUY")
            .orderType("LIMIT")
            .size("0.0001")
            .sizeUnit("BASECCY")
            .price(ticker.getAsk() == null ? "1" : ticker.getAsk().toPlainString())
            .build();
    UtaOrderResult result = trade.placeOrderSafe(order, CurrencyPair.BTC_USDT);
    System.out.println("Placed order: " + result.getOrderId());
  }
}
