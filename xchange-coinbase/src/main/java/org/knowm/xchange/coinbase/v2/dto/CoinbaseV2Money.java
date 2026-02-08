package org.knowm.xchange.coinbase.v2.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import lombok.Getter;

/**
 * Coinbase API v2 money amount (amount + currency code).
 *
 * <p>Coinbase v2 represents amounts as strings; Jackson maps them to {@link BigDecimal} for
 * consumers.</p>
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class CoinbaseV2Money {

  private final BigDecimal amount;
  private final String currency;

  @JsonCreator
  public CoinbaseV2Money(@JsonProperty("amount") BigDecimal amount,
      @JsonProperty("currency") String currency) {
    this.amount = amount;
    this.currency = currency;
  }

  @Override
  public String toString() {
    return "CoinbaseV2Money [amount=" + amount + ", currency=" + currency + "]";
  }
}

