package org.knowm.xchange.okex.service;

import lombok.Getter;

/**
 * Legacy candle-stick period enum kept for source compatibility.
 *
 * @deprecated use {@link org.knowm.xchange.okx.service.OkxCandleStickPeriodType} instead.
 */
@Deprecated
public enum OkexCandleStickPeriodType {
  CANDLE_STICK_1M(1, "1m"),
  CANDLE_STICK_3M(3, "3m"),
  CANDLE_STICK_5M(5, "5m"),
  CANDLE_STICK_15M(15, "15m"),
  CANDLE_STICK_30M(30, "30m"),
  CANDLE_STICK_1H(60, "1H"),
  CANDLE_STICK_2H(2 * 60, "2H"),
  CANDLE_STICK_4H(4 * 60, "4H");
  private final long periodInSecs;
  @Getter private final String fieldValue;

  OkexCandleStickPeriodType(long periodInMinutes, String fieldValue) {
    this.periodInSecs = periodInMinutes * 60;
    this.fieldValue = fieldValue;
  }

  /**
   * @deprecated use {@link
   *     org.knowm.xchange.okx.service.OkxCandleStickPeriodType#getSupportedPeriodsInSecs()}
   *     instead.
   */
  @Deprecated
  public static long[] getSupportedPeriodsInSecs() {
    return org.knowm.xchange.okx.service.OkxCandleStickPeriodType.getSupportedPeriodsInSecs();
  }
}
