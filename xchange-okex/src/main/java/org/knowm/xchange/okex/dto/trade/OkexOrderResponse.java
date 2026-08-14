package org.knowm.xchange.okex.dto.trade;

import org.knowm.xchange.okx.dto.trade.OkxOrderResponse;

/**
 * @deprecated use {@link org.knowm.xchange.okx.dto.trade.OkxOrderResponse} instead.
 */
@Deprecated
public class OkexOrderResponse {

  private final OkxOrderResponse delegate;

  public OkexOrderResponse(OkxOrderResponse delegate) {
    this.delegate = delegate;
  }

  public String getOrderId() {
    return delegate.getOrderId();
  }

  public String getClientOrderId() {
    return delegate.getClientOrderId();
  }

  public String getOrderTag() {
    return delegate.getOrderTag();
  }

  public String getCode() {
    return delegate.getCode();
  }

  public String getMessage() {
    return delegate.getMessage();
  }

  public Long getTs() {
    return delegate.getTs();
  }
}
