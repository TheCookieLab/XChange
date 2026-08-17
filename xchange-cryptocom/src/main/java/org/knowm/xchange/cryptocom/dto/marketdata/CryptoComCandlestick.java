package org.knowm.xchange.cryptocom.dto.marketdata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Single candlestick row from {@code public/get-candlestick} result.data[] (official
 * {@code CandlestickItem}). All OHLCV values keep the provider's exact decimal-string form.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CryptoComCandlestick {

  @JsonProperty("o")
  private String open;

  @JsonProperty("h")
  private String high;

  @JsonProperty("l")
  private String low;

  @JsonProperty("c")
  private String close;

  @JsonProperty("v")
  private String volume;

  /** Candlestick start time, Unix timestamp in milliseconds. */
  @JsonProperty("t")
  private String startTimeMs;
}