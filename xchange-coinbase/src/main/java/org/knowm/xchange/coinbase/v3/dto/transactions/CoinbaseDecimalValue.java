package org.knowm.xchange.coinbase.v3.dto.transactions;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import lombok.Getter;

/**
 * Decimal object used by Coinbase transaction-summary fields.
 *
 * <p>The wire contract is an object containing a decimal string. Scalar values are intentionally not
 * accepted so malformed or stale schemas fail deserialization rather than becoming defaults.
 *
 * @since 1.0.2
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public final class CoinbaseDecimalValue {
  private final BigDecimal value;

  /** Creates a typed decimal from Coinbase's exact decimal string. */
  @JsonCreator
  public CoinbaseDecimalValue(@JsonProperty("value") String value) {
    this.value = value == null ? null : new BigDecimal(value);
  }
}
