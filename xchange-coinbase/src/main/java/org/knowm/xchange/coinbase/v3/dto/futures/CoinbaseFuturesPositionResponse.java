package org.knowm.xchange.coinbase.v3.dto.futures;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

/**
 * Response containing a single futures position.
 *
 * @see <a href="https://docs.cdp.coinbase.com/advanced-trade/reference/retailbrokerageapi_getfcmposition">Get Futures Position</a>
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class CoinbaseFuturesPositionResponse {

  private final CoinbaseFuturesPosition position;

  @JsonCreator
  public CoinbaseFuturesPositionResponse(@JsonProperty("position") CoinbaseFuturesPosition position) {
    this.position = position;
  }

  @Override
  public String toString() {
    return "CoinbaseFuturesPositionResponse [position=" + (position == null ? null : position.getProductId()) + "]";
  }
}

