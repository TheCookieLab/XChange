package org.knowm.xchange.cryptocom.dto.trade;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Typed result of an order placement. Always carries the envelope {@code requestId} of the call
 * that placed (or attempted to place) the order, the provider {@code orderId} when known, the
 * client reference ({@code clientOid}) verbatim, and the outcome: {@link
 * CryptoComPlacementOutcome#ACKED} for a synchronous ack, {@link CryptoComPlacementOutcome#RECONCILED}
 * when the order was recovered by querying after a transport failure, and {@link
 * CryptoComPlacementOutcome#NOT_FOUND} when the order is provably absent. An ambiguous placement
 * (reconciliation itself failed) raises {@link
 * org.knowm.xchange.cryptocom.dto.CryptoComUnknownOrderOutcomeException} instead of returning a
 * result — the placement is never re-sent automatically.
 */
@Getter
@AllArgsConstructor
public class CryptoComOrderPlacementResult {

  private final CryptoComPlacementOutcome outcome;
  private final long requestId;
  private final String orderId;
  private final String clientOid;
}