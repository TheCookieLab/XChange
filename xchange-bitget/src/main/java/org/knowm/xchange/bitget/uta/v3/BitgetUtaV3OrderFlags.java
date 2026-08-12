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
   * Long position side for hedge-mode orders (wire {@code posSide=long}). Required together with
   * {@link #HEDGE_MODE} on futures: in two-way mode a buy/sell alone is ambiguous, so Bitget
   * requires an explicit position side. Ignored outside hedge mode.
   */
  POS_SIDE_LONG,
  /**
   * Short position side for hedge-mode orders (wire {@code posSide=short}). Required together with
   * {@link #HEDGE_MODE} on futures; see {@link #POS_SIDE_LONG}.
   */
  POS_SIDE_SHORT,
  /**
   * Margin order on a spot-family instrument (wire {@code category=margin}). Spot instruments
   * default to {@code category=spot}; this flag lifts them to the margin category. Ignored for
   * futures instruments, which keep their derivative category.
   */
  MARGIN,
  /**
   * Reduce-only futures order (wire {@code reduceOnly=yes}): the order can only decrease an
   * existing position and never opens exposure on the opposite side. A one-way futures caller
   * closing a position with a quantity larger than the remaining position is otherwise free to
   * flip exposure. Ignored outside derivative instruments.
   */
  REDUCE_ONLY,
  /**
   * Spot/margin market-buy order whose {@link MarketOrder#getOriginalAmount()} is the quote-coin
   * spend, not the base-coin quantity.
   *
   * <p>The v3 place-order endpoint accepts exactly one size parameter, the required {@code qty};
   * per the official docs {@code qty} is the base-coin quantity for limit and market-sell orders
   * and the quote-coin spend for market-buy orders on spot/margin categories. XChange's {@link
   * MarketOrder#getOriginalAmount()} is always base-denominated, so a spot/margin market buy
   * without this flag is rejected at adaptation — sending the base amount as quote spend would
   * place an order roughly a price-factor too small. Set this flag to declare that {@code
   * originalAmount} carries the quote-coin spend. Futures market orders are unaffected (their
   * {@code qty} is always the base contract count).
   */
  MARKET_BUY_QUOTE_AMOUNT
}
