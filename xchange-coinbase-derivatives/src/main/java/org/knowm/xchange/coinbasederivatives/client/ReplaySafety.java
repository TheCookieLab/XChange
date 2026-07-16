package org.knowm.xchange.coinbasederivatives.client;

/** Replay policy attached to a private request. */
public enum ReplaySafety {
  READ,
  IDEMPOTENT_CANCELLATION,
  PLACEMENT
}
