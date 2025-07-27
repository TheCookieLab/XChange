package org.knowm.xchange.coinbase.v3.dto.transactions;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import lombok.Getter;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class CoinbaseTransactionSummaryResponse {

  private final BigDecimal totalVolume;
  private final BigDecimal totalFees;
  private final CoinbaseFeeTier feeTier;

  @JsonCreator
  public CoinbaseTransactionSummaryResponse(@JsonProperty("total_volume") BigDecimal totalVolume,
      @JsonProperty("total_fees") BigDecimal totalFees,
      @JsonProperty("fee_tier") CoinbaseFeeTier feeTier) {

    this.totalVolume = totalVolume;
    this.totalFees = totalFees;
    this.feeTier = feeTier;
  }

  @Override
  public String toString() {
    return "CoinbaseTransactionSummaryResponse [totalVolume=" + totalVolume + ", totalFees="
        + totalFees + ", feeTier=" + feeTier + "]";
  }
}
