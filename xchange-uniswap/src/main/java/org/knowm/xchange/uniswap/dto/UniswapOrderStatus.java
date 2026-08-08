package org.knowm.xchange.uniswap.dto;

/** Lifecycle of an on-chain swap order. */
public enum UniswapOrderStatus {
  /** Broadcast accepted (or accepted-but-unconfirmed); receipt not yet observed. */
  PENDING,
  /** Receipt observed and successful; fills decoded from PoolManager logs. */
  MINED,
  /** Receipt observed with a failed status (revert). */
  REVERTED,
  /** Never confirmed: broadcast result was ambiguous and the node has no record. */
  UNKNOWN
}
