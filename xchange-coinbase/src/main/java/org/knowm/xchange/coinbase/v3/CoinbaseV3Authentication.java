package org.knowm.xchange.coinbase.v3;

import java.util.function.Supplier;
import org.knowm.xchange.ExchangeSpecification;
import si.mazi.rescu.ParamsDigest;

/**
 * Shared typed Coinbase Advanced Trade v3 authentication contract.
 *
 * <p>One component exposes both the REST request digest and the WebSocket JWT supplier from the
 * same key material and claims logic, replacing the reflective helper fallback and the untyped
 * {@code Supplier<String>} duck-typing previously used across the module boundary.
 *
 * <p>Key material is validated when the component is created; failures surface as sanitized {@link
 * IllegalStateException}s that never contain the private key or its PEM text.
 *
 * @since 1.0
 */
public interface CoinbaseV3Authentication {

  /** Returns the digest used for REST {@code Authorization: Bearer} headers. */
  ParamsDigest restDigest();

  /** Returns a supplier of purpose-correct WebSocket JWTs (no {@code uri} claim). */
  Supplier<String> websocketJwtSupplier();

  /**
   * Creates an authentication component from an exchange specification.
   *
   * @return the component, or null when no API credentials are configured (public-only usage)
   * @throws IllegalStateException when credentials are present but invalid; the message is
   *     sanitized and never contains key material
   */
  static CoinbaseV3Authentication from(ExchangeSpecification specification) {
    return CoinbaseV3DigestAuthentication.from(specification);
  }
}
