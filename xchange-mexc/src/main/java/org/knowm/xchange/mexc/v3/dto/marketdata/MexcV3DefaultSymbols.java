package org.knowm.xchange.mexc.v3.dto.marketdata;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Default-symbols envelope from {@code GET /api/v3/defaultSymbols}.
 *
 * <p>Unlike the other market endpoints, this one wraps the payload in the generic result envelope
 * {@code {"code":200,"data":[...],"msg":null}}.
 */
public class MexcV3DefaultSymbols {

  private final int code;
  private final String[] data;
  private final String msg;

  public MexcV3DefaultSymbols(
      @JsonProperty("code") int code,
      @JsonProperty("data") String[] data,
      @JsonProperty("msg") String msg) {
    this.code = code;
    this.data = data;
    this.msg = msg;
  }

  public int getCode() {
    return code;
  }

  /** The symbols returned by the provider; {@code null} when absent. */
  public String[] getData() {
    return data;
  }

  public String getMsg() {
    return msg;
  }
}
