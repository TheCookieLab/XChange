package org.knowm.xchange.coinbase.v3.dto.portfolios;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

/**
 * Represents a portfolio in Coinbase Advanced Trade.
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class CoinbasePortfolio {

  private final String uuid;
  private final String name;
  private final String type;
  private final Boolean deleted;

  @JsonCreator
  public CoinbasePortfolio(
      @JsonProperty("uuid") String uuid,
      @JsonProperty("name") String name,
      @JsonProperty("type") String type,
      @JsonProperty("deleted") Boolean deleted) {
    this.uuid = uuid;
    this.name = name;
    this.type = type;
    this.deleted = deleted;
  }

  @Override
  public String toString() {
    return "CoinbasePortfolio [uuid=" + uuid + ", name=" + name + ", type=" + type + ", deleted=" + deleted + "]";
  }
}

