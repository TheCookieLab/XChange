package org.knowm.xchange.okex.dto.account;

import com.fasterxml.jackson.annotation.JsonCreator;
import org.knowm.xchange.okx.dto.account.OkxAssetBalance;

/**
 * @deprecated use {@link org.knowm.xchange.okx.dto.account.OkxAssetBalance} instead.
 */
@Deprecated
public class OkexAssetBalance {

  private final OkxAssetBalance delegate;

  /**
   * Public no-argument constructor retained for source and binary compatibility with pre-rename
   * clients (previously Lombok {@code @NoArgsConstructor}).
   */
  public OkexAssetBalance() {
    this(new OkxAssetBalance());
  }

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
