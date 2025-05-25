package org.knowm.xchange.coinbase.v3.service;

import static org.junit.Assert.assertEquals;

import java.util.List;
import java.util.stream.Collectors;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.coinbase.v3.CoinbaseExchange;
import org.knowm.xchange.coinbase.v3.dto.accounts.CoinbaseAccount;
import org.knowm.xchange.coinbase.v3.dto.paymentmethods.CoinbasePaymentMethod;
import org.knowm.xchange.coinbase.v3.dto.pricebook.CoinbasePriceBook;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.utils.AuthUtils;

public class MarketDataServiceIntegration {

  static CoinbaseExchange exchange;
  static CoinbaseMarketDataService marketDataService;

  @BeforeClass
  public static void beforeClass() {
    ExchangeSpecification exchangeSpecification = ExchangeFactory.INSTANCE.createExchange(
        CoinbaseExchange.class).getDefaultExchangeSpecification();
    AuthUtils.setApiAndSecretKey(exchangeSpecification);
    exchange = (CoinbaseExchange) ExchangeFactory.INSTANCE.createExchange(exchangeSpecification);
    marketDataService = (CoinbaseMarketDataService) exchange.getMarketDataService();
  }

  @Test
  public void getBestBidAskWithCurrencyPair() throws Exception {
    Assume.assumeNotNull(marketDataService.authTokenCreator);

    List<CoinbasePriceBook> priceBooks = marketDataService.getBestBidAsk(CurrencyPair.ETH_USD);
    assertEquals(1, priceBooks.size());
  }

  @Test
  public void getBestBidAskWithBaseAndCounterCurrency() throws Exception {
    Assume.assumeNotNull(marketDataService.authTokenCreator);

    List<CoinbasePriceBook> priceBooks = marketDataService.getBestBidAsk(Currency.BTC, Currency.USD);
    assertEquals(1, priceBooks.size());
  }
}
