package org.knowm.xchange.kraken;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.account.Fee;
import org.knowm.xchange.dto.account.FundingRecord;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trades;
import org.knowm.xchange.dto.meta.CurrencyMetaData;
import org.knowm.xchange.dto.meta.ExchangeMetaData;
import org.knowm.xchange.dto.meta.FeeTier;
import org.knowm.xchange.dto.meta.InstrumentMetaData;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.dto.trade.OpenOrders;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.dto.trade.UserTrades;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.kraken.dto.account.KrakenLedger;
import org.knowm.xchange.kraken.dto.account.KrakenTradeVolume;
import org.knowm.xchange.kraken.dto.account.LedgerType;
import org.knowm.xchange.kraken.dto.account.results.KrakenLedgerResult;
import org.knowm.xchange.kraken.dto.account.results.KrakenTradeVolumeResult;
import org.knowm.xchange.kraken.dto.marketdata.KrakenAsset;
import org.knowm.xchange.kraken.dto.marketdata.KrakenAssetPair;
import org.knowm.xchange.kraken.dto.marketdata.KrakenDepth;
import org.knowm.xchange.kraken.dto.marketdata.KrakenFee;
import org.knowm.xchange.kraken.dto.marketdata.results.KrakenAssetPairsResult;
import org.knowm.xchange.kraken.dto.marketdata.results.KrakenAssetsResult;
import org.knowm.xchange.kraken.dto.marketdata.results.KrakenDepthResult;
import org.knowm.xchange.kraken.dto.marketdata.results.KrakenPublicTradesResult;
import org.knowm.xchange.kraken.dto.marketdata.results.KrakenTickerResult;
import org.knowm.xchange.kraken.dto.trade.KrakenTrade;
import org.knowm.xchange.kraken.dto.trade.KrakenUserTrade;
import org.knowm.xchange.kraken.dto.trade.results.KrakenOpenOrdersResult;
import org.knowm.xchange.kraken.dto.trade.results.KrakenQueryOrderResult;
import org.knowm.xchange.kraken.dto.trade.results.KrakenTradeHistoryResult;
import org.knowm.xchange.kraken.dto.trade.results.KrakenTradeHistoryResult.KrakenTradeHistory;

public class KrakenAdaptersTest {

  @BeforeAll
  public static void before() throws IOException {

    KrakenUtils.clearAssets();
    // Read in the JSON from the example resources
    InputStream is =
        KrakenAdaptersTest.class.getResourceAsStream(
            "/org/knowm/xchange/kraken/dto/marketdata/example-assets-data.json");
    // Use Jackson to parse it
    ObjectMapper mapper = new ObjectMapper();
    KrakenAssetsResult krakenResult = mapper.readValue(is, KrakenAssetsResult.class);
    KrakenUtils.setKrakenAssets(krakenResult.getResult());

    // Read in the JSON from the example resources
    is =
        KrakenAdaptersTest.class.getResourceAsStream(
            "/org/knowm/xchange/kraken/dto/marketdata/example-assetpairs-data.json");
    // Use Jackson to parse it
    mapper = new ObjectMapper();
    KrakenAssetPairsResult krakenAssetPairs = mapper.readValue(is, KrakenAssetPairsResult.class);
    KrakenUtils.setKrakenAssetPairs(krakenAssetPairs.getResult());
  }

  @Test
  public void testAdaptTicker() throws IOException {

    // Read in the JSON from the example resources
    InputStream is =
        KrakenAdaptersTest.class.getResourceAsStream(
            "/org/knowm/xchange/kraken/dto/marketdata/example-ticker-data.json");

    // Use Jackson to parse it
    ObjectMapper mapper = new ObjectMapper();
    KrakenTickerResult krakenTicker = mapper.readValue(is, KrakenTickerResult.class);
    CurrencyPair currencyPair = CurrencyPair.BTC_EUR;
    String krakenCurencyPair = "XXBTZEUR";
    Ticker ticker =
        KrakenAdapters.adaptTicker(krakenTicker.getResult().get(krakenCurencyPair), currencyPair);

    // Verify that the example data was unmarshalled correctly
    assertThat(ticker.getAsk()).isEqualTo(new BigDecimal("562.26651"));
    assertThat(ticker.getBid()).isEqualTo(new BigDecimal("560.46600"));
    assertThat(ticker.getLow()).isEqualTo(new BigDecimal("560.00000"));
    assertThat(ticker.getHigh()).isEqualTo(new BigDecimal("591.11000"));
    assertThat(ticker.getLast()).isEqualTo(new BigDecimal("560.87711"));
    assertThat(ticker.getVwap()).isEqualTo(new BigDecimal("576.77284"));
    assertThat(ticker.getVolume()).isEqualByComparingTo("600.91850325");
    assertThat(ticker.getOpen()).isEqualTo(new BigDecimal("568.98910"));
    assertThat(ticker.getCurrencyPair().getBase().getCurrencyCode())
        .isEqualTo(currencyPair.getBase().getCurrencyCode());
  }

  @Test
  public void testAdaptCurrencyPairs() throws IOException {

    // Read in the JSON from the example resources
    InputStream is =
        KrakenAdaptersTest.class.getResourceAsStream(
            "/org/knowm/xchange/kraken/dto/marketdata/example-assetpairs-data.json");

    // Use Jackson to parse it
    ObjectMapper mapper = new ObjectMapper();
    KrakenAssetPairsResult krakenAssetPairs = mapper.readValue(is, KrakenAssetPairsResult.class);

    Set<CurrencyPair> pairs =
        KrakenAdapters.adaptCurrencyPairs(krakenAssetPairs.getResult().keySet());
    assertThat(pairs).hasSize(75);
    assertThat(pairs.contains(CurrencyPair.BTC_USD)).isTrue();
  }

  @Test
  public void testAdaptTrades() throws IOException {

    // Read in the JSON from the example resources
    InputStream is =
        KrakenAdaptersTest.class.getResourceAsStream(
            "/org/knowm/xchange/kraken/dto/marketdata/example-trades-data.json");

    // Use Jackson to parse it
    ObjectMapper mapper = new ObjectMapper();
    KrakenPublicTradesResult krakenTrades = mapper.readValue(is, KrakenPublicTradesResult.class);

    Trades trades =
        KrakenAdapters.adaptTrades(
            krakenTrades.getResult().getTrades(),
            CurrencyPair.BTC_USD,
            krakenTrades.getResult().getLast());

    Assertions.assertEquals(14, trades.getTrades().size());
    assertThat(trades.getTrades().get(0).getPrice()).isEqualTo("1023.82219");
    assertThat(trades.getTrades().get(0).getType()).isEqualTo(OrderType.ASK);
    assertThat(trades.getTrades().get(0).getTimestamp()).isEqualTo(new Date(1385579841777L));
    assertThat(trades.getTrades().get(1).getOriginalAmount()).isEqualTo("0.01500000");
    assertThat(trades.getlastID()).isEqualTo(1385579841881785998L);
  }

  @Test
  public void testAdaptOrderBook() throws JsonParseException, JsonMappingException, IOException {

    // Read in the JSON from the example resources
    InputStream is =
        KrakenAdaptersTest.class.getResourceAsStream(
            "/org/knowm/xchange/kraken/dto/marketdata/example-depth-data.json");

    // Use Jackson to parse it
    ObjectMapper mapper = new ObjectMapper();
    KrakenDepthResult krakenDepthResult = mapper.readValue(is, KrakenDepthResult.class);
    Map<String, KrakenDepth> krakenDepths = krakenDepthResult.getResult();
    String krakenAssetPair = "XXBTZEUR";
    KrakenDepth krakenDepth = krakenDepths.get(krakenAssetPair);

    OrderBook orderBook = KrakenAdapters.adaptOrderBook(krakenDepth, CurrencyPair.BTC_EUR);

    List<LimitOrder> asks = orderBook.getAsks();

    assertThat(asks.size()).isEqualTo(3);
    LimitOrder order = asks.get(0);
    assertThat(order.getLimitPrice()).isEqualTo(new BigDecimal("530.75513"));
    assertThat(order.getOriginalAmount()).isEqualTo("0.248");
    assertThat(order.getTimestamp()).isEqualTo(new Date(1391825343000L));
  }

  @Test
  public void testAdaptOpenOrders() throws IOException {

    // Read in the JSON from the example resources
    InputStream is =
        KrakenAdaptersTest.class.getResourceAsStream(
            "/org/knowm/xchange/kraken/dto/trading/example-openorders-data.json");

    // Use Jackson to parse it
    ObjectMapper mapper = new ObjectMapper();
    KrakenOpenOrdersResult krakenResult = mapper.readValue(is, KrakenOpenOrdersResult.class);

    OpenOrders orders = KrakenAdapters.adaptOpenOrders(krakenResult.getResult().getOrders());

    // Verify that the example data was unmarshalled correctly
    assertThat(orders.getOpenOrders()).hasSize(6);
    assertThat(orders.getOpenOrders().get(0).getId()).isEqualTo("O767CW-TXHCL-FWZ5R2");
    assertThat(orders.getOpenOrders().get(0).getLimitPrice()).isEqualTo("0.00001000");
    assertThat(orders.getOpenOrders().get(0).getOriginalAmount()).isEqualTo("1000.00000000");
    assertThat(orders.getOpenOrders().get(0).getCurrencyPair().getBase()).isEqualTo(Currency.XRP);
    assertThat(orders.getOpenOrders().get(0).getCurrencyPair().getCounter())
        .isEqualTo(Currency.BTC);
    assertThat(orders.getOpenOrders().get(0).getType()).isEqualTo(OrderType.BID);
  }

  @Test
  public void testAdaptOpenOrdersInTransactionCurrency() throws IOException {

    // Read in the JSON from the example resources
    InputStream is =
        KrakenAdaptersTest.class.getResourceAsStream(
            "/org/knowm/xchange/kraken/dto/trading/example-openorders-in-transaction-currency-data.json");

    // Use Jackson to parse it
    ObjectMapper mapper = new ObjectMapper();
    KrakenOpenOrdersResult krakenResult = mapper.readValue(is, KrakenOpenOrdersResult.class);

    OpenOrders orders = KrakenAdapters.adaptOpenOrders(krakenResult.getResult().getOrders());

    // Verify that the example data was unmarshalled correctly
    assertThat(orders.getOpenOrders()).hasSize(1);
    assertThat(orders.getOpenOrders().get(0).getId()).isEqualTo("OR6QMM-BCKM4-Q6YHIN");
    assertThat(orders.getOpenOrders().get(0).getLimitPrice()).isEqualTo("500.00000");
    assertThat(orders.getOpenOrders().get(0).getOriginalAmount()).isEqualTo("1.00000000");
    assertThat(orders.getOpenOrders().get(0).getCurrencyPair().getBase()).isEqualTo(Currency.BTC);
    assertThat(orders.getOpenOrders().get(0).getCurrencyPair().getCounter())
        .isEqualTo(Currency.EUR);
    assertThat(orders.getOpenOrders().get(0).getType()).isEqualTo(OrderType.BID);
  }

  @Test
  public void testAdaptTradeHistory() throws JsonParseException, JsonMappingException, IOException {
    List<UserTrade> tradeList =
        loadUserTrades("/org/knowm/xchange/kraken/dto/trading/example-tradehistory-data.json");

    assertThat(tradeList.size()).isEqualTo(3);
    UserTrade trade = tradeList.get(0);
    assertThat(trade).isInstanceOf(KrakenUserTrade.class);
    assertThat(trade.getId()).isEqualTo("TY5BYV-WJUQF-XPYEYD-1");
    assertThat(trade.getPrice()).isEqualTo("32.07562");
    assertThat(trade.getOriginalAmount()).isEqualTo("0.50000000");
    assertThat(trade.getInstrument().getBase()).isEqualTo(Currency.LTC);
    assertThat(trade.getInstrument().getCounter()).isEqualTo(Currency.BTC);
    assertThat(trade.getType()).isEqualTo(OrderType.ASK);
    assertThat(trade.getFeeAmount()).isEqualTo("0.03208");
    assertThat(trade.getFeeCurrency()).isEqualTo(Currency.BTC);
    assertThat(((KrakenUserTrade) trade).getCost()).isEqualTo("16.03781");
  }

  @Test
  public void testIcnTradeHistory() throws JsonParseException, JsonMappingException, IOException {
    List<UserTrade> tradeList =
        loadUserTrades("/org/knowm/xchange/kraken/dto/trading/example-tradehistory-icn.json");

    assertThat(tradeList.size()).isEqualTo(1);
    UserTrade trade = tradeList.get(0);
    assertThat(trade).isInstanceOf(KrakenUserTrade.class);
    assertThat(trade.getId()).isEqualTo("TY5BYV-WJUQF-XPYEYD");
    assertThat(trade.getPrice()).isEqualTo("32.07562");
    assertThat(trade.getOriginalAmount()).isEqualTo("0.50000000");
    assertThat(trade.getInstrument().getBase()).isEqualTo(Currency.getInstance("XICNX"));
    assertThat(trade.getInstrument().getCounter()).isEqualTo(Currency.BTC);
    assertThat(trade.getType()).isEqualTo(OrderType.ASK);
    assertThat(trade.getFeeAmount()).isEqualTo("0.03208");
    assertThat(trade.getFeeCurrency()).isEqualTo(Currency.BTC);
    assertThat(((KrakenUserTrade) trade).getCost()).isEqualTo("16.03781");
  }

  private static List<UserTrade> loadUserTrades(String resourceName) throws IOException {
    // Read in the JSON from the example resources
    InputStream is = KrakenAdaptersTest.class.getResourceAsStream(resourceName);

    // Use Jackson to parse it
    ObjectMapper mapper = new ObjectMapper();
    KrakenTradeHistoryResult krakenResult = mapper.readValue(is, KrakenTradeHistoryResult.class);
    KrakenTradeHistory krakenTradeHistory = krakenResult.getResult();
    Map<String, KrakenTrade> krakenTradeHistoryMap = krakenTradeHistory.getTrades();

    UserTrades trades = KrakenAdapters.adaptTradesHistory(krakenTradeHistoryMap);
    return trades.getUserTrades();
  }

  @Test
  public void testAdaptFundingHistory()
      throws JsonParseException, JsonMappingException, IOException {

    // Read in the JSON from the example resources
    InputStream is =
        KrakenAdaptersTest.class.getResourceAsStream(
            "/org/knowm/xchange/kraken/dto/account/example-ledgerinfo-data.json");

    // Use Jackson to parse it
    ObjectMapper mapper = new ObjectMapper();
    KrakenLedgerResult krakenResult = mapper.readValue(is, KrakenLedgerResult.class);
    KrakenLedgerResult.KrakenLedgers ledgers = krakenResult.getResult();
    Map<String, KrakenLedger> ledgerMap = ledgers.getLedgerMap();

    List<FundingRecord> records = KrakenAdapters.adaptFundingHistory(ledgerMap);

    assertThat(records.size()).isEqualTo(5);
    FundingRecord fundingRecord = records.get(1);
    assertThat(fundingRecord).isInstanceOf(FundingRecord.class);
    assertThat(fundingRecord.getType()).isEqualTo(FundingRecord.Type.WITHDRAWAL);
    assertThat(fundingRecord.getStatus()).isEqualTo(FundingRecord.Status.COMPLETE);
    assertThat(fundingRecord.getAmount()).isEqualTo(new BigDecimal("15.9857300000"));
    assertThat(fundingRecord.getFee().doubleValue())
        .isEqualTo(new BigDecimal("0.02").doubleValue());
    assertThat(fundingRecord.getBalance().doubleValue()).isEqualTo(BigDecimal.ZERO.doubleValue());
  }

  @Test
  public void testAdaptMarketOrder() throws IOException {

    // Read in the JSON from the example resources
    InputStream is =
        KrakenAdaptersTest.class.getResourceAsStream(
            "/org/knowm/xchange/kraken/dto/order/example-market-ask-order.json");

    // Use Jackson to parse it
    ObjectMapper mapper = new ObjectMapper();
    KrakenQueryOrderResult krakenQueryOrderResult =
        mapper.readValue(is, KrakenQueryOrderResult.class);

    List<Order> orders = KrakenAdapters.adaptOrders(krakenQueryOrderResult.getResult());

    assertThat(orders.size()).isEqualTo(1);

    Order order = orders.get(0);

    assertThat(order.getId()).isEqualTo("OHR2QC-2XDSQ-WHOFMW");
    assertThat(order.getAveragePrice()).isEqualTo(new BigDecimal("260.23"));
    assertThat(order.getCumulativeAmount()).isEqualTo(new BigDecimal("0.84962599"));
    assertThat(order.getCurrencyPair()).isEqualTo(CurrencyPair.LTC_USD);
    assertThat(MarketOrder.class.isAssignableFrom(order.getClass()));
  }

  @Test
  public void testAdaptFees() throws IOException {
    // Read in the JSON from the example resources
    InputStream is =
        KrakenAdaptersTest.class.getResourceAsStream(
            "/org/knowm/xchange/kraken/dto/account/example-tradevolume-data-2.json");

    // Use Jackson to parse it
    KrakenTradeVolumeResult krakenTradeVolumeResult =
        new ObjectMapper().readValue(is, KrakenTradeVolumeResult.class);
    KrakenTradeVolume krakenTradeVolume = krakenTradeVolumeResult.getResult();

    Map<Instrument, Fee> feeMap = KrakenAdapters.adaptFees(krakenTradeVolume);

    assertThat(feeMap.size()).isEqualTo(krakenTradeVolume.getFees().size());
  }

  @Test
  public void testAdaptFeeTiers1() {
    List<KrakenFee> krakenMakerFees = new ArrayList<>();
    List<KrakenFee> krakenTakerFees = new ArrayList<>();

    krakenMakerFees.add(new KrakenFee(BigDecimal.TEN, BigDecimal.ONE));
    krakenTakerFees.add(new KrakenFee(BigDecimal.TEN, new BigDecimal(2)));
    krakenMakerFees.add(new KrakenFee(new BigDecimal(45), new BigDecimal("0.5")));
    krakenTakerFees.add(new KrakenFee(new BigDecimal(30), new BigDecimal("0.75")));

    FeeTier[] adaptedFeeTiers = KrakenAdapters.adaptFeeTiers(krakenMakerFees, krakenTakerFees);
    assertThat(adaptedFeeTiers.length).isEqualTo(3);

    assertThat(adaptedFeeTiers[0].beginQuantity).isEqualByComparingTo(BigDecimal.TEN);
    assertThat(adaptedFeeTiers[0].fee.getMakerFee())
        .isEqualByComparingTo(BigDecimal.ONE.movePointLeft(2));
    assertThat(adaptedFeeTiers[0].fee.getTakerFee())
        .isEqualByComparingTo(new BigDecimal(2).movePointLeft(2));

    assertThat(adaptedFeeTiers[1].beginQuantity).isEqualByComparingTo(new BigDecimal(30));
    assertThat(adaptedFeeTiers[1].fee.getMakerFee())
        .isEqualByComparingTo(BigDecimal.ONE.movePointLeft(2));
    assertThat(adaptedFeeTiers[1].fee.getTakerFee())
        .isEqualByComparingTo(new BigDecimal(0.75).movePointLeft(2));

    assertThat(adaptedFeeTiers[2].beginQuantity).isEqualByComparingTo(new BigDecimal(45));
    assertThat(adaptedFeeTiers[2].fee.getMakerFee())
        .isEqualByComparingTo(new BigDecimal(0.5).movePointLeft(2));
    assertThat(adaptedFeeTiers[2].fee.getTakerFee())
        .isEqualByComparingTo(new BigDecimal(0.75).movePointLeft(2));
  }

  @Test
  public void testAdaptToExchangeMetaData_AccurateFeesAndDecimals() throws IOException {
    ObjectMapper mapper = new ObjectMapper();
    KrakenAssetsResult krakenAssets =
        mapper.readValue(
            KrakenAdaptersTest.class.getResourceAsStream(
                "/org/knowm/xchange/kraken/dto/marketdata/example-assets-data.json"),
            KrakenAssetsResult.class);
    KrakenAssetPairsResult krakenAssetPairs =
        mapper.readValue(
            KrakenAdaptersTest.class.getResourceAsStream(
                "/org/knowm/xchange/kraken/dto/marketdata/example-assetpairs-data.json"),
            KrakenAssetPairsResult.class);

    ExchangeMetaData metaData =
        KrakenAdapters.adaptToExchangeMetaData(
            null, krakenAssetPairs.getResult(), krakenAssets.getResult());

    InstrumentMetaData xbtUsd = metaData.getInstruments().get(CurrencyPair.BTC_USD);
    assertThat(xbtUsd).isNotNull();
    // First taker tier 0.26% -> 0.0026, first maker tier 0.16% -> 0.0016
    assertThat(xbtUsd.getTradingFee()).isEqualByComparingTo("0.0026");
    assertThat(xbtUsd.getFeeTiers()).hasSize(9);
    assertThat(xbtUsd.getFeeTiers()[0].beginQuantity).isEqualByComparingTo("0");
    assertThat(xbtUsd.getFeeTiers()[0].fee.getMakerFee()).isEqualByComparingTo("0.0016");
    assertThat(xbtUsd.getFeeTiers()[0].fee.getTakerFee()).isEqualByComparingTo("0.0026");
    assertThat(xbtUsd.getFeeTiers()[8].beginQuantity).isEqualByComparingTo("10000000");
    assertThat(xbtUsd.getFeeTiers()[8].fee.getMakerFee()).isEqualByComparingTo("0.0000");
    assertThat(xbtUsd.getFeeTiers()[8].fee.getTakerFee()).isEqualByComparingTo("0.0010");
    // Decimals, steps, and order minimums in exact units
    assertThat(xbtUsd.getPriceScale()).isEqualTo(1);
    assertThat(xbtUsd.getVolumeScale()).isEqualTo(8);
    assertThat(xbtUsd.getMinimumAmount()).isEqualByComparingTo("0.002");
    assertThat(xbtUsd.getCounterMinimumAmount()).isEqualByComparingTo("0.5");
    assertThat(xbtUsd.getTradingFeeCurrency()).isEqualTo(Currency.USD);

    // Asset scale is preserved as currency metadata
    KrakenAsset adaAsset = krakenAssets.getResult().get("ADA");
    assertThat(adaAsset).isNotNull();
    CurrencyMetaData adaMeta = metaData.getCurrencies().get(Currency.ADA);
    assertThat(adaMeta).isNotNull();
    assertThat(adaMeta.getScale()).isEqualTo(adaAsset.getScale());
  }

  @Test
  public void testAdaptToExchangeMetaData_SkipsDarkMarkets() {
    KrakenAssetPair regular =
        KrakenAssetPair.builder()
            .altName("XBTUSD")
            .base("XXBT")
            .quote("ZUSD")
            .pairScale(1)
            .volumeLotScale(8)
            .volumeMultiplier(BigDecimal.ONE)
            .tickSize(new BigDecimal("0.1"))
            .orderMin(new BigDecimal("0.002"))
            .costMin(new BigDecimal("0.5"))
            .feeVolumeCurrency("ZUSD")
            .fees(List.of(new KrakenFee(BigDecimal.ZERO, new BigDecimal("0.26"))))
            .fees_maker(List.of(new KrakenFee(BigDecimal.ZERO, new BigDecimal("0.16"))))
            .build();
    KrakenAssetPair dark =
        KrakenAssetPair.builder()
            .altName("XBTUSD.d")
            .base("XXBT")
            .quote("ZUSD")
            .pairScale(1)
            .volumeLotScale(8)
            .volumeMultiplier(BigDecimal.ONE)
            .tickSize(new BigDecimal("0.1"))
            .orderMin(new BigDecimal("0.002"))
            .costMin(new BigDecimal("0.5"))
            .feeVolumeCurrency("ZUSD")
            .fees(List.of(new KrakenFee(BigDecimal.ZERO, new BigDecimal("0.26"))))
            .fees_maker(List.of(new KrakenFee(BigDecimal.ZERO, new BigDecimal("0.16"))))
            .build();
    KrakenAsset asset = new KrakenAsset("XXBT", "currency", 8, 6);

    ExchangeMetaData metaData =
        KrakenAdapters.adaptToExchangeMetaData(
            null, Map.of("XXBTZUSD", regular, "XXBTZUSD.d", dark), Map.of("XXBT", asset));

    assertThat(metaData.getInstruments().keySet()).containsExactly(CurrencyPair.BTC_USD);
  }

  @Test
  public void testAdaptPair_NoMisleadingFeeWhenUnavailable() {
    KrakenAsset asset = new KrakenAsset("XXBT", "currency", 8, 6);

    // No fee data at all: tradingFee must stay null instead of a misleading zero
    KrakenAssetPair noFees =
        KrakenAssetPair.builder()
            .altName("XBTUSD")
            .base("XXBT")
            .quote("ZUSD")
            .pairScale(1)
            .volumeLotScale(8)
            .volumeMultiplier(BigDecimal.ONE)
            .tickSize(new BigDecimal("0.1"))
            .orderMin(new BigDecimal("0.002"))
            .costMin(new BigDecimal("0.5"))
            .feeVolumeCurrency("ZUSD")
            .fees(List.of())
            .fees_maker(List.of())
            .build();
    ExchangeMetaData noFeeMeta =
        KrakenAdapters.adaptToExchangeMetaData(
            null, Map.of("XXBTZUSD", noFees), Map.of("XXBT", asset));
    assertThat(noFeeMeta.getInstruments().get(CurrencyPair.BTC_USD).getTradingFee()).isNull();
    assertThat(noFeeMeta.getInstruments().get(CurrencyPair.BTC_USD).getFeeTiers()).isEmpty();

    // Maker-only fee data falls back to the first maker tier
    KrakenAssetPair makerOnly =
        KrakenAssetPair.builder()
            .altName("XBTUSD")
            .base("XXBT")
            .quote("ZUSD")
            .pairScale(1)
            .volumeLotScale(8)
            .volumeMultiplier(BigDecimal.ONE)
            .tickSize(new BigDecimal("0.1"))
            .orderMin(new BigDecimal("0.002"))
            .costMin(new BigDecimal("0.5"))
            .feeVolumeCurrency("ZUSD")
            .fees(List.of())
            .fees_maker(List.of(new KrakenFee(BigDecimal.ZERO, new BigDecimal("0.1"))))
            .build();
    ExchangeMetaData makerMeta =
        KrakenAdapters.adaptToExchangeMetaData(
            null, Map.of("XXBTZUSD", makerOnly), Map.of("XXBT", asset));
    assertThat(makerMeta.getInstruments().get(CurrencyPair.BTC_USD).getTradingFee())
        .isEqualByComparingTo("0.001");
    assertThat(makerMeta.getInstruments().get(CurrencyPair.BTC_USD).getFeeTiers()).isEmpty();
  }

  @Test
  public void testAdaptFundingHistory_WithReceiveLedgerType() {
    // Test that adaptFundingHistory handles RECEIVE ledger type
    Map<String, KrakenLedger> ledgerMap = new java.util.HashMap<>();
    KrakenLedger ledger =
        new KrakenLedger(
            "ref123",
            System.currentTimeMillis() / 1000.0,
            LedgerType.RECEIVE,
            "currency",
            "XXBT", // Kraken currency code for BTC
            new BigDecimal("1.5"),
            BigDecimal.ZERO,
            new BigDecimal("100.0"));

    ledgerMap.put("ledger1", ledger);

    List<FundingRecord> records = KrakenAdapters.adaptFundingHistory(ledgerMap);

    assertThat(records).hasSize(1);
    FundingRecord record = records.get(0);
    assertThat(record.getType()).isEqualTo(FundingRecord.Type.DEPOSIT);
    assertThat(record.getAmount()).isEqualByComparingTo(new BigDecimal("1.5"));
  }

  @Test
  public void testAdaptFundingHistory_WithSpendLedgerType() {
    // Test that adaptFundingHistory handles SPEND ledger type
    Map<String, KrakenLedger> ledgerMap = new java.util.HashMap<>();
    KrakenLedger ledger =
        new KrakenLedger(
            "ref456",
            System.currentTimeMillis() / 1000.0,
            LedgerType.SPEND,
            "currency",
            "XETH", // Kraken currency code for ETH
            new BigDecimal("-0.5"),
            new BigDecimal("0.01"),
            new BigDecimal("50.0"));

    ledgerMap.put("ledger2", ledger);

    List<FundingRecord> records = KrakenAdapters.adaptFundingHistory(ledgerMap);

    assertThat(records).hasSize(1);
    FundingRecord record = records.get(0);
    assertThat(record.getType()).isEqualTo(FundingRecord.Type.WITHDRAWAL);
    assertThat(record.getAmount()).isEqualByComparingTo(new BigDecimal("0.5"));
  }

  @Test
  public void testAdaptFundingHistory_WithRewardLedgerType() {
    // Test that adaptFundingHistory handles REWARD ledger type
    Map<String, KrakenLedger> ledgerMap = new java.util.HashMap<>();
    KrakenLedger ledger =
        new KrakenLedger(
            "ref789",
            System.currentTimeMillis() / 1000.0,
            LedgerType.REWARD,
            "currency",
            "USDT",
            new BigDecimal("10.0"),
            BigDecimal.ZERO,
            new BigDecimal("1000.0"));

    ledgerMap.put("ledger3", ledger);

    List<FundingRecord> records = KrakenAdapters.adaptFundingHistory(ledgerMap);

    assertThat(records).hasSize(1);
    FundingRecord record = records.get(0);
    assertThat(record.getType()).isEqualTo(FundingRecord.Type.AIRDROP);
    assertThat(record.getAmount()).isEqualByComparingTo(new BigDecimal("10.0"));
  }

  @Test
  public void testLedgerType_FromString() {
    // Test that all new ledger types can be deserialized from strings
    assertThat(LedgerType.fromString("receive")).isEqualTo(LedgerType.RECEIVE);
    assertThat(LedgerType.fromString("spend")).isEqualTo(LedgerType.SPEND);
    assertThat(LedgerType.fromString("reward")).isEqualTo(LedgerType.REWARD);
  }
}
