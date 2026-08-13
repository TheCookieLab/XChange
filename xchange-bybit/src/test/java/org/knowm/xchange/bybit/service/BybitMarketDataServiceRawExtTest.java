package org.knowm.xchange.bybit.service;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Date;
import org.junit.Test;
import org.knowm.xchange.bybit.BybitAdapters;
import org.knowm.xchange.bybit.dto.BybitCategory;
import org.knowm.xchange.bybit.dto.marketdata.BybitOpenInterest;
import org.knowm.xchange.bybit.dto.marketdata.BybitPublicTrade;
import org.knowm.xchange.bybit.dto.marketdata.BybitServerTime;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.Trade;
import org.knowm.xchange.dto.marketdata.Trades;
import org.knowm.xchange.instrument.Instrument;

public class BybitMarketDataServiceRawExtTest extends BaseWiremockTest {

  @Test
  public void publicTradesPreserveExactDecimals() throws IOException {
    initGetStub(
        "/v5/market/public-trades",
        "/getPublicTrades.json5",
        "category",
        equalTo("spot"));
    initGetStub(
        "/v5/market/public-trades",
        "/getPublicTrades.json5",
        "symbol",
        equalTo("BTCUSDT"));

    BybitMarketDataServiceRaw raw =
        (BybitMarketDataServiceRaw) createExchange().getMarketDataService();
    java.util.List<BybitPublicTrade> trades =
        raw.getPublicTrades(BybitCategory.SPOT, "BTCUSDT", 2).getResult().getList();

    assertEquals(2, trades.size());
    assertEquals("33125.42", trades.get(0).getPrice());
    assertEquals(new BigDecimal("33125.42"), new BigDecimal(trades.get(0).getPrice()));
    assertEquals("0.011", trades.get(0).getSize());
    assertEquals("33124.987654321", trades.get(1).getPrice());
    assertEquals("0.0000001", trades.get(1).getSize());
    assertEquals("Buy", trades.get(0).getSide());
    assertFalse(trades.get(0).getIsBlockTrade());
    assertTrue(trades.get(1).getIsBlockTrade());
  }

  @Test
  public void genericGetTradesMapsExactly() throws IOException {
    initGetStub(
        "/v5/market/public-trades",
        "/getPublicTrades.json5",
        "category",
        equalTo("spot"));
    initGetStub(
        "/v5/market/public-trades",
        "/getPublicTrades.json5",
        "symbol",
        equalTo("BTCUSDT"));

    Instrument instrument = CurrencyPair.BTC_USDT;
    Trades trades = createExchange().getMarketDataService().getTrades(instrument);

    assertEquals(2, trades.getTrades().size());
    Trade first = trades.getTrades().get(0);
    assertEquals("b6b5f5e4e8f0f2f4-0", first.getId());
    assertEquals(new BigDecimal("33125.42"), first.getPrice());
    assertEquals(new BigDecimal("0.011"), first.getOriginalAmount());
    assertEquals(new Date(1672304894063L), first.getTimestamp());
    assertEquals(CurrencyPair.BTC_USDT, first.getInstrument());
    Trade second = trades.getTrades().get(1);
    assertEquals(new BigDecimal("33124.987654321"), second.getPrice());
    assertEquals(new BigDecimal("0.0000001"), second.getOriginalAmount());
  }

  @Test
  public void serverTimeParses() throws IOException {
    initGetStub("/v5/market/time", "/getServerTime.json5");

    BybitMarketDataServiceRaw raw =
        (BybitMarketDataServiceRaw) createExchange().getMarketDataService();
    BybitServerTime serverTime = raw.getServerTime();

    assertNotNull(serverTime);
    assertEquals("1672304894", serverTime.getTimeSecond());
    assertEquals("1672304894063647852", serverTime.getTimeNano());
  }

  @Test
  public void openInterestParses() throws IOException {
    initGetStub(
        "/v5/market/open-interest",
        "/getOpenInterest.json5",
        "category",
        equalTo("linear"));
    initGetStub(
        "/v5/market/open-interest", "/getOpenInterest.json5", "symbol", equalTo("BTCUSDT"));
    initGetStub(
        "/v5/market/open-interest",
        "/getOpenInterest.json5",
        "intervalTime",
        equalTo("5min"));

    BybitMarketDataServiceRaw raw =
        (BybitMarketDataServiceRaw) createExchange().getMarketDataService();
    BybitOpenInterest openInterest =
        raw.getOpenInterest(BybitCategory.LINEAR, "BTCUSDT", "5min", null);

    assertEquals("linear", openInterest.getCategory());
    assertEquals("BTCUSDT", openInterest.getSymbol());
    assertEquals("190.937", openInterest.getOpenInterest());
    assertEquals("1672300000000", openInterest.getTimestamp());
  }

  @Test
  public void adaptPublicTradeMapsSideToOrderType() {
    Trade buy =
        BybitAdapters.adaptPublicTrade(
            BybitPublicTrade.builder()
                .execId("id-1")
                .symbol("BTCUSDT")
                .price("100.5")
                .size("0.5")
                .side("Buy")
                .time("1672304894063")
                .isBlockTrade(false)
                .build(),
            CurrencyPair.BTC_USDT);
    assertEquals(org.knowm.xchange.dto.Order.OrderType.BID, buy.getType());

    Trade sell =
        BybitAdapters.adaptPublicTrade(
            BybitPublicTrade.builder()
                .execId("id-2")
                .symbol("BTCUSDT")
                .price("100.5")
                .size("0.5")
                .side("Sell")
                .time("1672304894063")
                .isBlockTrade(true)
                .build(),
            CurrencyPair.BTC_USDT);
    assertEquals(org.knowm.xchange.dto.Order.OrderType.ASK, sell.getType());
  }
}
