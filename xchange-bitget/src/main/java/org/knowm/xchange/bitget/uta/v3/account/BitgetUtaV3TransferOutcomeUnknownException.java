package org.knowm.xchange.bitget.uta.v3.account;

import java.io.IOException;

/**
 * Signals that a transfer may have been accepted by the exchange even though no HTTP response was
 * received (read timeout, connection reset).
 *
 * <p>{@code POST /api/v3/account/transfer} is asynchronous and {@code clientOid} is an
 * idempotency-scoped key, so blindly retrying a transfer whose response was lost can execute it
 * twice. Callers must verify balances to determine the true outcome, and may retry idempotently by
 * reusing {@link #getClientOid()} on the new request. The message never carries key material.
 *
 * @since 5.1.0
 */
public class BitgetUtaV3TransferOutcomeUnknownException extends IOException {

  private static final long serialVersionUID = 1L;

  private final String clientOid;

  public BitgetUtaV3TransferOutcomeUnknownException(String clientOid, IOException cause) {
    super(
        "Bitget UTA v3 transfer outcome is unknown: the request may have been accepted before the"
            + " response was lost (transport failure"
            + (clientOid == null ? "" : ", clientOid=" + clientOid)
            + "); verify balances before retrying, and reuse the clientOid to retry idempotently"
            + (cause.getMessage() == null ? "" : ": " + cause.getMessage()),
        cause);
    this.clientOid = clientOid;
  }

  /** The idempotency key that was sent with the transfer; reuse it on an idempotent retry. */
  public String getClientOid() {
    return clientOid;
  }
}
