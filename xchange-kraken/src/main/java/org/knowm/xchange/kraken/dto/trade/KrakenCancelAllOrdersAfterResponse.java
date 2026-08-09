package org.knowm.xchange.kraken.dto.trade;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Result of the Kraken CancelAllOrdersAfter (dead-man switch) endpoint.
 *
 * <p>The timer cancels all open orders when {@code triggerTime} is reached unless the client
 * re-arms it with a new request. A timeout of zero disables the timer.
 */
public class KrakenCancelAllOrdersAfterResponse {

  private final String currentTime;
  private final String triggerTime;

  public KrakenCancelAllOrdersAfterResponse(
      @JsonProperty("currentTime") String currentTime,
      @JsonProperty("triggerTime") String triggerTime) {

    this.currentTime = currentTime;
    this.triggerTime = triggerTime;
  }

  /**
   * @return engine time when the request was handled (RFC3339)
   */
  public String getCurrentTime() {
    return currentTime;
  }

  /**
   * @return time at which all open orders will be cancelled unless the timer is re-armed
   */
  public String getTriggerTime() {
    return triggerTime;
  }

  @Override
  public String toString() {
    return "KrakenCancelAllOrdersAfterResponse [currentTime="
        + currentTime
        + ", triggerTime="
        + triggerTime
        + "]";
  }
}
