package org.knowm.xchange.cryptocom.dto.trade;

/** How an order-placement call reached its outcome. */
public enum CryptoComPlacementOutcome {
  /** The provider acknowledged the placement synchronously. */
  ACKED,

  /** The provider did not answer synchronously, but a later query proved the order exists. */
  RECONCILED,

  /**
   * The provider did not answer synchronously, and later open-orders and recent-history queries
   * completed within the bounded reconciliation window without surfacing the order. This is a
   * deterministic absent outcome for that window, not an authoritative provider rejection: a
   * not-yet-visible order may still exist, so callers must verify order state manually before
   * re-submitting.
   */
  NOT_FOUND
}