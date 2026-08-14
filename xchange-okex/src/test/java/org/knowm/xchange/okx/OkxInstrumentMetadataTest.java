package org.knowm.xchange.okx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.derivative.FuturesContract;
import org.knowm.xchange.derivative.OptionsContract;
import org.knowm.xchange.dto.meta.InstrumentMetaData;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.okx.dto.OkxException;
import org.knowm.xchange.okx.dto.OkxInstType;
import org.knowm.xchange.okx.dto.OkxResponse;
import org.knowm.xchange.okx.dto.account.OkxAccountConfig;
import org.knowm.xchange.okx.dto.marketdata.OkxInstrument;
import org.knowm.xchange.okx.service.OkxAccountService;
import org.knowm.xchange.okx.service.OkxMarketDataService;

/** Offline, deterministic coverage of Phase 2 instrument-metadata support. */
public class OkxInstrumentMetadataTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  static {
    MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  }

  private OkxInstrument btcUsdtSpot;
  private OkxInstrument ethUsdtSpot;
  private OkxInstrument btcUsdtMargin;
  private OkxInstrument btcUsdtSwap;
  private OkxInstrument btcUsdFutures;
  private OkxInstrument ethUsdtFutures;
  private OkxInstrument btcUsdCallOption;
  private OkxInstrument ethUsdPutOption;

  @Before
  public void setUp() throws IOException {
    List<OkxInstrument> spot = loadInstruments("instrumentsSpot.json5");
    List<OkxInstrument> margin = loadInstruments("instrumentsMargin.json5");
    List<OkxInstrument> swaps = loadInstruments("instrumentsSwap.json5");
    List<OkxInstrument> futures = loadInstruments("instrumentsFutures.json5");
    List<OkxInstrument> options = loadInstruments("instrumentsOption.json5");

    btcUsdtSpot = spot.get(0);
    ethUsdtSpot = spot.get(1);
    btcUsdtMargin = margin.get(0);
    btcUsdtSwap = swaps.get(0);
    btcUsdFutures = futures.get(0);
    ethUsdtFutures = futures.get(1);
    btcUsdCallOption = options.get(0);
    ethUsdPutOption = options.get(1);

    OkxAdapters.instrumentToInstrumentIdMap.clear();
  }

  @After
  public void tearDown() {
    OkxAdapters.instrumentToInstrumentIdMap.clear();
  }

  private static List<OkxInstrument> loadInstruments(String resource) throws IOException {
    InputStream is = OkxInstrumentMetadataTest.class.getResourceAsStream("/" + resource);
    assertThat(is).as("fixture %s", resource).isNotNull();
    return MAPPER.readValue(is, new TypeReference<OkxResponse<List<OkxInstrument>>>() {}).getData();
  }

  @Test
  public void testSpotFieldsMapLosslessly() {
    assertThat(btcUsdtSpot.getInstrumentType()).isEqualTo("SPOT");
    assertThat(btcUsdtSpot.getInstrumentId()).isEqualTo("BTC-USDT");
    assertThat(btcUsdtSpot.getUnderlying()).isEqualTo("");
    assertThat(btcUsdtSpot.getInstrumentFamily()).isEqualTo("");
    assertThat(btcUsdtSpot.getInstrumentCategory()).isEqualTo("1");
    assertThat(btcUsdtSpot.getBaseCurrency()).isEqualTo("BTC");
    assertThat(btcUsdtSpot.getQuoteCurrency()).isEqualTo("USDT");
    assertThat(btcUsdtSpot.getSettleCurrency()).isEqualTo("");
    assertThat(btcUsdtSpot.getContractValue()).isEqualTo("");
    assertThat(btcUsdtSpot.getContractValueCurrency()).isEqualTo("");
    assertThat(btcUsdtSpot.getContractMultiplier()).isEqualTo("");
    assertThat(btcUsdtSpot.getRuleType()).isEqualTo("normal");
    assertThat(btcUsdtSpot.getMaxLimitSize()).isEqualTo("9999999999");
    assertThat(btcUsdtSpot.getMaxMarketSize()).isEqualTo("1000000");
    assertThat(btcUsdtSpot.getMaxLimitAmount()).isEqualTo("20000000");
    assertThat(btcUsdtSpot.getMaxMarketAmount()).isEqualTo("1000000");
    assertThat(btcUsdtSpot.getMaxStopSize()).isEqualTo("1000000");
    assertThat(btcUsdtSpot.getMaxPriceLimitPercent()).isEqualTo("0.01");
    assertThat(btcUsdtSpot.getFloatPriceLimitPercent()).isEqualTo("0.005");
    assertThat(btcUsdtSpot.getInitialPriceLimitPercent()).isEqualTo("");
    assertThat(btcUsdtSpot.getLongPositionRemainingQuota()).isEqualTo("");
    assertThat(btcUsdtSpot.getShortPositionRemainingQuota()).isEqualTo("");
    assertThat(btcUsdtSpot.getMaxPlatformOICoinLimit()).isEqualTo("");
    assertThat(btcUsdtSpot.getMaxPlatformOILimit()).isEqualTo("");
    assertThat(btcUsdtSpot.getAuctionEndTime()).isEqualTo("");
    assertThat(btcUsdtSpot.getContractTradingSwitchTime()).isEqualTo("");
    assertThat(btcUsdtSpot.getPreMarketSwitchTime()).isEqualTo("");
    assertThat(btcUsdtSpot.isFutureSettlement()).isFalse();
    assertThat(btcUsdtSpot.getFrequency()).isEqualTo("");
    assertThat(btcUsdtSpot.getGroupId()).isEqualTo("12");
    assertThat(btcUsdtSpot.getMethod()).isEqualTo("");
    assertThat(btcUsdtSpot.getOpenType()).isEqualTo("fix_price");
    assertThat(btcUsdtSpot.getSeriesId()).isEqualTo("");
    assertThat(btcUsdtSpot.getTradeQuoteCurrencies()).containsExactly("USDT");
    assertThat(btcUsdtSpot.getUpcomingPriceCapChanges()).isEmpty();
    // Pre-existing fields remain mapped
    assertThat(btcUsdtSpot.getLeverage()).isEqualTo("10");
    assertThat(btcUsdtSpot.getState()).isEqualTo("live");
    assertThat(btcUsdtSpot.getInstIdCode()).isEqualTo("3");
  }

  @Test
  public void testMarginFieldsMapLosslessly() {
    assertThat(btcUsdtMargin.getInstrumentType()).isEqualTo("MARGIN");
    assertThat(btcUsdtMargin.getInstrumentId()).isEqualTo("BTC-USDT");
    assertThat(btcUsdtMargin.getUnderlying()).isEqualTo("BTC-USDT");
    assertThat(btcUsdtMargin.getInstrumentFamily()).isEqualTo("BTC-USDT");
    assertThat(btcUsdtMargin.getBaseCurrency()).isEqualTo("BTC");
    assertThat(btcUsdtMargin.getQuoteCurrency()).isEqualTo("USDT");
    // MARGIN reuses the SPOT instId and instIdCode
    assertThat(btcUsdtMargin.getInstIdCode()).isEqualTo("3");
    assertThat(btcUsdtMargin.getTradeQuoteCurrencies()).containsExactly("USDT");
    assertThat(btcUsdtMargin.getGroupId()).isEqualTo("12");
  }

  @Test
  public void testSwapFieldsMapLosslessly() {
    assertThat(btcUsdtSwap.getInstrumentType()).isEqualTo("SWAP");
    assertThat(btcUsdtSwap.getInstrumentId()).isEqualTo("BTC-USDT-SWAP");
    assertThat(btcUsdtSwap.getUnderlying()).isEqualTo("BTC-USDT");
    assertThat(btcUsdtSwap.getInstrumentFamily()).isEqualTo("BTC-USDT");
    // base/quote are empty for derivatives; pair comes from uly
    assertThat(btcUsdtSwap.getBaseCurrency()).isEqualTo("");
    assertThat(btcUsdtSwap.getQuoteCurrency()).isEqualTo("");
    assertThat(btcUsdtSwap.getSettleCurrency()).isEqualTo("USDT");
    assertThat(btcUsdtSwap.getContractValue()).isEqualTo("0.01");
    assertThat(btcUsdtSwap.getContractValueCurrency()).isEqualTo("BTC");
    assertThat(btcUsdtSwap.getContractMultiplier()).isEqualTo("1");
    assertThat(btcUsdtSwap.getContractType()).isEqualTo("linear");
    assertThat(btcUsdtSwap.getExpiryTime()).isEqualTo("");
    assertThat(btcUsdtSwap.getContractTradingSwitchTime()).isEqualTo("1611916860000");
    assertThat(btcUsdtSwap.getOpenType()).isEqualTo("call_auction");
    assertThat(btcUsdtSwap.getPositionLimitAmount()).isEqualTo("250000");
    assertThat(btcUsdtSwap.getPositionLimitPercent()).isEqualTo("30");
    assertThat(btcUsdtSwap.getLeverage()).isEqualTo("100");
    assertThat(btcUsdtSwap.getInstIdCode()).isEqualTo("10459");
  }

  @Test
  public void testFuturesFieldsMapLosslessly() {
    assertThat(btcUsdFutures.getInstrumentType()).isEqualTo("FUTURES");
    assertThat(btcUsdFutures.getInstrumentId()).isEqualTo("BTC-USD-260814");
    assertThat(btcUsdFutures.getUnderlying()).isEqualTo("BTC-USD");
    assertThat(btcUsdFutures.getInstrumentFamily()).isEqualTo("BTC-USD");
    assertThat(btcUsdFutures.getBaseCurrency()).isEqualTo("");
    assertThat(btcUsdFutures.getSettleCurrency()).isEqualTo("BTC");
    assertThat(btcUsdFutures.getContractValue()).isEqualTo("100");
    assertThat(btcUsdFutures.getContractValueCurrency()).isEqualTo("USD");
    assertThat(btcUsdFutures.getContractMultiplier()).isEqualTo("1");
    assertThat(btcUsdFutures.getContractType()).isEqualTo("inverse");
    assertThat(btcUsdFutures.getExpiryTime()).isEqualTo("1786694400000");
    assertThat(btcUsdFutures.getAlias()).isEqualTo("this_week");
    assertThat(btcUsdFutures.getLeverage()).isEqualTo("20");
    assertThat(btcUsdFutures.getPositionLimitAmount()).isEqualTo("200000000");
    assertThat(btcUsdFutures.getPositionLimitPercent()).isEqualTo("25");
    assertThat(btcUsdFutures.getGroupId()).isEqualTo("5");
    assertThat(btcUsdFutures.getRuleType()).isEqualTo("normal");
    assertThat(btcUsdFutures.getInstIdCode()).isEqualTo("388286");
  }

  @Test
  public void testOptionFieldsMapLosslessly() {
    assertThat(btcUsdCallOption.getInstrumentType()).isEqualTo("OPTION");
    assertThat(btcUsdCallOption.getInstrumentId()).isEqualTo("BTC-USD-260828-110000-C");
    assertThat(btcUsdCallOption.getUnderlying()).isEqualTo("BTC-USD");
    assertThat(btcUsdCallOption.getInstrumentFamily()).isEqualTo("BTC-USD");
    assertThat(btcUsdCallOption.getBaseCurrency()).isEqualTo("");
    assertThat(btcUsdCallOption.getQuoteCurrency()).isEqualTo("");
    assertThat(btcUsdCallOption.getSettleCurrency()).isEqualTo("BTC");
    assertThat(btcUsdCallOption.getContractValue()).isEqualTo("1");
    assertThat(btcUsdCallOption.getContractValueCurrency()).isEqualTo("BTC");
    assertThat(btcUsdCallOption.getContractMultiplier()).isEqualTo("0.01");
    assertThat(btcUsdCallOption.getContractType()).isEqualTo("inverse");
    assertThat(btcUsdCallOption.getOptionType()).isEqualTo("C");
    assertThat(btcUsdCallOption.getStrikePrice()).isEqualTo("110000");
    assertThat(btcUsdCallOption.getExpiryTime()).isEqualTo("1787904000000");
    assertThat(btcUsdCallOption.getLotSize()).isEqualTo("1");
    assertThat(btcUsdCallOption.getMinSize()).isEqualTo("1");
    assertThat(btcUsdCallOption.getTickSize()).isEqualTo("0.0001");
    assertThat(btcUsdCallOption.getLeverage()).isEqualTo("");
    assertThat(btcUsdCallOption.getInstIdCode()).isEqualTo("273778");
    assertThat(ethUsdPutOption.getOptionType()).isEqualTo("P");
    assertThat(ethUsdPutOption.getStrikePrice()).isEqualTo("2000");
    assertThat(ethUsdPutOption.getSettleCurrency()).isEqualTo("ETH");
  }

  @Test
  public void testUpcomingPriceCapChangeMaps() {
    assertThat(ethUsdtSpot.getUpcomingPriceCapChanges()).hasSize(1);
    assertThat(ethUsdtSpot.getUpcomingPriceCapChanges().get(0).getEffectiveTime())
        .isEqualTo("1786694400000");
    assertThat(ethUsdtSpot.getUpcomingPriceCapChanges().get(0).getNewValue()).isEqualTo("0.00001");
    assertThat(ethUsdtSpot.getUpcomingPriceCapChanges().get(0).getParameter()).isEqualTo("minSz");
  }

  @Test
  public void testAggregateInstrumentFamiliesDeduplicatesAndPreservesOrder() throws IOException {
    List<OkxInstrument> aggregated =
        OkxExchange.aggregateInstrumentFamilies(
            Arrays.asList(
                loadInstruments("instrumentsSpot.json5"),
                loadInstruments("instrumentsSwap.json5"),
                loadInstruments("instrumentsMargin.json5"),
                loadInstruments("instrumentsFutures.json5"),
                loadInstruments("instrumentsOption.json5")));

    // 10 entries across families, 8 unique instrument ids: SPOT/MARGIN share both pairs
    assertThat(aggregated).hasSize(8);
    assertThat(aggregated)
        .extracting(OkxInstrument::getInstrumentId)
        .containsExactly(
            "BTC-USDT",
            "ETH-USDT",
            "BTC-USDT-SWAP",
            "ETH-USDT-SWAP",
            "BTC-USD-260814",
            "ETH-USDT-260829",
            "BTC-USD-260828-110000-C",
            "ETH-USD-260828-2000-P");
    // first occurrence wins: the SPOT entry survives over the MARGIN duplicate
    assertThat(aggregated.get(0).getInstrumentType()).isEqualTo("SPOT");
    assertThat(aggregated.get(1).getInstrumentType()).isEqualTo("SPOT");
    // null/empty families are tolerated
    assertThat(OkxExchange.aggregateInstrumentFamilies(Arrays.asList(null, List.of()))).isEmpty();
  }

  @Test
  public void testAggregateInstrumentFamiliesPopulatesIdMapWithoutCollisions() throws IOException {
    List<OkxInstrument> aggregated =
        OkxExchange.aggregateInstrumentFamilies(
            Arrays.asList(
                loadInstruments("instrumentsSpot.json5"),
                loadInstruments("instrumentsSwap.json5"),
                loadInstruments("instrumentsMargin.json5"),
                loadInstruments("instrumentsFutures.json5"),
                loadInstruments("instrumentsOption.json5")));

    Map<Instrument, Long> idMap = OkxAdapters.instrumentToInstrumentIdMap;
    // 8 parsed keys plus the exact-expiry option aliases registered for the native adapter
    assertThat(idMap).hasSize(10);
    assertThat(idMap)
        .containsEntry(new CurrencyPair("BTC", "USDT"), 3L)
        .containsEntry(new CurrencyPair("ETH", "USDT"), 12L)
        .containsEntry(new FuturesContract(new CurrencyPair("BTC", "USDT"), "SWAP"), 10459L)
        .containsEntry(new FuturesContract(new CurrencyPair("ETH", "USDT"), "SWAP"), 12345L)
        .containsEntry(new FuturesContract(new CurrencyPair("BTC", "USD"), "260814"), 388286L)
        .containsEntry(new FuturesContract(new CurrencyPair("ETH", "USDT"), "260829"), 388287L)
        // options keys are built with the same TZ-sensitive parsing as the map population, so
        // derive the expected key the same way instead of constructing a Date here
        .containsEntry(OkxAdapters.adaptOkxInstrumentId("BTC-USD-260828-110000-C"), 273778L)
        .containsEntry(OkxAdapters.adaptOkxInstrumentId("ETH-USD-260828-2000-P"), 273779L)
        // the exact-expiry native contracts resolve the same codes
        .containsEntry(OkxAdapters.adaptOkxInstrument(aggregated.get(6)), 273778L)
        .containsEntry(OkxAdapters.adaptOkxInstrument(aggregated.get(7)), 273779L);
  }

  @Test
  public void testAdaptOkxInstrumentNativeFieldsMatchIdParsing() throws IOException {
    for (OkxInstrument instrument :
        OkxExchange.aggregateInstrumentFamilies(
            Arrays.asList(
                loadInstruments("instrumentsSpot.json5"),
                loadInstruments("instrumentsSwap.json5"),
                loadInstruments("instrumentsMargin.json5"),
                loadInstruments("instrumentsFutures.json5"),
                loadInstruments("instrumentsOption.json5")))) {
      Instrument nativeInstrument = OkxAdapters.adaptOkxInstrument(instrument);
      Instrument parsedInstrument = OkxAdapters.adaptOkxInstrumentId(instrument.getInstrumentId());
      if (instrument.getInstrumentType().equals("OPTION")) {
        // expiry Date differs by default-TZ parsing vs exact expTime; compare the stable parts
        assertThat(nativeInstrument).isInstanceOf(OptionsContract.class);
        OptionsContract nativeContract = (OptionsContract) nativeInstrument;
        assertThat(nativeContract.getCurrencyPair())
            .isEqualTo(((OptionsContract) parsedInstrument).getCurrencyPair());
        assertThat(nativeContract.getStrike())
            .isEqualTo(((OptionsContract) parsedInstrument).getStrike());
        assertThat(nativeContract.getType())
            .isEqualTo(((OptionsContract) parsedInstrument).getType());
      } else {
        assertThat(nativeInstrument).isEqualTo(parsedInstrument);
      }
    }
  }

  @Test
  public void testAdaptOkxInstrumentOptionUsesNativeFields() {
    OptionsContract option = (OptionsContract) OkxAdapters.adaptOkxInstrument(btcUsdCallOption);
    assertThat(option.getCurrencyPair()).isEqualTo(new CurrencyPair("BTC", "USD"));
    assertThat(option.getExpireDate()).isEqualTo(new Date(1787904000000L));
    assertThat(option.getStrike()).isEqualByComparingTo("110000");
    assertThat(option.getType()).isEqualTo(OptionsContract.OptionType.CALL);
    assertThat(((OptionsContract) OkxAdapters.adaptOkxInstrument(ethUsdPutOption)).getType())
        .isEqualTo(OptionsContract.OptionType.PUT);
  }

  @Test
  public void testAdaptToExchangeMetaDataCoversAllFamilies() throws IOException {
    List<OkxInstrument> instruments =
        OkxExchange.aggregateInstrumentFamilies(
            Arrays.asList(
                loadInstruments("instrumentsSpot.json5"),
                loadInstruments("instrumentsSwap.json5"),
                loadInstruments("instrumentsMargin.json5"),
                loadInstruments("instrumentsFutures.json5"),
                loadInstruments("instrumentsOption.json5")));

    Map<Instrument, InstrumentMetaData> metadata =
        OkxAdapters.adaptToExchangeMetaData(instruments, null).getInstruments();
    // 8 parsed keys plus the exact-expiry option aliases for the native adapter
    assertThat(metadata).hasSize(10);

    InstrumentMetaData spot = metadata.get(new CurrencyPair("BTC", "USDT"));
    assertThat(spot).isNotNull();
    assertThat(spot.getMinimumAmount()).isEqualByComparingTo("0.00001");
    assertThat(spot.getContractValue()).isNull();

    InstrumentMetaData swap =
        metadata.get(new FuturesContract(new CurrencyPair("BTC", "USDT"), "SWAP"));
    assertThat(swap).isNotNull();
    assertThat(swap.getContractValue()).isEqualByComparingTo("0.01");
    assertThat(swap.getMinimumAmount()).isEqualByComparingTo("0.0001");

    // USD-margined futures enter the metadata and carry their contract value
    InstrumentMetaData btcUsdFuture =
        metadata.get(new FuturesContract(new CurrencyPair("BTC", "USD"), "260814"));
    assertThat(btcUsdFuture).isNotNull();
    assertThat(btcUsdFuture.getContractValue()).isEqualByComparingTo("100");
    assertThat(btcUsdFuture.getMinimumAmount()).isEqualByComparingTo("10");
    assertThat(btcUsdFuture.getAmountStepSize()).isEqualByComparingTo("10");

    InstrumentMetaData ethUsdtFuture =
        metadata.get(new FuturesContract(new CurrencyPair("ETH", "USDT"), "260829"));
    assertThat(ethUsdtFuture).isNotNull();
    assertThat(ethUsdtFuture.getContractValue()).isEqualByComparingTo("1");
    assertThat(ethUsdtFuture.getMinimumAmount()).isEqualByComparingTo("0.01");

    InstrumentMetaData option =
        metadata.get(OkxAdapters.adaptOkxInstrumentId("BTC-USD-260828-110000-C"));
    assertThat(option).isNotNull();
    // Option sizes convert by ctMult (0.01): minSz of 1 contract = 0.01 BTC
    assertThat(option.getContractValue()).isEqualByComparingTo("0.01");
    assertThat(option.getMinimumAmount()).isEqualByComparingTo("0.01");

    // Callers using the native adapter (exact expTime) resolve the same metadata entry
    Instrument nativeOption =
        OkxAdapters.adaptOkxInstrument(
            instruments.stream()
                .filter(i -> "BTC-USD-260828-110000-C".equals(i.getInstrumentId()))
                .findFirst()
                .orElseThrow());
    assertThat(metadata.get(nativeOption)).isSameAs(option);
  }

  @Test
  public void testAdaptToExchangeMetaDataRetainsInversePerpetualSwaps() throws IOException {
    List<OkxInstrument> instruments = loadInstruments("instrumentsSwapUsd.json5");

    Map<Instrument, InstrumentMetaData> metadata =
        OkxAdapters.adaptToExchangeMetaData(instruments, null).getInstruments();

    InstrumentMetaData btcUsdSwap =
        metadata.get(new FuturesContract(new CurrencyPair("BTC", "USD"), "SWAP"));
    assertThat(btcUsdSwap).isNotNull();
    // Inverse swap minimums stay notional (sz x ctVal) at metadata time; price-aware call sites
    // divide by the price, so the contract value must be present for them to dereference.
    assertThat(btcUsdSwap.getContractValue()).isEqualByComparingTo("100");
    assertThat(btcUsdSwap.getMinimumAmount()).isEqualByComparingTo("100");
    assertThat(btcUsdSwap.getAmountStepSize()).isEqualByComparingTo("100");
  }

  @Test
  public void testRemoteInitUsesOnlyPublicEndpointsWithoutCredentials() throws Exception {
    OkxExchange exchange = new OkxExchange();
    ExchangeSpecification specification = new ExchangeSpecification(OkxExchange.class);
    specification.setShouldLoadRemoteMetaData(false);
    exchange.applySpecification(specification);
    assertThat(exchange.getExchangeSpecification().getApiKey()).isNull();

    OkxMarketDataService raw = mock(OkxMarketDataService.class);
    when(raw.getOkxInstruments("SPOT", null, null))
        .thenReturn(new OkxResponse<>(null, "0", "", loadInstruments("instrumentsSpot.json5")));
    when(raw.getOkxInstruments("SWAP", null, null))
        .thenReturn(new OkxResponse<>(null, "0", "", loadInstruments("instrumentsSwap.json5")));
    when(raw.getOkxInstruments("MARGIN", null, null))
        .thenReturn(new OkxResponse<>(null, "0", "", loadInstruments("instrumentsMargin.json5")));
    when(raw.getOkxInstruments("FUTURES", null, null))
        .thenReturn(new OkxResponse<>(null, "0", "", loadInstruments("instrumentsFutures.json5")));
    List<String> underlyings =
        MAPPER
            .readValue(
                OkxInstrumentMetadataTest.class.getResourceAsStream("/underlyingsOption.json5"),
                new TypeReference<OkxResponse<List<List<String>>>>() {})
            .getData()
            .get(0);
    when(raw.getOkxUnderlyings(OkxInstType.OPTION))
        .thenReturn(new OkxResponse<>(null, "0", "", underlyings));
    when(raw.getOkxInstruments("OPTION", "BTC-USD", null))
        .thenReturn(new OkxResponse<>(null, "0", "", loadInstruments("instrumentsOption.json5")));
    when(raw.getOkxInstruments("OPTION", "ETH-USD", null))
        .thenReturn(new OkxResponse<>(null, "0", "", loadInstruments("instrumentsOption.json5")));

    Field marketDataService =
        org.knowm.xchange.BaseExchange.class.getDeclaredField("marketDataService");
    marketDataService.setAccessible(true);
    marketDataService.set(exchange, raw);

    exchange.remoteInit();

    verify(raw).getOkxInstruments("SPOT", null, null);
    verify(raw).getOkxInstruments("SWAP", null, null);
    verify(raw).getOkxInstruments("MARGIN", null, null);
    verify(raw).getOkxInstruments("FUTURES", null, null);
    verify(raw).getOkxUnderlyings(OkxInstType.OPTION);
    verify(raw).getOkxInstruments("OPTION", "BTC-USD", null);
    verify(raw).getOkxInstruments("OPTION", "ETH-USD", null);
    // the private currencies endpoint must not be touched without credentials
    verify(raw, never()).getOkxCurrencies();

    assertThat(exchange.getExchangeMetaData().getInstruments())
        .containsKey(new CurrencyPair("BTC", "USDT"));
    assertThat(exchange.getExchangeMetaData().getInstruments())
        .containsKey(OkxAdapters.adaptOkxInstrumentId("BTC-USD-260828-110000-C"));
  }

  @Test
  public void testRemoteInitPropagatesFailedUnderlyingsLookup() throws Exception {
    // A non-success underlying response must not be interpreted as "no options": remoteInit fails
    // instead of silently omitting the whole OPTION family from metadata.
    OkxExchange exchange = new OkxExchange();
    ExchangeSpecification specification = new ExchangeSpecification(OkxExchange.class);
    specification.setShouldLoadRemoteMetaData(false);
    exchange.applySpecification(specification);

    OkxMarketDataService raw = mock(OkxMarketDataService.class);
    when(raw.getOkxInstruments("SPOT", null, null))
        .thenReturn(new OkxResponse<>(null, "0", "", loadInstruments("instrumentsSpot.json5")));
    when(raw.getOkxInstruments("SWAP", null, null))
        .thenReturn(new OkxResponse<>(null, "0", "", loadInstruments("instrumentsSwap.json5")));
    when(raw.getOkxInstruments("MARGIN", null, null))
        .thenReturn(new OkxResponse<>(null, "0", "", loadInstruments("instrumentsMargin.json5")));
    when(raw.getOkxInstruments("FUTURES", null, null))
        .thenReturn(new OkxResponse<>(null, "0", "", loadInstruments("instrumentsFutures.json5")));
    when(raw.getOkxUnderlyings(OkxInstType.OPTION))
        .thenReturn(new OkxResponse<>(null, "51000", "Something went wrong", null));

    Field marketDataService =
        org.knowm.xchange.BaseExchange.class.getDeclaredField("marketDataService");
    marketDataService.setAccessible(true);
    marketDataService.set(exchange, raw);

    assertThatThrownBy(exchange::remoteInit)
        .isInstanceOf(OkxException.class)
        .hasMessageContaining("Something went wrong");
  }

  @Test
  public void testRemoteInitPropagatesFailedInstrumentsLookup() throws Exception {
    // A non-success instruments response must not be interpreted as an empty family: remoteInit
    // fails instead of silently building incomplete metadata and instrument-code mappings.
    OkxExchange exchange = new OkxExchange();
    ExchangeSpecification specification = new ExchangeSpecification(OkxExchange.class);
    specification.setShouldLoadRemoteMetaData(false);
    exchange.applySpecification(specification);

    OkxMarketDataService raw = mock(OkxMarketDataService.class);
    when(raw.getOkxInstruments("SPOT", null, null))
        .thenReturn(new OkxResponse<>(null, "51000", "Something went wrong", null));
    when(raw.getOkxInstruments("SWAP", null, null))
        .thenReturn(new OkxResponse<>(null, "0", "", loadInstruments("instrumentsSwap.json5")));
    when(raw.getOkxInstruments("MARGIN", null, null))
        .thenReturn(new OkxResponse<>(null, "0", "", loadInstruments("instrumentsMargin.json5")));
    when(raw.getOkxInstruments("FUTURES", null, null))
        .thenReturn(new OkxResponse<>(null, "0", "", loadInstruments("instrumentsFutures.json5")));
    when(raw.getOkxUnderlyings(OkxInstType.OPTION))
        .thenReturn(new OkxResponse<>(null, "0", "", List.of()));

    Field marketDataService =
        org.knowm.xchange.BaseExchange.class.getDeclaredField("marketDataService");
    marketDataService.setAccessible(true);
    marketDataService.set(exchange, raw);

    assertThatThrownBy(exchange::remoteInit)
        .isInstanceOf(OkxException.class)
        .hasMessageContaining("Something went wrong");
  }

  @Test
  public void testRemoteInitPropagatesFailedOptionInstrumentsLookup() throws Exception {
    // The per-underlying OPTION instruments fetch is validated like every other family: a failed
    // response propagates instead of being treated as an empty option family.
    OkxExchange exchange = new OkxExchange();
    ExchangeSpecification specification = new ExchangeSpecification(OkxExchange.class);
    specification.setShouldLoadRemoteMetaData(false);
    exchange.applySpecification(specification);

    OkxMarketDataService raw = mock(OkxMarketDataService.class);
    when(raw.getOkxInstruments("SPOT", null, null))
        .thenReturn(new OkxResponse<>(null, "0", "", loadInstruments("instrumentsSpot.json5")));
    when(raw.getOkxInstruments("SWAP", null, null))
        .thenReturn(new OkxResponse<>(null, "0", "", loadInstruments("instrumentsSwap.json5")));
    when(raw.getOkxInstruments("MARGIN", null, null))
        .thenReturn(new OkxResponse<>(null, "0", "", loadInstruments("instrumentsMargin.json5")));
    when(raw.getOkxInstruments("FUTURES", null, null))
        .thenReturn(new OkxResponse<>(null, "0", "", loadInstruments("instrumentsFutures.json5")));
    when(raw.getOkxUnderlyings(OkxInstType.OPTION))
        .thenReturn(new OkxResponse<>(null, "0", "", List.of("BTC-USD")));
    when(raw.getOkxInstruments("OPTION", "BTC-USD", null))
        .thenReturn(new OkxResponse<>(null, "51000", "Something went wrong", null));

    Field marketDataService =
        org.knowm.xchange.BaseExchange.class.getDeclaredField("marketDataService");
    marketDataService.setAccessible(true);
    marketDataService.set(exchange, raw);

    assertThatThrownBy(exchange::remoteInit)
        .isInstanceOf(OkxException.class)
        .hasMessageContaining("Something went wrong");
  }

  @Test
  public void canonicalExchangeRetainsOfflineMetadataWhenRemoteInitIsDisabled() {
    ExchangeSpecification spec = new OkxExchange().getDefaultExchangeSpecification();
    spec.setShouldLoadRemoteMetaData(false);

    OkxExchange exchange = new OkxExchange();
    exchange.applySpecification(spec);

    assertThat(exchange.getExchangeMetaData()).isNotNull();
  }

  private OkxExchange credentialedExchange() {
    OkxExchange exchange = new OkxExchange();
    ExchangeSpecification specification = new ExchangeSpecification(OkxExchange.class);
    specification.setShouldLoadRemoteMetaData(false);
    specification.setApiKey("api-key");
    specification.setSecretKey("secret-key");
    specification.setExchangeSpecificParametersItem("passphrase", "passphrase");
    exchange.applySpecification(specification);
    return exchange;
  }

  private OkxMarketDataService stubInstrumentFamilies(OkxMarketDataService raw) throws IOException {
    when(raw.getOkxInstruments("SPOT", null, null))
        .thenReturn(new OkxResponse<>(null, "0", "", loadInstruments("instrumentsSpot.json5")));
    when(raw.getOkxInstruments("SWAP", null, null))
        .thenReturn(new OkxResponse<>(null, "0", "", loadInstruments("instrumentsSwap.json5")));
    when(raw.getOkxInstruments("MARGIN", null, null))
        .thenReturn(new OkxResponse<>(null, "0", "", loadInstruments("instrumentsMargin.json5")));
    when(raw.getOkxInstruments("FUTURES", null, null))
        .thenReturn(new OkxResponse<>(null, "0", "", loadInstruments("instrumentsFutures.json5")));
    when(raw.getOkxUnderlyings(OkxInstType.OPTION))
        .thenReturn(new OkxResponse<>(null, "0", "", List.of()));
    return raw;
  }

  private void setMarketDataService(OkxExchange exchange, OkxMarketDataService raw)
      throws Exception {
    Field marketDataService =
        org.knowm.xchange.BaseExchange.class.getDeclaredField("marketDataService");
    marketDataService.setAccessible(true);
    marketDataService.set(exchange, raw);
  }

  private void setAccountService(OkxExchange exchange, OkxAccountService raw) throws Exception {
    Field accountService = org.knowm.xchange.BaseExchange.class.getDeclaredField("accountService");
    accountService.setAccessible(true);
    accountService.set(exchange, raw);
  }

  @Test
  public void testRemoteInitPropagatesFailedCurrenciesLookup() throws Exception {
    // A failed authenticated currencies response must not be silently treated as absent currency
    // metadata: remoteInit fails instead of publishing metadata without currencies.
    OkxExchange exchange = credentialedExchange();
    OkxMarketDataService raw = stubInstrumentFamilies(mock(OkxMarketDataService.class));
    when(raw.getOkxCurrencies())
        .thenReturn(new OkxResponse<>(null, "50111", "Invalid OK Access Key", null));
    setMarketDataService(exchange, raw);

    assertThatThrownBy(exchange::remoteInit)
        .isInstanceOf(OkxException.class)
        .hasMessageContaining("Invalid OK Access Key");
  }

  @Test
  public void testRemoteInitPropagatesFailedAccountConfigurationLookup() throws Exception {
    // A failed authenticated account-configuration response must not be dereferenced as null
    // data: remoteInit fails with the provider error instead of an unrelated NPE.
    OkxExchange exchange = credentialedExchange();
    OkxMarketDataService raw = stubInstrumentFamilies(mock(OkxMarketDataService.class));
    when(raw.getOkxCurrencies()).thenReturn(new OkxResponse<>(null, "0", "", List.of()));
    setMarketDataService(exchange, raw);

    OkxAccountService accountRaw = mock(OkxAccountService.class);
    when(accountRaw.getOkxAccountConfiguration())
        .thenReturn(new OkxResponse<>(null, "50111", "Invalid OK Access Key", null));
    setAccountService(exchange, accountRaw);

    assertThatThrownBy(exchange::remoteInit)
        .isInstanceOf(OkxException.class)
        .hasMessageContaining("Invalid OK Access Key");
  }

  @Test
  public void testRemoteInitPropagatesEmptyAccountConfigurationData() throws Exception {
    // A success envelope with no account-configuration entries cannot yield an account level:
    // remoteInit fails instead of throwing an unrelated IndexOutOfBoundsException.
    OkxExchange exchange = credentialedExchange();
    OkxMarketDataService raw = stubInstrumentFamilies(mock(OkxMarketDataService.class));
    when(raw.getOkxCurrencies()).thenReturn(new OkxResponse<>(null, "0", "", List.of()));
    setMarketDataService(exchange, raw);

    OkxAccountService accountRaw = mock(OkxAccountService.class);
    when(accountRaw.getOkxAccountConfiguration())
        .thenReturn(new OkxResponse<>(null, "0", "", List.of()));
    setAccountService(exchange, accountRaw);

    assertThatThrownBy(exchange::remoteInit)
        .isInstanceOf(OkxException.class)
        .hasMessageContaining("Empty data from OKX account configuration endpoint");
  }

  @Test
  public void testRemoteInitAppliesAccountLevelFromCredentialedConfiguration() throws Exception {
    // The credentialed branch populates accountLevel from the authenticated configuration and
    // still publishes instruments metadata.
    OkxExchange exchange = credentialedExchange();
    OkxMarketDataService raw = stubInstrumentFamilies(mock(OkxMarketDataService.class));
    when(raw.getOkxCurrencies()).thenReturn(new OkxResponse<>(null, "0", "", List.of()));
    setMarketDataService(exchange, raw);

    OkxAccountService accountRaw = mock(OkxAccountService.class);
    when(accountRaw.getOkxAccountConfiguration())
        .thenReturn(
            new OkxResponse<>(
                null,
                "0",
                "",
                Collections.singletonList(
                    MAPPER.readValue("{\"acctLv\":\"2\"}", OkxAccountConfig.class))));
    setAccountService(exchange, accountRaw);

    exchange.remoteInit();

    assertThat(exchange.accountLevel).isEqualTo("2");
    assertThat(exchange.getExchangeMetaData().getInstruments())
        .containsKey(new CurrencyPair("BTC", "USDT"));
  }
}
