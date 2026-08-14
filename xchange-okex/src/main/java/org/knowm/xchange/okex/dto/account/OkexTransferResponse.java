package org.knowm.xchange.okex.dto.account;

import org.knowm.xchange.okx.dto.account.OkxTransferResponse;

/**
 * @deprecated use {@link org.knowm.xchange.okx.dto.account.OkxTransferResponse} instead.
 */
@Deprecated
public class OkexTransferResponse {

  private final OkxTransferResponse delegate;

  public OkexTransferResponse(OkxTransferResponse delegate) {
    this.delegate = delegate;
  }

  public String getTransferId() {
    return delegate.getTransferId();
  }
}
