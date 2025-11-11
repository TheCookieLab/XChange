package org.knowm.xchange.coinbase.v3.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

/**
 * Response from the GET /time endpoint.
 *
 * <p>Returns the current server time in ISO 8601 format.
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class CoinbaseTimeResponse {

  private final String iso;

  @JsonCreator
  public CoinbaseTimeResponse(@JsonProperty("iso") String iso) {
    this.iso = iso;
  }

  @Override
  public String toString() {
    return "CoinbaseTimeResponse [iso=" + iso + "]";
  }
}

