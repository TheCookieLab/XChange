package org.knowm.xchange.okex.dto.account;

import org.knowm.xchange.okx.dto.account.OkxDepositAddress;

/**
 * @deprecated use {@link org.knowm.xchange.okx.dto.account.OkxDepositAddress} instead.
 */
@Deprecated
public class OkexDepositAddress {

  private final OkxDepositAddress delegate;

  public OkexDepositAddress(OkxDepositAddress delegate) {
    this.delegate = delegate;
  }

  public String getAddress() {
    return delegate.getAddress();
  }

  public String getTag() {
    return delegate.getTag();
  }

  public String getMemo() {
    return delegate.getMemo();
  }

  public String getPaymentId() {
    return delegate.getPaymentId();
  }

  public String getCurrency() {
    return delegate.getCurrency();
  }

  public String getChain() {
    return delegate.getChain();
  }

  public String getTo() {
    return delegate.getTo();
  }

  public String getSelected() {
    return delegate.getSelected();
  }

  public String getContactAddress() {
    return delegate.getContactAddress();
  }
}
