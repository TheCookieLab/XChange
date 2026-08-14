package org.knowm.xchange.okex.dto.account;

import com.fasterxml.jackson.annotation.JsonCreator;
import org.knowm.xchange.okx.dto.account.OkxDepositAddress;

/**
 * @deprecated use {@link org.knowm.xchange.okx.dto.account.OkxDepositAddress} instead.
 */
@Deprecated
public class OkexDepositAddress {

  private final OkxDepositAddress delegate;

  /**
   * Public no-argument constructor retained for source and binary compatibility with pre-rename
   * clients (previously Lombok {@code @NoArgsConstructor}).
   */
  public OkexDepositAddress() {
    this(new OkxDepositAddress());
  }

  @JsonCreator
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
