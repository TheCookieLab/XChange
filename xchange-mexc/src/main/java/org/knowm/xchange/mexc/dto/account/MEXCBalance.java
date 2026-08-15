package org.knowm.xchange.mexc.dto.account;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @deprecated MEXC Spot v2 ({@code /open/api/v2}) is frozen for compatibility; use the Spot v3
 *     implementation in {@code org.knowm.xchange.mexc.v3} instead. See the xchange-mexc README
 *     migration notes for the removal policy.
 */
@Deprecated
public class MEXCBalance {

  private final String frozen;
  private final String available;

  public MEXCBalance(
      @JsonProperty("frozen") String frozen, @JsonProperty("available") String available) {
    this.frozen = frozen;
    this.available = available;
  }

  public String getFrozen() {
    return frozen;
  }

  public String getAvailable() {
    return available;
  }
}
