package org.knowm.xchange.cryptocom.dto.account;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Derivative position row from {@code private/get-positions}. Quantities are signed (negative for
 * shorts); {@code position} carries the provider's LONG/SHORT marker. PnL fields are exact decimal
 * strings so fixture assertions stay deterministic.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CryptoComPosition {

  @JsonProperty("account_id")
  private String accountId;

  @JsonProperty("instrument_name")
  private String instrumentName;

  /** Signed quantity: negative for short positions. */
  @JsonProperty("quantity")
  private String quantity;

  /** LONG or SHORT marker. */
  @JsonProperty("position")
  private String position;

  @JsonProperty("cost")
  private String cost;

  @JsonProperty("open_pos_cost")
  private String openPosCost;

  @JsonProperty("allocated_cash")
  private String allocatedCash;

  @JsonProperty("mm_contribution")
  private String mmContribution;

  @JsonProperty("ml_contribution")
  private String mlContribution;

  @JsonProperty("position_cost")
  private String positionCost;

  @JsonProperty("mark_price")
  private String markPrice;

  @JsonProperty("last_price")
  private String lastPrice;

  @JsonProperty("average_cost")
  private String averageCost;

  @JsonProperty("session_upl")
  private String sessionUpl;

  @JsonProperty("upl")
  private String upl;

  @JsonProperty("upl_history")
  private String uplHistory;

  @JsonProperty("close_price")
  private String closePrice;

  @JsonProperty("insert_time")
  private Long insertTime;

  @JsonProperty("update_time")
  private Long updateTime;
}