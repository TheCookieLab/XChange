package org.knowm.xchange.mexc.v3;

import org.knowm.xchange.dto.Order.IOrderFlags;

/**
 * MEXC Spot v3 order flags.
 *
 * <p>{@link #QUOTE_ORDER_QTY} makes a market BUY quote-denominated: the order amount is spent in
 * the quote asset ({@code quoteOrderQty}) instead of the default XChange contract where {@link
 * org.knowm.xchange.dto.trade.MarketOrder#getOriginalAmount()} is the base quantity ({@code
 * quantity}). MEXC only prices market SELL orders in base quantity, so the flag is rejected on
 * asks.
 */
public enum MexcV3OrderFlags implements IOrderFlags {
  QUOTE_ORDER_QTY
}
