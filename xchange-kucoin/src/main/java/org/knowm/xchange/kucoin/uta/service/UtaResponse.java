package org.knowm.xchange.kucoin.uta.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;
import lombok.Data;

/**
 * UTA response envelope.
 *
 * <p>Every UTA endpoint returns {@code {"code": "200000", "msg": ..., "data": ...}}; {@code code}
 * is a string and must never be parsed as a number.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class UtaResponse<T> implements Serializable {

  private static final long serialVersionUID = 1L;

  private String code;
  private String msg;
  private T data;

  public boolean isSuccessful() {
    return UtaConstants.SUCCESS_CODE.equals(code);
  }

  public String getMessage() {
    return msg;
  }
}
