package org.knowm.xchange.mexc.v3.client;

/** Replay policy attached to a MEXC Spot v3 request. */
public enum ReplaySafety {
  /** Read-only request; safe to replay without side effects. */
  READ,
  /** Idempotent cancellation; safe to replay. */
  IDEMPOTENT_CANCELLATION,
  /** Order placement; never replay on an ambiguous outcome, reconcile first. */
  PLACEMENT
}
