package info.bitrich.xchangestream.bitget.uta.v3.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

/**
 * Bitget UTA v3 kline push payload ({@code {start, open, close, high, low, volume, turnover}}).
 *
 * <p>Pushed once per interval, and additionally per second while a trade happens inside the
 * interval.
 *
 * @since 5.1.0
 */
@Data
@Builder
@Jacksonized
public class BitgetUtaV3KlineData {

  /** Interval start, epoch milliseconds. */
  @JsonProperty("start")
  private Long start;

  @JsonProperty("open")
  private BigDecimal open;

  @JsonProperty("close")
  private BigDecimal close;

  @JsonProperty("high")
  private BigDecimal high;

  @JsonProperty("low")
  private BigDecimal low;

  @JsonProperty("volume")
  private BigDecimal volume;

  @JsonProperty("turnover")
  private BigDecimal turnover;
}
