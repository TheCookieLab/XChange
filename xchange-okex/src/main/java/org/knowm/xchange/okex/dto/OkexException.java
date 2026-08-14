package org.knowm.xchange.okex.dto;

import org.knowm.xchange.okx.dto.OkxException;

/**
 * @deprecated use {@link org.knowm.xchange.okx.dto.OkxException} instead.
 */
@Deprecated
public class OkexException extends OkxException {

  public OkexException(String message, int code) {
    super(message, code);
  }

  public OkexException(OkxException cause) {
    super(cause.getMessage(), cause.getCode());
  }
}
