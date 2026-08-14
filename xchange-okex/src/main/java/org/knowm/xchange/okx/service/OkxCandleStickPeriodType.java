package org.knowm.xchange.okx.service;

import lombok.Getter;

public enum OkxCandleStickPeriodType {
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

  OkxCandleStickPeriodType(long periodInMinutes, String fieldValue) {
    this.periodInSecs = periodInMinutes * 60;
    this.fieldValue = fieldValue;
  }

  static OkxCandleStickPeriodType getPeriodTypeFromSecs(long periodInSecs) {
    OkxCandleStickPeriodType result = null;
    for (OkxCandleStickPeriodType period : OkxCandleStickPeriodType.values()) {
      if (period.periodInSecs == periodInSecs) {
        result = period;
        break;
      }
    }
    return result;
  }

  public static long[] getSupportedPeriodsInSecs() {
    long[] result = new long[OkxCandleStickPeriodType.values().length];
    int index = 0;
    for (OkxCandleStickPeriodType period : OkxCandleStickPeriodType.values()) {
      result[index++] = period.periodInSecs;
    }
    return result;
  }
}
