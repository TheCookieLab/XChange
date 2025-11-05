package org.knowm.xchange.coinbase.v3.dto.products;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Getter;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CoinbaseProductCandlesResponse {

  @Getter
  private final List<CoinbaseProductCandle> candles;

  private CoinbaseProductCandlesResponse(
      @JsonProperty("candles") List<CoinbaseProductCandle> candles) {
    this.candles = candles;
  }

  @Override
  public String toString() {
    return "CoinbaseProductCandlesResponse [candles:" + candles + "]";
  }
}
