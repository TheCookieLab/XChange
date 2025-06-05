package org.knowm.xchange.coinsph.dto.account;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.ToString;
import org.knowm.xchange.coinsph.dto.CoinsphResponse;

/**
 * Represents a cash out response from Coins.ph API Based on POST /openapi/fiat/v1/cash-out endpoint
 */
@Getter
@ToString
public class CoinsphCashOutResponse extends CoinsphResponse {

  private final String orderId;

  public CoinsphCashOutResponse(@JsonProperty("orderId") String orderId) {
    this.orderId = orderId;
  }
}
