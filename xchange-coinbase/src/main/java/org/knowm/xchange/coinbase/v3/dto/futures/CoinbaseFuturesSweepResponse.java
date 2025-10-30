package org.knowm.xchange.coinbase.v3.dto.futures;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

/**
 * Response from scheduling or canceling a futures sweep.
 *
 * @see <a href="https://docs.cdp.coinbase.com/advanced-trade/reference/retailbrokerageapi_schedulefcmsweep">Schedule Futures Sweep</a>
 * @see <a href="https://docs.cdp.coinbase.com/advanced-trade/reference/retailbrokerageapi_cancelfcmsweep">Cancel Futures Sweep</a>
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class CoinbaseFuturesSweepResponse {

  private final Boolean success;

  @JsonCreator
  public CoinbaseFuturesSweepResponse(@JsonProperty("success") Boolean success) {
    this.success = success;
  }

  @Override
  public String toString() {
    return "CoinbaseFuturesSweepResponse [success=" + success + "]";
  }
}

