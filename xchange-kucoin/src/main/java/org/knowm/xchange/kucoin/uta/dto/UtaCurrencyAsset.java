package org.knowm.xchange.kucoin.uta.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

/**
 * UTA currency-level asset snapshot (one entry of the {@code accounts[].currencies[]} array).
 *
 * <p>Preserves liability and collateral status so unified balances are never lossily flattened into
 * classic balances.
 */
@Data
public class UtaCurrencyAsset {

  @JsonProperty("currency")
  private String currency;

  @JsonProperty("equity")
  private BigDecimal equity;

  @JsonProperty("hold")
  private BigDecimal hold;

  @JsonProperty("balance")
  private BigDecimal balance;

  @JsonProperty("available")
  private BigDecimal available;

  @JsonProperty("liability")
  private BigDecimal liability;

  @JsonProperty("potentialBorrow")
  private BigDecimal potentialBorrow;

  /** Collateral status: 1 normal, 2 approaching cap, 3 cap exceeded. */
  @JsonProperty("collateralStatus")
  private String collateralStatus;
}
