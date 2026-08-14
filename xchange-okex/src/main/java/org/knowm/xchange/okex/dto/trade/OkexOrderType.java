package org.knowm.xchange.okex.dto.trade;

import org.knowm.xchange.okx.dto.trade.OkxOrderType;

/**
 * @deprecated use {@link org.knowm.xchange.okx.dto.trade.OkxOrderType} instead.
 */
@Deprecated
public enum OkexOrderType {
  market,
  limit,
  post_only,
  fok,
  iok,
  optimal_limit_ioc;

  public static OkexOrderType from(OkxOrderType value) {
    return value == null ? null : OkexOrderType.valueOf(value.name());
  }

  public OkxOrderType to() {
    return OkxOrderType.valueOf(name());
  }
}
