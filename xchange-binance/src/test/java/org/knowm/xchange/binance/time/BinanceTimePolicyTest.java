package org.knowm.xchange.binance.time;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.Test;
import org.knowm.xchange.binance.BinanceTimestampFactory;
import org.knowm.xchange.binance.config.BinanceTimestampUnit;

public class BinanceTimePolicyTest {

  @Test
  public void testApplyUnit() {
    assertThat(BinanceTimePolicy.applyUnit(1_700_000_000_000L, BinanceTimestampUnit.MILLISECONDS))
        .isEqualTo(1_700_000_000_000L);
    assertThat(BinanceTimePolicy.applyUnit(1_700_000_000_000L, BinanceTimestampUnit.MICROSECONDS))
        .isEqualTo(1_700_000_000_000_000L);
  }

  @Test
  public void testCurrentTimestampIsWithinClockBounds() {
    long before = System.currentTimeMillis();
    long value = BinanceTimePolicy.currentTimestampMillis();
    long after = System.currentTimeMillis();
    assertThat(value).isBetween(before, after);
  }

  @Test
  public void testValidateRecvWindow() {
    BinanceTimePolicy.validateRecvWindow(0L);
    BinanceTimePolicy.validateRecvWindow(60_000L);
    BinanceTimePolicy.validateRecvWindow(null);
    assertThatThrownBy(() -> BinanceTimePolicy.validateRecvWindow(-1L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("receive window");
    assertThatThrownBy(() -> BinanceTimePolicy.validateRecvWindow(60_001L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("receive window");
  }

  @Test
  public void testTimestampFactoryUsesConfiguredUnit() {
    BinanceTimestampFactory millisFactory =
        new BinanceTimestampFactory(null, null, BinanceTimestampUnit.MILLISECONDS);
    BinanceTimestampFactory microsFactory =
        new BinanceTimestampFactory(null, null, BinanceTimestampUnit.MICROSECONDS);

    long millis = millisFactory.createValue();
    long micros = microsFactory.createValue();

    assertThat(micros / 1000L).isEqualTo(millis);
  }

  @Test
  public void testLegacyTimestampFactoryConstructorDefaultsToMillis() {
    BinanceTimestampFactory factory = new BinanceTimestampFactory(null, null);
    assertThat(factory.createValue()).isGreaterThan(1_600_000_000_000L);
  }
}
