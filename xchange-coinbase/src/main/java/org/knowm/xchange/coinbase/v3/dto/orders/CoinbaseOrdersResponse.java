package org.knowm.xchange.coinbase.v3.dto.orders;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Getter;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CoinbaseOrdersResponse {

  private final List<CoinbaseFill> fills;

  @Getter
  private final String cursor;

  public CoinbaseOrdersResponse(@JsonProperty("fills") List<CoinbaseFill> fills,
      @JsonProperty("cursor") String cursor) {
    this.fills = fills;
    this.cursor = cursor;
  }

  /** Returns the provider fills collection, or null when the required field was absent. */
  public List<CoinbaseFill> getFills() {
    return fills;
  }

  @Override
  public String toString() {
    return "CoinbaseOrdersResponse [fills=" + (fills == null ? 0 : fills.size()) + ", cursor="
        + cursor + "]";
  }
}
