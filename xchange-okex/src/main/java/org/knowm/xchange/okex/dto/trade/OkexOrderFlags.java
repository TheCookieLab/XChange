package org.knowm.xchange.okex.dto.trade;

import org.knowm.xchange.dto.Order;
import org.knowm.xchange.okx.dto.trade.OkxOrderFlags;

/**
 * @deprecated use {@link org.knowm.xchange.okx.dto.trade.OkxOrderFlags} instead.
 */
@Deprecated
public enum OkexOrderFlags implements Order.IOrderFlags {
  POST_ONLY,
  REDUCE_ONLY,
  OPTIMAL_LIMIT_IOC;

  public static OkexOrderFlags from(OkxOrderFlags value) {
    return value == null ? null : OkexOrderFlags.valueOf(value.name());
  }

  public OkxOrderFlags to() {
    return OkxOrderFlags.valueOf(name());
  }
}
