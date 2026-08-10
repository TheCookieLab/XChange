package org.knowm.xchange.binance.auth;

import jakarta.ws.rs.QueryParam;
import org.knowm.xchange.binance.config.BinanceKeyAlgorithm;
import org.knowm.xchange.binance.service.BinanceED25519Digest;
import org.knowm.xchange.binance.service.BinanceHmacDigest;
import si.mazi.rescu.Params;
import si.mazi.rescu.ParamsDigest;
import si.mazi.rescu.RestInvocation;

/**
 * Central signing support for the Binance integration.
 *
 * <p>All key algorithms sign the same canonical payload: the query string (excluding the {@code
 * signature} parameter itself) for GET/DELETE, plus the request body for POST/PUT. Keeping the
 * payload assembly in one place guarantees every algorithm signs identical bytes and gives a
 * single location for deterministic signature fixtures.
 */
public final class BinanceSigning {

  /** Query parameter carrying the request signature. */
  public static final String SIGNATURE_PARAM = "signature";

  private BinanceSigning() {}

  /**
   * The canonical payload a Binance request signature covers.
   *
   * @return query string (without {@code signature}) for GET/DELETE; query string plus request
   *     body for POST/PUT.
   */
  public static String signingPayload(RestInvocation restInvocation) {
    final Params p = Params.of();
    restInvocation.getParamsMap().get(QueryParam.class).asHttpHeaders().entrySet().stream()
        .filter(e -> !SIGNATURE_PARAM.equals(e.getKey()))
        .forEach(e -> p.add(e.getKey(), e.getValue()));
    final String query = p.asQueryString();
    switch (restInvocation.getHttpMethod()) {
      case "GET":
      case "DELETE":
        return query;
      case "POST":
      case "PUT":
        return query + restInvocation.getRequestBody();
      default:
        throw new IllegalStateException(
            "Unsupported HTTP method for Binance request signing: "
                + restInvocation.getHttpMethod());
    }
  }

  /**
   * Creates the {@link ParamsDigest} for the configured key algorithm, or {@code null} when no
   * secret key is available.
   *
   * @throws IllegalArgumentException for an unsupported algorithm value.
   */
  public static ParamsDigest createDigest(BinanceKeyAlgorithm algorithm, String secretKey) {
    if (secretKey == null) {
      return null;
    }
    switch (algorithm) {
      case HMAC_SHA_256:
        return BinanceHmacDigest.createInstance(secretKey);
      case RSA:
        return BinanceRsaDigest.createInstance(secretKey);
      case ED25519:
        return BinanceED25519Digest.createInstance(secretKey);
      default:
        throw new IllegalArgumentException(
            "Unsupported Binance key algorithm: " + algorithm + ".");
    }
  }
}
