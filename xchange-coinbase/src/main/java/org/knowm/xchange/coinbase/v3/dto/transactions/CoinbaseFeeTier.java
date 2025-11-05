package org.knowm.xchange.coinbase.v3.dto.transactions;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import lombok.Getter;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class CoinbaseFeeTier {

  private final BigDecimal takerFeeRate;
  private final BigDecimal makerFeeRate;

  @JsonCreator
  public CoinbaseFeeTier(@JsonProperty("taker_fee_rate") BigDecimal takerFeeRate,
      @JsonProperty("maker_fee_rate") BigDecimal makerFeeRate) {

    this.takerFeeRate = takerFeeRate;
    this.makerFeeRate = makerFeeRate;
  }

  @Override
  public String toString() {
    return "CoinbaseFeeTier [takerFeeRate=" + takerFeeRate + ", makerFeeRate=" + makerFeeRate + "]";
  }
}
