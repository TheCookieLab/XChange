package org.knowm.xchange.mexc.service;

import org.knowm.xchange.Exchange;

/**
 * @deprecated MEXC Spot v2 ({@code /open/api/v2}) is frozen for compatibility; use the Spot v3
 *     implementation in {@code org.knowm.xchange.mexc.v3} instead. See the xchange-mexc README
 *     migration notes for the removal policy.
 */
@Deprecated
public class MEXCMarketDataServiceRaw extends MEXCBaseService {
  public MEXCMarketDataServiceRaw(Exchange exchange) {
    super(exchange);
  }
}
