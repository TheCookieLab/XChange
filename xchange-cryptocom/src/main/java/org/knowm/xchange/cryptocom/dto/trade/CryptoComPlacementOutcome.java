package org.knowm.xchange.cryptocom.dto.trade;

/** How an order-placement call reached its outcome. */
public enum CryptoComPlacementOutcome {
  /** The provider acknowledged the placement synchronously. */
  ACKED,

  /** The provider did not answer synchronously, but a later query proved the order exists. */
  RECONCILED,

  /** The provider did not answer synchronously and later queries prove the order was not taken. */
  NOT_FOUND
}