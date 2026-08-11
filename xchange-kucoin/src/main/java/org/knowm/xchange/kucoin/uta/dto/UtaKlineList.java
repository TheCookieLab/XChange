package org.knowm.xchange.kucoin.uta.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

/**
 * UTA candlestick list.
 *
 * <p>Each candle is a 7-element array of strings: start time (seconds), open, high, low, close,
 * volume, amount.
 */
@Data
public class UtaKlineList {

  @JsonProperty("tradeType")
  private String tradeType;

  @JsonProperty("symbol")
  private String symbol;

  @JsonProperty("list")
  private List<List<String>> list;

  /** @return the candle row as typed values; {@code null} list yields an empty result. */
  public java.util.List<UtaKline> typed() {
    if (list == null) {
      return java.util.Collections.emptyList();
    }
    return list.stream().map(UtaKline::from).collect(java.util.stream.Collectors.toList());
  }

  @Data
  public static class UtaKline {
    private long startTimeSeconds;
    private BigDecimal open;
    private BigDecimal high;
    private BigDecimal low;
    private BigDecimal close;
    private BigDecimal volume;
    private BigDecimal amount;

    static UtaKline from(List<String> row) {
      if (row == null || row.size() < 7) {
        throw new IllegalArgumentException("Invalid UTA kline row: " + row);
      }
      UtaKline k = new UtaKline();
      k.startTimeSeconds = Long.parseLong(row.get(0));
      k.open = new BigDecimal(row.get(1));
      k.high = new BigDecimal(row.get(2));
      k.low = new BigDecimal(row.get(3));
      k.close = new BigDecimal(row.get(4));
      k.volume = new BigDecimal(row.get(5));
      k.amount = new BigDecimal(row.get(6));
      return k;
    }
  }
}
