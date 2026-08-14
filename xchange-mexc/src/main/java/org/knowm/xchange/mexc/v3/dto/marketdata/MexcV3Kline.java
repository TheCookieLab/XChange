package org.knowm.xchange.mexc.v3.dto.marketdata;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.List;

/**
 * One MEXC Spot v3 kline row.
 *
 * <p>The REST endpoint returns positional arrays; the canonical order is {@code [openTime, open,
 * high, low, close, volume, closeTime, quoteAssetVolume]}. Prices and volumes are kept as strings
 * to preserve exact provider decimals; convert with {@link #openAsBigDecimal()} etc. when numeric
 * work is needed.
 */
public class MexcV3Kline {

  private final long openTime;
  private final String open;
  private final String high;
  private final String low;
  private final String close;
  private final String volume;
  private final long closeTime;
  private final String quoteAssetVolume;

  @JsonCreator
  public MexcV3Kline(List<String> values) {
    if (values == null || values.size() < 8) {
      throw new IllegalArgumentException(
          "Malformed MEXC kline row: expected 8 positional values, got "
              + (values == null ? "null" : values.size()));
    }
    this.openTime = Long.parseLong(values.get(0));
    this.open = values.get(1);
    this.high = values.get(2);
    this.low = values.get(3);
    this.close = values.get(4);
    this.volume = values.get(5);
    this.closeTime = Long.parseLong(values.get(6));
    this.quoteAssetVolume = values.get(7);
  }

  public long getOpenTime() {
    return openTime;
  }

  public String getOpen() {
    return open;
  }

  public String getHigh() {
    return high;
  }

  public String getLow() {
    return low;
  }

  public String getClose() {
    return close;
  }

  public String getVolume() {
    return volume;
  }

  public long getCloseTime() {
    return closeTime;
  }

  public String getQuoteAssetVolume() {
    return quoteAssetVolume;
  }
}
