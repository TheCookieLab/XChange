package org.knowm.xchange.okex.dto.marketdata;

import org.knowm.xchange.okx.dto.marketdata.OkxCurrency;

/**
 * @deprecated use {@link org.knowm.xchange.okx.dto.marketdata.OkxCurrency} instead.
 */
@Deprecated
public class OkexCurrency {

  private final OkxCurrency delegate;

  public OkexCurrency(OkxCurrency delegate) {
    this.delegate = delegate;
  }

  /** Returns the wrapped canonical DTO. */
  public OkxCurrency to() {
    return delegate;
  }

  public String getCurrency() {
    return delegate.getCurrency();
  }

  public String getName() {
    return delegate.getName();
  }

  public String getChain() {
    return delegate.getChain();
  }

  public boolean isCanDep() {
    return delegate.isCanDep();
  }

  public boolean isCanWd() {
    return delegate.isCanWd();
  }

  public boolean isCanInternal() {
    return delegate.isCanInternal();
  }

  public String getMinWd() {
    return delegate.getMinWd();
  }

  public String getMinFee() {
    return delegate.getMinFee();
  }

  public String getMaxFee() {
    return delegate.getMaxFee();
  }
}
