package org.knowm.xchange.kucoin.uta.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import lombok.Data;

/**
 * UTA account-level funds summary.
 *
 * <p>Fields are strings on the wire to preserve exact decimal precision; mapped to {@link
 * BigDecimal} here.
 */
@Data
public class UtaAccountOverview {

  @JsonProperty("accountType")
  private String accountType;

  @JsonProperty("riskRatio")
  private BigDecimal riskRatio;

  @JsonProperty("equity")
  private BigDecimal equity;

  @JsonProperty("liability")
  private BigDecimal liability;

  @JsonProperty("availableMargin")
  private BigDecimal availableMargin;

  @JsonProperty("adjustedEquity")
  private BigDecimal adjustedEquity;

  @JsonProperty("im")
  private BigDecimal im;

  @JsonProperty("mm")
  private BigDecimal mm;
}
