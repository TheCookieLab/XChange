package org.knowm.xchange.coinbase.v2.dto.transactions;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

/**
 * Coinbase API v2 transaction details (human-readable title/subtitle).
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class CoinbaseV2TransactionDetails {

  private final String title;
  private final String subtitle;

  @JsonCreator
  public CoinbaseV2TransactionDetails(
      @JsonProperty("title") String title,
      @JsonProperty("subtitle") String subtitle) {
    this.title = title;
    this.subtitle = subtitle;
  }

  @Override
  public String toString() {
    return "CoinbaseV2TransactionDetails [title=" + title + ", subtitle=" + subtitle + "]";
  }
}

