package org.knowm.xchange.okex.dto.subaccount;

import com.fasterxml.jackson.annotation.JsonCreator;
import org.knowm.xchange.okx.dto.subaccount.OkxSubAccountDetails;

/**
 * @deprecated use {@link org.knowm.xchange.okx.dto.subaccount.OkxSubAccountDetails} instead.
 */
@Deprecated
public class OkexSubAccountDetails {

  private final OkxSubAccountDetails delegate;

  @JsonCreator
  public OkexSubAccountDetails(OkxSubAccountDetails delegate) {
    this.delegate = delegate;
  }

  public String getEnable() {
    return delegate.getEnable();
  }

  public String getSubAcct() {
    return delegate.getSubAcct();
  }

  public String getLabel() {
    return delegate.getLabel();
  }

  public String getMobile() {
    return delegate.getMobile();
  }

  public String getGAuth() {
    return delegate.getGAuth();
  }

  public String getTs() {
    return delegate.getTs();
  }
}
