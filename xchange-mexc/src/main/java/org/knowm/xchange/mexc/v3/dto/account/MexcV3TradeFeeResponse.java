package org.knowm.xchange.mexc.v3.dto.account;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Envelope of {@code GET /api/v3/tradeFee}: {@code {data, code, msg, timestamp}}. */
public class MexcV3TradeFeeResponse {

  private final MexcV3TradeFeeData data;
  private final int code;
  private final String msg;
  private final long timestamp;

  public MexcV3TradeFeeResponse(
      @JsonProperty("data") MexcV3TradeFeeData data,
      @JsonProperty("code") int code,
      @JsonProperty("msg") String msg,
      @JsonProperty("timestamp") long timestamp) {
    this.data = data;
    this.code = code;
    this.msg = msg;
    this.timestamp = timestamp;
  }

  public MexcV3TradeFeeData getData() {
    return data;
  }

  public int getCode() {
    return code;
  }

  public String getMsg() {
    return msg;
  }

  public long getTimestamp() {
    return timestamp;
  }
}
