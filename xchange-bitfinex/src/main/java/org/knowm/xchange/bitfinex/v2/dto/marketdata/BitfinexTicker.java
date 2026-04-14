package org.knowm.xchange.bitfinex.v2.dto.marketdata;

import java.math.BigDecimal;

public interface BitfinexTicker {

  default boolean isFundingCurrency() {
    return false;
  }

  default boolean isTradingPair() {
    return false;
  }

  String getSymbol();

  BigDecimal getBid();

  BigDecimal getBidSize();

  BigDecimal getAsk();

  BigDecimal getAskSize();

  BigDecimal getDailyChange();

  BigDecimal getDailyChangePerc();

  BigDecimal getLastPrice();

  BigDecimal getVolume();

  BigDecimal getHigh();

  BigDecimal getLow();

  /** Millisecond timestamp from the Bitfinex ticker row, or null when the exchange omits it. */
  default Long getTimestamp() {
    return null;
  }
}
