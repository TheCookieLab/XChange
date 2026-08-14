package org.knowm.xchange.okex.dto.marketdata;

import java.math.BigDecimal;
import org.knowm.xchange.okx.dto.marketdata.OkxPublicOrder;

/**
 * @deprecated use {@link org.knowm.xchange.okx.dto.marketdata.OkxPublicOrder} instead.
 */
@Deprecated
public class OkexPublicOrder {

  private final OkxPublicOrder delegate;

  public OkexPublicOrder(OkxPublicOrder delegate) {
    this.delegate = delegate;
  }

  public BigDecimal getPrice() {
    return delegate.getPrice();
  }

  public BigDecimal getVolume() {
    return delegate.getVolume();
  }
}
