package org.knowm.xchange.mexc.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @deprecated MEXC Spot v2 ({@code /open/api/v2}) is frozen for compatibility; use the Spot v3
 *     implementation in {@code org.knowm.xchange.mexc.v3} instead. See the xchange-mexc README
 *     migration notes for the removal policy.
 */
@Deprecated
public class MEXCResult<T> {

  private final int code;
  private final T data;

  @JsonCreator
  public MEXCResult(@JsonProperty("code") int code, @JsonProperty("data") T data) {
    this.code = code;
    this.data = data;
  }

  public T getData() {
    return data;
  }

  public int getCode() {
    return code;
  }
}
