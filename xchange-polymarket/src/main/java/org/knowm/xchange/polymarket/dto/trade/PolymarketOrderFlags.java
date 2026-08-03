package org.knowm.xchange.polymarket.dto.trade;

import org.knowm.xchange.dto.Order.IOrderFlags;

/** Polymarket-specific order flags accepted by {@code PolymarketAdapters.toSignedOrder}. */
public enum PolymarketOrderFlags implements IOrderFlags {

  /** Map to {@code postOnly} on the create-order request. */
  POST_ONLY,

  /** Use order type {@code FOK} instead of the default {@code GTC}. */
  FILL_OR_KILL,

  /** Use order type {@code FAK} (immediate-or-cancel). */
  IMMEDIATE_OR_CANCEL
}
