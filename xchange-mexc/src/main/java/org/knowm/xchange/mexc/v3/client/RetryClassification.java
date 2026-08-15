package org.knowm.xchange.mexc.v3.client;

/** Describes whether a failed MEXC Spot v3 operation is safe for callers to replay. */
public enum RetryClassification {
  /**
   * Request failed at the transport layer or returned a transport-classified provider failure
   * (gateway/service 5xx); the request may or may not have been applied.
   */
  TRANSPORT,
  /** Provider rejected the credentials or signature. */
  AUTHENTICATION,
  /** Provider rate limiting applied (weight or IP limits). */
  RATE_LIMITED,
  /** Provider rejected the request outright; replay cannot succeed unchanged. */
  PERMANENT,
  /** The request may or may not have been applied; callers must reconcile, never replay blindly. */
  AMBIGUOUS
}
