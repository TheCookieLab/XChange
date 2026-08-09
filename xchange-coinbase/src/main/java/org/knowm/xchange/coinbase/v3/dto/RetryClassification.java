package org.knowm.xchange.coinbase.v3.dto;

/**
 * Whether a failed Coinbase Advanced Trade operation is safe for callers to replay.
 *
 * <p>Mirrors the classification used by the Coinbase Derivatives gateway module so both
 * integrations share one retry vocabulary. Only {@code READ} and {@code IDEMPOTENT_CANCELLATION}
 * operations may be retried automatically; {@code AMBIGUOUS} results must never be replayed
 * without explicit reconciliation.
 *
 * @since 1.0
 */
public enum RetryClassification {
  /** Credentials or authorization were rejected; retrying without fixing auth is pointless. */
  AUTHENTICATION,
  /** A transient provider or transport failure that is safe to retry for reads. */
  TRANSIENT,
  /** The provider rate-limited the caller; honor rate metadata before retrying reads. */
  RATE_CREDIT,
  /** The request was rejected deterministically; retrying will not change the outcome. */
  PERMANENT,
  /** The outcome is unknown (for example a network failure after the request was sent). */
  AMBIGUOUS
}
