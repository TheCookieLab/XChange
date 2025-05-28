package org.knowm.xchange.coinbase.v3.dto.products;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import lombok.Getter;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class CoinbaseProductCandle {

  private final String start;
  private final BigDecimal low;
  private final BigDecimal high;
  private final BigDecimal open;
  private final BigDecimal close;
  private final BigDecimal volume;

  @JsonCreator
  public CoinbaseProductCandle(@JsonProperty("start") String start,
      @JsonProperty("low") BigDecimal low, @JsonProperty("high") BigDecimal high,
      @JsonProperty("open") BigDecimal open, @JsonProperty("close") BigDecimal close,
      @JsonProperty("volume") BigDecimal volume) {
    this.start = start;
    this.low = low;
    this.high = high;
    this.open = open;
    this.close = close;
    this.volume = volume;
  }

  @Override
  public String toString() {
    return "CoinbaseProductCandle [start=" + start + ", open=" + open + ", high=" + high + ", low="
        + low + ", close=" + close + ", volume=" + volume + "]";
  }

}
