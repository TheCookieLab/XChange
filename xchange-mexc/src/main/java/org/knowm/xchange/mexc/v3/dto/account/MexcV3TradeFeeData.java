package org.knowm.xchange.mexc.v3.dto.account;

import com.fasterxml.jackson.annotation.JsonProperty;

/** {@code data} payload of {@code GET /api/v3/tradeFee}. */
public class MexcV3TradeFeeData {

  private final String makerCommission;
  private final String takerCommission;

  public MexcV3TradeFeeData(
      @JsonProperty("makerCommission") String makerCommission,
      @JsonProperty("takerCommission") String takerCommission) {
    this.makerCommission = makerCommission;
    this.takerCommission = takerCommission;
  }

  public String getMakerCommission() {
    return makerCommission;
  }

  public String getTakerCommission() {
    return takerCommission;
  }
}
