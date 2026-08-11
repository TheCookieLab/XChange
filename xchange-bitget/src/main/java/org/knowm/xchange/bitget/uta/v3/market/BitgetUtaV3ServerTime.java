package org.knowm.xchange.bitget.uta.v3.market;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

/**
 * Bitget UTA v3 server time payload.
 *
 * <p>{@code GET /api/v3/market/time} returns the server time as a string of Unix milliseconds (live
 * payload: {@code {"data":{"serverTime":"1786487306240"}}}).
 */
@Data
@Builder
@Jacksonized
public class BitgetUtaV3ServerTime {

  @JsonProperty("serverTime")
  private String serverTime;
}
