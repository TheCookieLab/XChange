package org.knowm.xchange.cryptocom.dto;

/**
 * Classification of whether and how a failed Crypto.com call may be retried safely. {@link
 * #NONE} means a retry cannot change the outcome (e.g. a rejected order); {@link
 * #RATE_LIMIT} and {@link #TRANSIENT} may be retried with backoff; {@link #AUTH} requires
 * fixing credentials/nonce state first.
 */
public enum CryptoComRetryClass {
  /** Retrying is pointless or unsafe (e.g. ambiguous order placement). */
  NONE,
  /** Provider rate limit — retry only with backoff. */
  RATE_LIMIT,
  /** Authentication/nonce problem — fix credentials or clock before retrying. */
  AUTH,
  /** Transient transport failure (timeout, connection reset). */
  TRANSIENT,
  /** Request rejected by the provider for a business reason. */
  REJECTED
}