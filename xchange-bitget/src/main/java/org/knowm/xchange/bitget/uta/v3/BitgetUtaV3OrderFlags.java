package org.knowm.xchange.bitget.uta.v3;

import org.knowm.xchange.dto.Order;

/**
 * Exchange-specific placement flags for UTA v3 orders.
 *
 * <p>Bitget futures serialization defaults to one-way position mode with cross margin. Accounts
 * configured for hedge mode or isolated positions must opt out per order; XChange core carries no
 * margin/position-mode fields, so callers set these flags on the {@link Order} they place and the
 * adapter derives the wire {@code holdMode}/{@code marginMode} values from them.
 */
public enum BitgetUtaV3OrderFlags implements Order.IOrderFlags {
  /** Isolated margin mode (wire {@code marginMode=isolated}); default is crossed. */
  ISOLATED_MARGIN,
  /** Two-way (hedge) position mode (wire {@code holdMode=hedge_mode}); default is one-way. */
  HEDGE_MODE,
  /**
   * Margin order on a spot-family instrument (wire {@code category=margin}). Spot instruments
   * default to {@code category=spot}; this flag lifts them to the margin category. Ignored for
   * futures instruments, which keep their derivative category.
   */
  MARGIN
}
