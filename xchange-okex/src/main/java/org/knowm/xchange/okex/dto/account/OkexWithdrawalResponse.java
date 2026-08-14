package org.knowm.xchange.okex.dto.account;

import org.knowm.xchange.okx.dto.account.OkxWithdrawalResponse;

/**
 * @deprecated use {@link org.knowm.xchange.okx.dto.account.OkxWithdrawalResponse} instead.
 */
@Deprecated
public class OkexWithdrawalResponse {

  private final OkxWithdrawalResponse delegate;

  public OkexWithdrawalResponse(OkxWithdrawalResponse delegate) {
    this.delegate = delegate;
  }

  public String getCurrency() {
    return delegate.getCurrency();
  }

  public String getAmount() {
    return delegate.getAmount();
  }

  public String getChain() {
    return delegate.getChain();
  }

  public String getClientId() {
    return delegate.getClientId();
  }

  public String getWithdrawalId() {
    return delegate.getWithdrawalId();
  }
}
