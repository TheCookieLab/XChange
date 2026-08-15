package org.knowm.xchange.mexc.service;

import static org.knowm.xchange.mexc.MEXCErrorUtils.*;

import com.fasterxml.jackson.annotation.JsonProperty;
import si.mazi.rescu.HttpStatusExceptionSupport;

/**
 * @deprecated MEXC Spot v2 ({@code /open/api/v2}) is frozen for compatibility; use the Spot v3
 *     implementation in {@code org.knowm.xchange.mexc.v3} instead. See the xchange-mexc README
 *     migration notes for the removal policy.
 */
@Deprecated
public class MEXCException extends HttpStatusExceptionSupport {

  private final String message;
  private final int code;

  public MEXCException(@JsonProperty("msg") String message, @JsonProperty("code") int code) {
    super(message);
    this.message = message;
    this.code = code;
  }

  @Override
  public String getMessage() {
    return message;
  }

  @Override
  public String toString() {
    return code + "[" + getOptionalErrorMessage(code).orElse("null") + "]:" + message;
  }
}
