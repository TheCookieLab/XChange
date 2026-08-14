package org.knowm.xchange.okx.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.knowm.xchange.BaseExchange;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.client.ResilienceRegistries;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.derivative.FuturesContract;
import org.knowm.xchange.derivative.OptionsContract;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.dto.account.Fee;
import org.knowm.xchange.dto.meta.ExchangeMetaData;
import org.knowm.xchange.dto.meta.InstrumentMetaData;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.okx.OkxExchange;
import org.knowm.xchange.okx.dto.OkxException;
import org.knowm.xchange.okx.dto.OkxResponse;
import org.knowm.xchange.okx.dto.account.OkxAccountPositionRisk;
import org.knowm.xchange.okx.dto.account.OkxAssetBalance;
import org.knowm.xchange.okx.dto.account.OkxTradeFee;
import org.knowm.xchange.okx.dto.account.OkxWalletBalance;

/**
 * Offline tests for the dynamic trading-fee map: every fee category present in the exchange
 * metadata (spot, perpetual swaps, dated futures, options) is fetched with the endpoint parameters
 * its instrument family requires, and each instrument gets a fee.
 */
public class OkxAccountServiceTest {

  private final ObjectMapper mapper = new ObjectMapper();

  private StubAccountService service;

  @Before
  public void setUp() throws Exception {
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    OkxExchange exchange = new OkxExchange();
    ExchangeSpecification specification = new ExchangeSpecification(OkxExchange.class);
    specification.setShouldLoadRemoteMetaData(false);
    exchange.applySpecification(specification);
    Map<Instrument, InstrumentMetaData> instruments = new LinkedHashMap<>();
    instruments.put(new CurrencyPair("BTC", "USDT"), InstrumentMetaData.builder().build());
    instruments.put(
        new FuturesContract(new CurrencyPair("BTC", "USDT"), "SWAP"),
        InstrumentMetaData.builder().build());
    instruments.put(
        new FuturesContract(new CurrencyPair("BTC", "USD"), "260814"),
        InstrumentMetaData.builder().build());
    instruments.put(
        new OptionsContract("BTC/USD/260828/110000/C"), InstrumentMetaData.builder().build());
    Field exchangeMetaData = BaseExchange.class.getDeclaredField("exchangeMetaData");
    exchangeMetaData.setAccessible(true);
    exchangeMetaData.set(
        exchange, new ExchangeMetaData(instruments, new HashMap<>(), null, null, false));

    service = new StubAccountService(exchange);
  }

  @Test
  public void testDefaultBranchFetchesEveryInstrumentFamily() throws Exception {
    Map<Instrument, Fee> fees = service.getDynamicTradingFeesByInstrument();

    assertThat(service.requested)
        .containsExactly(
            "SPOT|null|null|null",
            "SWAP|null|null|null",
            "FUTURES|null|BTC-USD|null",
            "OPTION|null|null|BTC-USD");
    // spot pair, perpetual swap, dated future and option each receive an adapted fee
    assertThat(fees.keySet())
        .containsExactlyInAnyOrder(
            new CurrencyPair("BTC", "USDT"),
            new FuturesContract(new CurrencyPair("BTC", "USDT"), "SWAP"),
            new FuturesContract(new CurrencyPair("BTC", "USD"), "260814"),
            new OptionsContract("BTC/USD/260828/110000/C"));
    assertThat(fees.get(new FuturesContract(new CurrencyPair("BTC", "USD"), "260814")))
        .isEqualTo(new Fee(new java.math.BigDecimal("0.0002"), new java.math.BigDecimal("0.0005")));
    assertThat(fees.get(new OptionsContract("BTC/USD/260828/110000/C")))
        .isEqualTo(new Fee(new java.math.BigDecimal("0.0002"), new java.math.BigDecimal("0.0005")));
  }

  @Test
  public void testFuturesCategoryFetchesOnlyDatedFutures() throws Exception {
    Map<Instrument, Fee> fees = service.getDynamicTradingFeesByInstrument("FUTURES");

    assertThat(service.requested).containsExactly("FUTURES|null|BTC-USD|null");
    assertThat(fees.keySet())
        .containsExactly(new FuturesContract(new CurrencyPair("BTC", "USD"), "260814"));
  }

  @Test
  public void testOptionCategoryFetchesOnlyOptions() throws Exception {
    Map<Instrument, Fee> fees = service.getDynamicTradingFeesByInstrument("OPTION");

    assertThat(service.requested).containsExactly("OPTION|null|null|BTC-USD");
    assertThat(fees.keySet()).containsExactly(new OptionsContract("BTC/USD/260828/110000/C"));
  }

  /** Subclass that stubs the trade-fee HTTP seam so the map assembly runs offline. */
  private class StubAccountService extends OkxAccountService {

    final List<String> requested = new ArrayList<>();

    StubAccountService(OkxExchange exchange) {
      super(exchange, new ResilienceRegistries());
    }

    @Override
    public OkxResponse<List<OkxTradeFee>> getTradeFee(
        String instrumentType, String instrumentId, String underlying, String instFamily)
        throws IOException {
      requested.add(instrumentType + "|" + instrumentId + "|" + underlying + "|" + instFamily);
      OkxTradeFee okxTradeFee =
          mapper.readValue(
              "{\"instType\":\""
                  + instrumentType
                  + "\",\"level\":\"Lv1\","
                  + "\"maker\":\"-0.0002\",\"taker\":\"-0.0005\","
                  + "\"makerU\":\"-0.0002\",\"takerU\":\"-0.0005\","
                  + "\"makerUSDC\":\"-0.0002\",\"takerUSDC\":\"-0.0005\"}",
              OkxTradeFee.class);
      return new OkxResponse<>(null, "0", "", List.of(okxTradeFee));
    }
  }

  /** Subclass that stubs the three account envelopes so {@code getAccountInfo} runs offline. */
  private class EnvelopeAccountService extends OkxAccountService {

    OkxResponse<List<OkxWalletBalance>> walletBalances;
    OkxResponse<List<OkxAssetBalance>> assetBalances;
    OkxResponse<List<OkxAccountPositionRisk>> positionRisk;

    EnvelopeAccountService(OkxExchange exchange) {
      super(exchange, new ResilienceRegistries());
    }

    @Override
    public OkxResponse<List<OkxWalletBalance>> getWalletBalances(List<Currency> currencies) {
      return walletBalances;
    }

    @Override
    public OkxResponse<List<OkxAssetBalance>> getAssetBalances(List<Currency> currencies) {
      return assetBalances;
    }

    @Override
    public OkxResponse<List<OkxAccountPositionRisk>> getAccountPositionRisk() {
      return positionRisk;
    }
  }

  @Test
  public void getAccountInfoThrowsOnWalletBalanceBusinessFailure() {
    EnvelopeAccountService service = new EnvelopeAccountService(exchange());
    service.walletBalances = new OkxResponse<>(null, "40001", "Invalid OK-ACCESS-KEY", null);
    service.assetBalances = new OkxResponse<>(null, "0", "", List.of());
    service.positionRisk = new OkxResponse<>(null, "0", "", List.of());

    assertThatThrownBy(service::getAccountInfo)
        .isInstanceOf(OkxException.class)
        .hasMessageContaining("Invalid OK-ACCESS-KEY");
  }

  @Test
  public void getAccountInfoThrowsOnAssetBalanceBusinessFailure() {
    EnvelopeAccountService service = new EnvelopeAccountService(exchange());
    service.walletBalances = new OkxResponse<>(null, "0", "", List.of());
    service.assetBalances = new OkxResponse<>(null, "50001", "Asset error", null);
    service.positionRisk = new OkxResponse<>(null, "0", "", List.of());

    assertThatThrownBy(service::getAccountInfo)
        .isInstanceOf(OkxException.class)
        .hasMessageContaining("Asset error");
  }

  @Test
  public void getAccountInfoThrowsOnPositionRiskBusinessFailure() {
    EnvelopeAccountService service = new EnvelopeAccountService(exchange());
    service.walletBalances = new OkxResponse<>(null, "0", "", List.of());
    service.assetBalances = new OkxResponse<>(null, "0", "", List.of());
    service.positionRisk = new OkxResponse<>(null, "51001", "Position risk error", null);

    assertThatThrownBy(service::getAccountInfo)
        .isInstanceOf(OkxException.class)
        .hasMessageContaining("Position risk error");
  }

  @Test
  public void getAccountInfoThrowsOnMissingPositionRiskPayload() {
    EnvelopeAccountService service = new EnvelopeAccountService(exchange());
    service.walletBalances = new OkxResponse<>(null, "0", "", List.of());
    service.assetBalances = new OkxResponse<>(null, "0", "", List.of());
    service.positionRisk = new OkxResponse<>(null, "0", "", null);

    assertThatThrownBy(service::getAccountInfo)
        .isInstanceOf(OkxException.class)
        .hasMessageContaining("Missing position-risk data");
  }

  @Test
  public void getAccountInfoSucceedsWithEmptySuccessEnvelopes() throws Exception {
    EnvelopeAccountService service = new EnvelopeAccountService(exchange());
    service.walletBalances = new OkxResponse<>(null, "0", "", List.of());
    service.assetBalances = new OkxResponse<>(null, "0", "", List.of());
    service.positionRisk =
        new OkxResponse<>(
            null,
            "0",
            "",
            List.of(
                new OkxAccountPositionRisk(
                    new java.math.BigDecimal("1000"), List.of(), List.of(), new Date())));

    AccountInfo accountInfo = service.getAccountInfo();

    assertThat(accountInfo.getWallets()).hasSize(3);
  }

  /**
   * Subclass that stubs a single trade-fee envelope so {@code getDynamicTradingFeesByInstrument}
   * runs offline.
   */
  private class FeeEnvelopeAccountService extends OkxAccountService {

    OkxResponse<List<OkxTradeFee>> feeResponse;

    FeeEnvelopeAccountService(OkxExchange exchange) {
      super(exchange, new ResilienceRegistries());
    }

    @Override
    public OkxResponse<List<OkxTradeFee>> getTradeFee(
        String instrumentType, String instrumentId, String underlying, String instFamily) {
      return feeResponse;
    }
  }

  @Test
  public void getTradeFeesThrowsOnFeeBusinessFailure() {
    FeeEnvelopeAccountService service = new FeeEnvelopeAccountService(exchange());
    service.feeResponse = new OkxResponse<>(null, "40001", "Invalid OK-ACCESS-KEY", null);

    assertThatThrownBy(service::getDynamicTradingFeesByInstrument)
        .isInstanceOf(OkxException.class)
        .hasMessageContaining("Invalid OK-ACCESS-KEY");
  }

  @Test
  public void getTradeFeesThrowsOnEmptyFeePayload() {
    FeeEnvelopeAccountService service = new FeeEnvelopeAccountService(exchange());
    service.feeResponse = new OkxResponse<>(null, "0", "", List.of());

    assertThatThrownBy(service::getDynamicTradingFeesByInstrument)
        .isInstanceOf(OkxException.class)
        .hasMessageContaining("Empty data in OKX response");
  }

  private OkxExchange exchange() {
    OkxExchange exchange = new OkxExchange();
    ExchangeSpecification specification = new ExchangeSpecification(OkxExchange.class);
    specification.setShouldLoadRemoteMetaData(false);
    exchange.applySpecification(specification);
    return exchange;
  }
}
