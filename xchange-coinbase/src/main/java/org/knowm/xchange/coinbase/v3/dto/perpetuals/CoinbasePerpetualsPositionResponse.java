package org.knowm.xchange.coinbase.v3.dto.perpetuals;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

/**
 * Response containing a single perpetuals position.
 *
 * @see <a href="https://docs.cdp.coinbase.com/advanced-trade/reference/retailbrokerageapi_getintxposition">Get Perpetuals Position</a>
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class CoinbasePerpetualsPositionResponse {

  private final CoinbasePerpetualsPosition position;

  @JsonCreator
  public CoinbasePerpetualsPositionResponse(@JsonProperty("position") CoinbasePerpetualsPosition position) {
    this.position = position;
  }

  @Override
  public String toString() {
    return "CoinbasePerpetualsPositionResponse [position=" + (position == null ? null : position.getSymbol()) + "]";
  }
}

