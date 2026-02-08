package org.knowm.xchange.coinbase.v2.dto.accounts;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

/**
 * Coinbase API v2 currency object.
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class CoinbaseV2Currency {

  private final String code;
  private final String name;
  private final Integer exponent;
  private final String type;

  @JsonCreator
  public CoinbaseV2Currency(
      @JsonProperty("code") String code,
      @JsonProperty("name") String name,
      @JsonProperty("exponent") Integer exponent,
      @JsonProperty("type") String type) {
    this.code = code;
    this.name = name;
    this.exponent = exponent;
    this.type = type;
  }

  @Override
  public String toString() {
    return "CoinbaseV2Currency [code=" + code + ", name=" + name + ", exponent=" + exponent
        + ", type=" + type + "]";
  }
}

