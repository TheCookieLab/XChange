package org.knowm.xchange.coinbase.v2.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.BeforeClass;
import org.junit.Test;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.coinbase.v2.CoinbaseExchange;
import org.knowm.xchange.coinbase.v2.dto.CoinbasePrice;
import org.knowm.xchange.coinbase.v2.dto.marketdata.CoinbaseCryptocurrencyData.CoinbaseCryptocurrency;
import org.knowm.xchange.coinbase.v2.dto.marketdata.CoinbaseFiatCurrencyData.CoinbaseFiatCurrency;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.service.marketdata.MarketDataService;

/**
 * @author timmolter
 */
public class MarketDataServiceIntegration {

  static Exchange exchange;
  static MarketDataService marketDataService;

  @BeforeClass
  public static void beforeClass() {
    exchange = ExchangeFactory.INSTANCE.createExchange(CoinbaseExchange.class);
    marketDataService = exchange.getMarketDataService();
  }

  @Test
  public void listCryptocurrencies() throws Exception {
    CoinbaseMarketDataService coinbaseService = (CoinbaseMarketDataService) marketDataService;
    List<CoinbaseCryptocurrency> currencies = coinbaseService.getCoinbaseCryptocurrencies();

    assertTrue(currencies.stream()
        .anyMatch(crypto -> crypto.getName().equals("Bitcoin") && crypto.getCode().equals("BTC")));
  }

  @Test
  public void listFiatCurrencies() throws Exception {
    CoinbaseMarketDataService coinbaseService = (CoinbaseMarketDataService) marketDataService;
    List<CoinbaseFiatCurrency> currencies = coinbaseService.getCoinbaseFiatCurrencies();

    assertTrue(currencies.stream()
        .anyMatch(fiat -> fiat.getName().equals("Euro") && fiat.getId().equals("EUR")));
  }

  @Test
  public void listExchangeRates() throws Exception {

    CoinbaseMarketDataService coinbaseService = (CoinbaseMarketDataService) marketDataService;
    Map<String, BigDecimal> exchangeRates = coinbaseService.getCoinbaseExchangeRates();
    assertTrue(exchangeRates.get("EUR") instanceof BigDecimal);
  }

  @Test
  public void listPrices() throws Exception {

    CoinbaseMarketDataService coinbaseService = (CoinbaseMarketDataService) marketDataService;
    CoinbasePrice money = coinbaseService.getCoinbaseBuyPrice(Currency.BTC, Currency.USD);
    assertThat(money).hasFieldOrPropertyWithValue("currency", Currency.USD)
        .hasNoNullFieldsOrProperties();

    money = coinbaseService.getCoinbaseSellPrice(Currency.BTC, Currency.USD);
    assertThat(money).hasFieldOrPropertyWithValue("currency", Currency.USD)
        .hasNoNullFieldsOrProperties();

    money = coinbaseService.getCoinbaseSpotRate(Currency.BTC, Currency.USD);
    assertThat(money).hasFieldOrPropertyWithValue("currency", Currency.USD)
        .hasNoNullFieldsOrProperties();

    money = coinbaseService.getCoinbaseSpotRate(Currency.BTC, Currency.USD);
    assertThat(money).hasFieldOrPropertyWithValue("currency", Currency.USD)
        .hasNoNullFieldsOrProperties();

    money = coinbaseService.getCoinbaseHistoricalSpotRate(Currency.BTC, Currency.USD, new Date());
    assertThat(money).hasFieldOrPropertyWithValue("currency", Currency.USD)
        .hasNoNullFieldsOrProperties();
  }
}
