package org.knowm.xchange.okex.dto.account;

/**
 * @deprecated use {@link org.knowm.xchange.okx.dto.account.PiggyBalance} instead.
 */
@Deprecated
public class PiggyBalance {

  private final org.knowm.xchange.okx.dto.account.PiggyBalance delegate;

  public PiggyBalance(org.knowm.xchange.okx.dto.account.PiggyBalance delegate) {
    this.delegate = delegate;
  }

  public String getEarnings() {
    return delegate.getEarnings();
  }

  public String getCcy() {
    return delegate.getCcy();
  }

  public String getAmt() {
    return delegate.getAmt();
  }
}
