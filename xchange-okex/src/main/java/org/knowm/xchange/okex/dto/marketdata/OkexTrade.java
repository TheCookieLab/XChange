package org.knowm.xchange.okex.dto.marketdata;

import java.math.BigDecimal;
import java.util.Date;
import org.knowm.xchange.okx.dto.marketdata.OkxTrade;

/**
 * @deprecated use {@link org.knowm.xchange.okx.dto.marketdata.OkxTrade} instead.
 */
@Deprecated
public class OkexTrade {

  private final OkxTrade delegate;

  public OkexTrade(OkxTrade delegate) {
    this.delegate = delegate;
  }

  /** Returns the wrapped canonical DTO. */
  public OkxTrade to() {
    return delegate;
  }

  public String getTradeId() {
    return delegate.getTradeId();
  }

  public String getInstId() {
    return delegate.getInstId();
  }

  public BigDecimal getPx() {
    return delegate.getPx();
  }

  public BigDecimal getSz() {
    return delegate.getSz();
  }

  public String getSide() {
    return delegate.getSide();
  }

  public Date getTs() {
    return delegate.getTs();
  }
}
