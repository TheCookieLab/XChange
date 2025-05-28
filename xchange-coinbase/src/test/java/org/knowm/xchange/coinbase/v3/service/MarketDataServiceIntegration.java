package org.knowm.xchange.coinbase.v3.service;

import static org.junit.Assert.assertEquals;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.coinbase.v3.CoinbaseExchange;
import org.knowm.xchange.coinbase.v3.dto.pricebook.CoinbasePriceBook;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.CandleStickData;
import org.knowm.xchange.dto.marketdata.Trades;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.service.trade.params.DefaultCandleStickParam;
import org.knowm.xchange.service.trade.params.DefaultCandleStickParamWithLimit;
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

    List<CoinbasePriceBook> priceBooks = marketDataService.getBestBidAsk(Currency.BTC,
        Currency.USD);
    assertEquals(1, priceBooks.size());
  }

  @Test
  public void getMarketTradesWithLimit() throws Exception {
    Assume.assumeNotNull(marketDataService.authTokenCreator);

    Instrument currencyPair = CurrencyPair.ETH_USD;
    Trades trades = marketDataService.getTrades(currencyPair, 1);

    assertEquals(1, trades.getTrades().size());
    assertEquals(currencyPair, trades.getTrades().get(0).getInstrument());
  }

  @Test
  public void getETHUSDProductCandleSticksWithLimit() throws Exception {
    Assume.assumeNotNull(marketDataService.authTokenCreator);

    CurrencyPair currencyPair = CurrencyPair.ETH_USD;
    int limit = 10;
    CandleStickData candleStickData = marketDataService.getCandleStickData(currencyPair,
        new DefaultCandleStickParamWithLimit(null, null, 86_400, limit));

    assertEquals(limit, candleStickData.getCandleSticks().size());
    assertEquals(currencyPair, candleStickData.getInstrument());
  }

  @Test
  public void getETHUSDProductCandleSticksWithStartDate() throws Exception {
    Assume.assumeNotNull(marketDataService.authTokenCreator);

    CurrencyPair currencyPair = CurrencyPair.ETH_USD;
    int daysInPast = 100;
    Date startDate = Date.from(
        LocalDate.now().minusDays(daysInPast).atStartOfDay(ZoneOffset.UTC).toInstant());
    Date endDate = null;
    CandleStickData candleStickData = marketDataService.getCandleStickData(currencyPair,
        new DefaultCandleStickParam(startDate, endDate, 86_400));

    assertEquals(daysInPast + 1, candleStickData.getCandleSticks().size());
    assertEquals(currencyPair, candleStickData.getInstrument());
  }
}
