package org.knowm.xchange.mexc.v3.dto.marketdata;

import com.fasterxml.jackson.annotation.JsonProperty;

/** One entry of the {@code rateLimits} array inside {@code GET /api/v3/exchangeInfo}. */
public class MexcV3RateLimit {

  private final String rateLimitType;
  private final String interval;
  private final int intervalNum;
  private final int limit;

  public MexcV3RateLimit(
      @JsonProperty("rateLimitType") String rateLimitType,
      @JsonProperty("interval") String interval,
      @JsonProperty("intervalNum") int intervalNum,
      @JsonProperty("limit") int limit) {
    this.rateLimitType = rateLimitType;
    this.interval = interval;
    this.intervalNum = intervalNum;
    this.limit = limit;
  }

  public String getRateLimitType() {
    return rateLimitType;
  }

  public String getInterval() {
    return interval;
  }

  public int getIntervalNum() {
    return intervalNum;
  }

  public int getLimit() {
    return limit;
  }
}
