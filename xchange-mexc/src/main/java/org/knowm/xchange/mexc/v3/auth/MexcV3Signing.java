package org.knowm.xchange.mexc.v3.auth;

import jakarta.ws.rs.QueryParam;
import si.mazi.rescu.Params;
import si.mazi.rescu.ParamsDigest;
import si.mazi.rescu.RestInvocation;

/**
 * Central signing support for the MEXC Spot v3 integration.
 *
 * <p>Every signed request carries the canonical payload: the query string (excluding the {@code
 * signature} parameter itself) concatenated with the request body. MEXC accepts signed parameters
 * in the query string for every HTTP method, so adapters put all request parameters (including
 * {@code timestamp} and {@code recvWindow}) in the query string and keep the payload assembly in
 * this single location for deterministic signature fixtures.
 */
public final class MexcV3Signing {

  /** Query parameter carrying the request signature. */
  public static final String SIGNATURE_PARAM = "signature";

  private MexcV3Signing() {}

  /**
   * The canonical payload a MEXC Spot v3 request signature covers.
   *
   * @return query string (without {@code signature}) plus request body, concatenated without a
   *     separator.
   */
  public static String signingPayload(RestInvocation restInvocation) {
    final Params p = Params.of();
    restInvocation.getParamsMap().get(QueryParam.class).asHttpHeaders().entrySet().stream()
        .filter(e -> !SIGNATURE_PARAM.equals(e.getKey()))
        .forEach(e -> p.add(e.getKey(), e.getValue()));
    return p.asQueryString() + restInvocation.getRequestBody();
  }

  /**
   * Creates the HMAC-SHA256 {@link ParamsDigest} for the given secret key, or {@code null} when no
   * secret key is available.
   */
  public static ParamsDigest createDigest(String secretKey) {
    return secretKey == null ? null : MexcV3HmacDigest.createInstance(secretKey);
  }
}
