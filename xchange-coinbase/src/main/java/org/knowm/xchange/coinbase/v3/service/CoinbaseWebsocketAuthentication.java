package org.knowm.xchange.coinbase.v3.service;

import java.util.Objects;
import java.util.function.Supplier;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.coinbase.v3.CoinbaseV3Digest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility for creating JWT suppliers that can be used by the streaming module when authenticating
 * with Coinbase Advanced Trade WebSocket endpoints.
 */
public final class CoinbaseWebsocketAuthentication {

  private static final Logger LOG =
      LoggerFactory.getLogger(CoinbaseWebsocketAuthentication.class);

  private CoinbaseWebsocketAuthentication() {}

  /**
   * Creates a {@link Supplier} that generates WebSocket JWT tokens on demand using the credentials
   * present in the provided {@link ExchangeSpecification}.
   *
   * <p>The supplier returns {@code null} when API credentials are missing or token generation
   * fails. Callers should handle the {@code null} case by restricting access to private channels.
   *
   * @param specification exchange specification containing API key and secret key
   * @return supplier that yields JWT strings or {@code null} if credentials are unavailable
   */
  public static Supplier<String> websocketJwtSupplier(ExchangeSpecification specification) {
    Objects.requireNonNull(specification, "specification");

    CoinbaseV3Digest digest =
        CoinbaseV3Digest.createInstance(specification.getApiKey(), specification.getSecretKey());

    if (digest == null) {
      LOG.debug("Coinbase Advanced Trade WebSocket JWT supplier unavailable - missing credentials");
      return () -> null;
    }

    return () -> {
      try {
        return digest.generateWebsocketJwt();
      } catch (Exception ex) {
        LOG.warn("Failed to generate Coinbase Advanced Trade WebSocket JWT", ex);
        return null;
      }
    };
  }
}
