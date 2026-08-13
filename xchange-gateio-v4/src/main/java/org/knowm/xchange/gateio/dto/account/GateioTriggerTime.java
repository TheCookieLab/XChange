package org.knowm.xchange.gateio.dto.account;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

/**
 * Countdown-cancel response (POST /spot/countdown_cancel_all).
 *
 * <p>The provider returns a {@code TriggerTime} payload: the epoch-millisecond timestamp at which
 * the countdown ends. {@code 0} means the countdown was cancelled before it expired.
 */
@Data
@Builder
@Jacksonized
public class GateioTriggerTime {

  /** Epoch milliseconds at which the countdown ends; {@code 0} = countdown cancelled. */
  @JsonProperty("triggerTime")
  Long triggerTime;
}
