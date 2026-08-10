package org.knowm.xchange.binance.error;

/**
 * Retry classification for Binance failures, driving resilience policy.
 *
 * <p>The contract is conservative: an operation is replay-safe only when its provider semantics
 * and request identity make replay safe (reads, queries, cancellations). Order placement is
 * {@link #RECONCILE}: after an ambiguous transport result the library may run a bounded
 * reconciliation query by client order ID but must never blindly resubmit.
 */
public enum BinanceRetryClassification {

  /** Read or idempotent operation; may be retried with bounded jittered backoff. */
  REPLAY_SAFE,

  /** Provider rejection; retrying cannot succeed without a request or state change. */
  NO_RETRY,

  /** Non-idempotent placement with ambiguous outcome; reconcile by client order ID, never replay. */
  RECONCILE,

  /** Rate-limit or weight exhaustion, or a ban; honor {@code Retry-After} when present. */
  RATE_LIMITED,

  /** Transient transport or server-side condition; retry only when the operation is replay-safe. */
  TRANSIENT,

  /** Credentials or authorization rejected; retrying without fixing them is futile. */
  AUTHENTICATION
}
