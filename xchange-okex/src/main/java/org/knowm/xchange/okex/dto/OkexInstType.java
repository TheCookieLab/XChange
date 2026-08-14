package org.knowm.xchange.okex.dto;

import org.knowm.xchange.okx.dto.OkxInstType;
import org.knowm.xchange.service.marketdata.params.Params;

/**
 * @deprecated use {@link org.knowm.xchange.okx.dto.OkxInstType} instead.
 */
@Deprecated
public enum OkexInstType implements Params {
  SPOT,
  MARGIN,
  SWAP,
  FUTURES,
  OPTION,
  ANY;

  public static OkexInstType from(OkxInstType value) {
    return value == null ? null : OkexInstType.valueOf(value.name());
  }

  public OkxInstType to() {
    return OkxInstType.valueOf(name());
  }
}
