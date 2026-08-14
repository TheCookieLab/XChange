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

  /**
   * Retained legacy value constructor; builds the canonical DTO internally.
   *
   * @deprecated use {@link org.knowm.xchange.okx.dto.marketdata.OkxPublicOrder} instead.
   */
  @Deprecated
  public OkexPublicOrder(
      BigDecimal price, BigDecimal volume, Integer liquidatedOrders, Integer activeOrders) {
    this(new OkxPublicOrder(price, volume, liquidatedOrders, activeOrders));
  }

  /** Returns the wrapped canonical DTO. */
  public OkxPublicOrder to() {
    return delegate;
  }

  public BigDecimal getPrice() {
    return delegate.getPrice();
  }

  public BigDecimal getVolume() {
    return delegate.getVolume();
  }
}
