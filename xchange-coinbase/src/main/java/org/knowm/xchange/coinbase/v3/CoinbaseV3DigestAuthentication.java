package org.knowm.xchange.coinbase.v3;

import java.util.function.Supplier;
import org.knowm.xchange.ExchangeSpecification;
import si.mazi.rescu.ParamsDigest;

/**
 * Default {@link CoinbaseV3Authentication} backed by {@link CoinbaseV3Digest}.
 *
 * <p>Both the REST digest and the WebSocket JWT supplier share one validated ES256 key pair and
 * the standard CDP claims ({@code kid}/{@code iss=cdp}/{@code sub}/{@code nbf}/{@code exp}, with a
 * {@code uri} claim for REST requests only).
 */
public final class CoinbaseV3DigestAuthentication implements CoinbaseV3Authentication {

  private final CoinbaseV3Digest digest;

  private CoinbaseV3DigestAuthentication(CoinbaseV3Digest digest) {
    this.digest = digest;
  }

  @Override
  public ParamsDigest restDigest() {
    return digest;
  }

  @Override
  public Supplier<String> websocketJwtSupplier() {
    return digest::generateWebsocketJwt;
  }

  /**
   * Creates the component from an exchange specification, validating the key material.
   *
   * @return null when API credentials are absent
   * @throws IllegalStateException when credentials are present but invalid; the message is
   *     sanitized and contains no key material
   */
  static CoinbaseV3Authentication from(ExchangeSpecification specification) {
    String keyName = specification.getApiKey();
    String secretKey = specification.getSecretKey();
    if (keyName == null || secretKey == null) {
      return null;
    }
    try {
      return new CoinbaseV3DigestAuthentication(
          CoinbaseV3Digest.createInstance(keyName, secretKey));
    } catch (IllegalStateException invalidKeyMaterial) {
      throw new IllegalStateException(
          "Coinbase v3 API credentials are invalid; check the CDP key name and PEM-encoded EC "
              + "private key (P-256) and recreate the exchange specification",
          invalidKeyMaterial);
    }
  }
}
