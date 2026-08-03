package org.knowm.xchange.prediction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.knowm.xchange.currency.Currency;
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

  @Test
  public void testOutcomeSideParsing() {
    assertThat(PredictionOutcomeSide.fromString("YES")).isEqualTo(PredictionOutcomeSide.YES);
    assertThat(PredictionOutcomeSide.fromString("no")).isEqualTo(PredictionOutcomeSide.NO);
    assertThat(PredictionOutcomeSide.fromString(" Yes ")).isEqualTo(PredictionOutcomeSide.YES);
    assertThatThrownBy(() -> PredictionOutcomeSide.fromString("MAYBE"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> PredictionOutcomeSide.fromString(null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
