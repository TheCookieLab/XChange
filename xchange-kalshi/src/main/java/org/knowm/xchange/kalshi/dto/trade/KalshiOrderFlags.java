package org.knowm.xchange.kalshi.dto.trade;

import org.knowm.xchange.dto.Order.IOrderFlags;

/** Kalshi-specific order flags accepted by {@code KalshiAdapters.toCreateOrderRequest}. */
public enum KalshiOrderFlags implements IOrderFlags {

  /** Map to {@code post_only} on the V2 create-order request. */
  POST_ONLY,

  /** Map to {@code cancel_order_on_pause}. */
  CANCEL_ON_PAUSE,

  /** Map to {@code reduce_only}. */
  REDUCE_ONLY,

  /** Use time-in-force {@code immediate_or_cancel} instead of the default {@code good_till_canceled}. */
  IMMEDIATE_OR_CANCEL,

  /** Use time-in-force {@code fill_or_kill}. */
  FILL_OR_KILL,

  /**
   * Explicit request to trade the NO leg. Rejected by the adapter with
   * {@code NotAvailableFromExchangeException}; NO exposure is never silently synthesized by
   * complementing a YES order.
   */
  SIDE_NO
}
