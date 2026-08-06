package org.knowm.xchange.prediction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.Test;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.meta.ExchangeMetaData;
import org.knowm.xchange.dto.meta.InstrumentMetaData;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.utils.ObjectMapperHelper;

public class PredictionMarketContractTest {

  private static final String KALSHI_WIRE =
      "PRED/kalshi/KXBTC-25DEC31/KXBTC-25DEC31-T90000/YES/USD";
  private static final String POLYMARKET_WIRE =
      "PRED/polymarket/0xdd22472e/713210456792522125/USD";

  @Test
  public void testParseWithEventId() {
    PredictionMarketContract contract = new PredictionMarketContract(KALSHI_WIRE);

    assertThat(contract.getProvider()).isEqualTo("kalshi");
    assertThat(contract.getEventId()).isEqualTo("KXBTC-25DEC31");
    assertThat(contract.getMarketId()).isEqualTo("KXBTC-25DEC31-T90000");
    assertThat(contract.getOutcomeId()).isEqualTo("YES");
    assertThat(contract.getQuoteCurrency()).isEqualTo(Currency.USD);
    assertThat(contract.getCounter()).isEqualTo(Currency.USD);
    assertThat(contract.getBase()).isNull();
    assertThat(contract.toString()).isEqualTo(KALSHI_WIRE);
  }

  @Test
  public void testParseWithoutEventId() {
    PredictionMarketContract contract = new PredictionMarketContract(POLYMARKET_WIRE);

    assertThat(contract.getProvider()).isEqualTo("polymarket");
    assertThat(contract.getEventId()).isNull();
    assertThat(contract.getMarketId()).isEqualTo("0xdd22472e");
    assertThat(contract.getOutcomeId()).isEqualTo("713210456792522125");
    assertThat(contract.getQuoteCurrency()).isEqualTo(Currency.USD);
    assertThat(contract.toString()).isEqualTo(POLYMARKET_WIRE);
  }

  @Test
  public void testProgrammaticConstruction() {
    PredictionMarketContract withoutEvent =
        new PredictionMarketContract("polymarket", "0xdd22472e", "713210456792522125", Currency.USD);
    assertThat(withoutEvent.getEventId()).isNull();
    assertThat(withoutEvent.toString()).isEqualTo(POLYMARKET_WIRE);

    PredictionMarketContract withEvent =
        new PredictionMarketContract(
            "kalshi", "KXBTC-25DEC31", "KXBTC-25DEC31-T90000", "YES", Currency.USD);
    assertThat(withEvent.toString()).isEqualTo(KALSHI_WIRE);
  }

  @Test
  public void testSerializeDeserialize() throws IOException {
    PredictionMarketContract withEvent = new PredictionMarketContract(KALSHI_WIRE);
    assertThat(ObjectMapperHelper.viaJSON(withEvent)).isEqualTo(withEvent);

    PredictionMarketContract withoutEvent = new PredictionMarketContract(POLYMARKET_WIRE);
    assertThat(ObjectMapperHelper.viaJSON(withoutEvent)).isEqualTo(withoutEvent);
  }

  @Test
  public void testEquality() {
    PredictionMarketContract contract = new PredictionMarketContract(KALSHI_WIRE);

    assertThat(contract).isEqualTo(new PredictionMarketContract(KALSHI_WIRE));
    assertThat(contract.hashCode())
        .isEqualTo(new PredictionMarketContract(KALSHI_WIRE).hashCode());

    // Different outcome is a different instrument.
    assertThat(contract)
        .isNotEqualTo(new PredictionMarketContract("PRED/kalshi/KXBTC-25DEC31/KXBTC-25DEC31-T90000/NO/USD"));
    // Presence of an event id is part of identity.
    assertThat(contract)
        .isNotEqualTo(
            new PredictionMarketContract("PRED/kalshi/KXBTC-25DEC31-T90000/YES/USD"));
    // Different provider namespaces never collide.
    assertThat(new PredictionMarketContract(POLYMARKET_WIRE))
        .isNotEqualTo(
            new PredictionMarketContract("PRED/kalshi/0xdd22472e/713210456792522125/USD"));
  }

  @Test
  public void testCompareToOrdering() {
    PredictionMarketContract kalshiYes = new PredictionMarketContract(KALSHI_WIRE);
    PredictionMarketContract kalshiNo =
        new PredictionMarketContract("PRED/kalshi/KXBTC-25DEC31/KXBTC-25DEC31-T90000/NO/USD");
    PredictionMarketContract polymarket = new PredictionMarketContract(POLYMARKET_WIRE);

    List<PredictionMarketContract> sorted =
        Arrays.asList(polymarket, kalshiYes, kalshiNo).stream().sorted().toList();

    assertThat(sorted).containsExactly(kalshiNo, kalshiYes, polymarket);
  }

  @Test
  public void testIsWireString() {
    assertThat(PredictionMarketContract.isWireString(KALSHI_WIRE)).isTrue();
    assertThat(PredictionMarketContract.isWireString(POLYMARKET_WIRE)).isTrue();
    assertThat(PredictionMarketContract.isWireString("BTC/USD")).isFalse();
    assertThat(PredictionMarketContract.isWireString("BTC/USD/200925/8956.67/P")).isFalse();
    assertThat(PredictionMarketContract.isWireString("pred/kalshi/m/YES/USD")).isFalse();
    assertThat(PredictionMarketContract.isWireString(null)).isFalse();
  }

  @Test
  public void testInvalidWireStrings() {
    // Missing the PRED prefix.
    assertThatThrownBy(
            () -> new PredictionMarketContract("BTC/USD/200925/8956.67/P"))
        .isInstanceOf(IllegalArgumentException.class);
    // Too few segments.
    assertThatThrownBy(() -> new PredictionMarketContract("PRED/kalshi/m/USD"))
        .isInstanceOf(IllegalArgumentException.class);
    // Blank segment.
    assertThatThrownBy(() -> new PredictionMarketContract("PRED//m/YES/USD"))
        .isInstanceOf(IllegalArgumentException.class);
    // Trailing separators must be rejected, not silently canonicalized away.
    assertThatThrownBy(() -> new PredictionMarketContract("PRED/kalshi/m/YES/USD/"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new PredictionMarketContract("PRED/kalshi/m/YES/USD//"))
        .isInstanceOf(IllegalArgumentException.class);
    // Empty trailing outcome segment.
    assertThatThrownBy(() -> new PredictionMarketContract("PRED/kalshi/m/YES//"))
        .isInstanceOf(IllegalArgumentException.class);
    // Empty segment after the event id.
    assertThatThrownBy(() -> new PredictionMarketContract("PRED/kalshi/e//m/YES/USD"))
        .isInstanceOf(IllegalArgumentException.class);
    // Null input.
    assertThatThrownBy(() -> new PredictionMarketContract((String) null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void testInvalidConstructorArguments() {
    assertThatThrownBy(() -> new PredictionMarketContract(null, "m", "YES", Currency.USD))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new PredictionMarketContract("kalshi", "m", "YES", null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new PredictionMarketContract("kalshi", "m/x", "YES", Currency.USD))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new PredictionMarketContract("kalshi", " ", "YES", Currency.USD))
        .isInstanceOf(IllegalArgumentException.class);
  }

  /**
   * Generic {@link Instrument} consumers must handle prediction-market contracts as first-class
   * instruments: metadata maps keyed by {@link Instrument}, generic DTO builders, and
   * serialization round-trips must preserve identity even though {@code getBase()} is null.
   */
  @Test
  public void testGenericConsumerCompatibility() throws IOException {
    PredictionMarketContract contract = new PredictionMarketContract(KALSHI_WIRE);

    // Metadata maps keyed by Instrument (InstrumentMapDeserializer path).
    Map<Instrument, InstrumentMetaData> instruments = new ConcurrentHashMap<>();
    instruments.put(contract, InstrumentMetaData.builder().priceScale(4).build());
    ExchangeMetaData metaData =
        new ExchangeMetaData(instruments, null, null, null, null);
    ExchangeMetaData metaCopy = ObjectMapperHelper.viaJSON(metaData);
    assertThat(metaCopy.getInstruments().keySet()).containsExactly(contract);
    assertThat(metaCopy.getInstruments().get(contract).getPriceScale()).isEqualTo(4);

    // Generic DTO builder path (Ticker.Builder.instrument + InstrumentDeserializer).
    Ticker ticker =
        new Ticker.Builder()
            .instrument(contract)
            .last(new java.math.BigDecimal("0.42"))
            .build();
    Ticker tickerCopy = ObjectMapperHelper.viaJSON(ticker);
    assertThat(tickerCopy.getInstrument()).isEqualTo(contract);
  }

}
