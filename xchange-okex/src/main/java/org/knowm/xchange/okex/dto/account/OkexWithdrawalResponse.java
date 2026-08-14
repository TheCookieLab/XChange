package org.knowm.xchange.okex.dto.account;

import com.fasterxml.jackson.annotation.JsonCreator;
import org.knowm.xchange.okx.dto.account.OkxWithdrawalResponse;

/**
 * @deprecated use {@link org.knowm.xchange.okx.dto.account.OkxWithdrawalResponse} instead.
 */
@Deprecated
public class OkexWithdrawalResponse {

  private final OkxWithdrawalResponse delegate;

  /**
   * Public no-argument constructor retained for source and binary compatibility with pre-rename
   * clients (previously Lombok {@code @NoArgsConstructor}).
   */
  public OkexWithdrawalResponse() {
    this(new OkxWithdrawalResponse());
  }

  @JsonCreator
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
