package org.knowm.xchange.bitget.uta.v3.common;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import lombok.Data;
import si.mazi.rescu.ExceptionalReturnContentException;

/**
 * Bitget UTA v3 response envelope.
 *
 * <p>Unlike the classic v2 API (integer {@code code} with {@code 0} meaning success), v3 uses the
 * string {@code "00000"} for success. Any other code is an error; the {@link
 * ExceptionalReturnContentException} thrown from {@link #setCode(String)} makes the REST client
 * parse the error body into {@link BitgetUtaV3Exception}.
 *
 * <p>Deliberately NOT {@code @Jacksonized}: builder-based deserialization would bypass {@link
 * #setCode(String)} and silently accept error envelopes with a null {@code data}.
 */
@Data
public class BitgetUtaV3Response<T> {

  /** Provider result code; {@code "00000"} is success. */
  @JsonProperty("code")
  private String code;

  @JsonProperty("msg")
  private String message;

  @JsonProperty("requestTime")
  private Instant requestTime;

  @JsonProperty("data")
  private T data;

  public void setCode(String code) {
    if (!"00000".equals(code)) {
      throw new ExceptionalReturnContentException(code);
    }
    this.code = code;
  }
}
