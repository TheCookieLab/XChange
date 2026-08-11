package org.knowm.xchange.bitget.uta.v3;

import org.knowm.xchange.bitget.uta.v3.common.BitgetUtaV3Exception;

/**
 * Structured unknown-outcome failure for non-replayable Bitget UTA v3 placement operations.
 *
 * <p>Raised when {@code trade/order} responds with an ambiguous provider code ({@code 40010}
 * partially placed/matched, {@code 40725} order may be placed, {@code 45001} order may be matched)
 * after the request was transmitted, so the server-side outcome is unknown. Callers must not
 * blindly replay the placement; they should reconcile by client/exchange order id through {@code
 * trade/order-info} or surface the ambiguity to the operator. The message carries the provider code
 * and the echoed {@code clientOid}, never key material.
 *
 * @since 5.1.0
 */
public class BitgetUtaV3UnknownOutcomeException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final String clientOid;
  private final String providerCode;

  public BitgetUtaV3UnknownOutcomeException(BitgetUtaV3Exception e, String clientOid) {
    super(
        "Bitget UTA v3 order outcome is unknown after transmission (code "
            + e.getCode()
            + (clientOid == null ? "" : ", clientOid=" + clientOid)
            + "); do not replay blindly, reconcile by order id via trade/order-info"
            + (e.getMessage() == null ? "" : ": " + e.getMessage()),
        e);
    this.clientOid = clientOid;
    this.providerCode = e.getCode();
  }

  /** The {@code clientOid} of the interrupted placement, when one was supplied. */
  public String getClientOid() {
    return clientOid;
  }

  /** The ambiguous provider code verbatim ({@code 40010}, {@code 40725} or {@code 45001}). */
  public String getProviderCode() {
    return providerCode;
  }
}
