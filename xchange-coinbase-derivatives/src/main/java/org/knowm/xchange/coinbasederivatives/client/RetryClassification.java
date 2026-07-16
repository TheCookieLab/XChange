package org.knowm.xchange.coinbasederivatives.client;

/** Describes whether a failed JSON-RPC operation is safe for callers to replay. */
public enum RetryClassification {
  AUTHENTICATION,
  TRANSIENT,
  RATE_CREDIT,
  PERMANENT,
  AMBIGUOUS
}
