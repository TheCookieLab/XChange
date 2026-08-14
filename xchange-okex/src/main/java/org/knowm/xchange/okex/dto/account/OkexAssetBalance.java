package org.knowm.xchange.okex.dto.account;

import com.fasterxml.jackson.annotation.JsonCreator;
import org.knowm.xchange.okx.dto.account.OkxAssetBalance;

/**
 * @deprecated use {@link org.knowm.xchange.okx.dto.account.OkxAssetBalance} instead.
 */
@Deprecated
public class OkexAssetBalance {

  private final OkxAssetBalance delegate;

  @JsonCreator
  public OkexAssetBalance(OkxAssetBalance delegate) {
    this.delegate = delegate;
  }

  /** Returns the wrapped canonical DTO. */
  public OkxAssetBalance to() {
    return delegate;
  }

  public String getCurrency() {
    return delegate.getCurrency();
  }

  public String getBalance() {
    return delegate.getBalance();
  }

  public String getAvailableBalance() {
    return delegate.getAvailableBalance();
  }

  public String getFrozenBalance() {
    return delegate.getFrozenBalance();
  }
}
