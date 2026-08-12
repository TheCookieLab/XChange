package org.knowm.xchange.bitget.uta.v3.market;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

/**
 * Bitget UTA v3 candlestick.
 *
 * <p>{@code GET /api/v3/market/candles} returns arrays of {@code [start, open, high, low, close,
 * volume, turnover]} where timestamps are Unix milliseconds and all values are decimal strings. The
 * wire shape is a positional array, hence the {@link JsonCreator} factory mapping.
 */
public final class BitgetUtaV3Candle {

  private final long startTime;
  private final BigDecimal open;
  private final BigDecimal high;
  private final BigDecimal low;
  private final BigDecimal close;
  private final BigDecimal volume;
  private final BigDecimal turnover;

  private BitgetUtaV3Candle(
      long startTime,
      BigDecimal open,
      BigDecimal high,
      BigDecimal low,
      BigDecimal close,
      BigDecimal volume,
      BigDecimal turnover) {
    this.startTime = startTime;
    this.open = open;
    this.high = high;
    this.low = low;
    this.close = close;
    this.volume = volume;
    this.turnover = turnover;
  }

  @JsonCreator
  public static BitgetUtaV3Candle fromArray(List<String> values) {
    return new BitgetUtaV3Candle(
        Long.parseLong(values.get(0)),
        new BigDecimal(values.get(1)),
        new BigDecimal(values.get(2)),
        new BigDecimal(values.get(3)),
        new BigDecimal(values.get(4)),
        new BigDecimal(values.get(5)),
        new BigDecimal(values.get(6)));
  }

  @JsonProperty("start")
  public long getStartTime() {
    return startTime;
  }

  @JsonProperty("open")
  public BigDecimal getOpen() {
    return open;
  }

  @JsonProperty("high")
  public BigDecimal getHigh() {
    return high;
  }

  @JsonProperty("low")
  public BigDecimal getLow() {
    return low;
  }

  @JsonProperty("close")
  public BigDecimal getClose() {
    return close;
  }

  @JsonProperty("volume")
  public BigDecimal getVolume() {
    return volume;
  }

  @JsonProperty("turnover")
  public BigDecimal getTurnover() {
    return turnover;
  }
}
