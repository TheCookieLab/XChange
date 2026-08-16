package org.knowm.xchange.cryptocom.dto.marketdata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Row of {@code public/get-expired-settlement-price} result.data[] (official schema: i, x, v, t).
 * Provides the expired settlement reference price for expired future/option instruments.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CryptoComExpiredSettlementPrice {

  /** Instrument name. */
  @JsonProperty("i")
  private String instrumentName;

  /** Expiry timestamp (ms). */
  @JsonProperty("x")
  private String expiryTimestampMs;

  /** Expired settlement price, exact decimal string. */
  @JsonProperty("v")
  private String value;

  /** Timestamp of the settlement price (ms). */
  @JsonProperty("t")
  private String timestampMs;
}