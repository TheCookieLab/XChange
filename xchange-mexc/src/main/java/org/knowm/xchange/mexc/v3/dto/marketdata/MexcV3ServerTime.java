package org.knowm.xchange.mexc.v3.dto.marketdata;

import com.fasterxml.jackson.annotation.JsonProperty;

/** MEXC Spot v3 server time, from {@code GET /api/v3/time}. */
public class MexcV3ServerTime {

  /** Server time in Unix milliseconds. */
  private final long serverTime;

  public MexcV3ServerTime(@JsonProperty("serverTime") long serverTime) {
    this.serverTime = serverTime;
  }

  public long getServerTime() {
    return serverTime;
  }
}
