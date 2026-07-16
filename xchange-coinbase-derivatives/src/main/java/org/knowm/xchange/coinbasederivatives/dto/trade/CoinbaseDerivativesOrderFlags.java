package org.knowm.xchange.coinbasederivatives.dto.trade;

import org.knowm.xchange.dto.Order.IOrderFlags;

/** Coinbase derivatives order behavior flags. */
public enum CoinbaseDerivativesOrderFlags implements IOrderFlags {
  REDUCE_ONLY,
  POST_ONLY
}
