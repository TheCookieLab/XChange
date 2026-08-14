package org.knowm.xchange.okex.dto.trade;

import org.knowm.xchange.okx.dto.trade.OkxAlgoOrderResponse;

/**
 * @deprecated use {@link org.knowm.xchange.okx.dto.trade.OkxAlgoOrderResponse} instead.
 */
@Deprecated
public class OkexAlgoOrderResponse {

  private final OkxAlgoOrderResponse delegate;

  public OkexAlgoOrderResponse(OkxAlgoOrderResponse delegate) {
    this.delegate = delegate;
  }

  public String getCode() {
    return delegate.getCode();
  }

  public String getMessage() {
    return delegate.getMessage();
  }

  public String getClientOrderId() {
    return delegate.getClientOrderId();
  }

  public String getOrderId() {
    return delegate.getOrderId();
  }

  public String getAlgoId() {
    return delegate.getAlgoId();
  }

  public String getOrderTag() {
    return delegate.getOrderTag();
  }
}
