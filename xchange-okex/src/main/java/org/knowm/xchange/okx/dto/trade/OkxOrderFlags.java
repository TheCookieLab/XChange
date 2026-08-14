package org.knowm.xchange.okx.dto.trade;

import org.knowm.xchange.dto.Order;

public enum OkxOrderFlags implements Order.IOrderFlags {
  POST_ONLY,
  REDUCE_ONLY,
  OPTIMAL_LIMIT_IOC
}
