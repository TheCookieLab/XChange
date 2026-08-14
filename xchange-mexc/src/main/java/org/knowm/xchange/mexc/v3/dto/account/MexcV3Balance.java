package org.knowm.xchange.mexc.v3.dto.account;

import com.fasterxml.jackson.annotation.JsonProperty;

/** One balance row of {@code GET /api/v3/account}. Amounts are provider strings. */
public class MexcV3Balance {

  private final String asset;
  private final String free;
  private final String locked;

  public MexcV3Balance(
      @JsonProperty("asset") String asset,
      @JsonProperty("free") String free,
      @JsonProperty("locked") String locked) {
    this.asset = asset;
    this.free = free;
    this.locked = locked;
  }

  public String getAsset() {
    return asset;
  }

  public String getFree() {
    return free;
  }

  public String getLocked() {
    return locked;
  }
}
