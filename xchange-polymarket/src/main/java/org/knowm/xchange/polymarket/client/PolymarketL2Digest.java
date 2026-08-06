package org.knowm.xchange.polymarket.client;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.knowm.xchange.exceptions.ExchangeSecurityException;
import si.mazi.rescu.ParamsDigest;
import si.mazi.rescu.RestInvocation;

/**
 * Polymarket L2 signer: HMAC-SHA256 over {@code timestamp + method + requestPath + body} using
 * the base64-decoded API secret, emitted as url-safe base64 with padding. The secret never
 * appears in exceptions or logs.
 */
public final class PolymarketL2Digest implements ParamsDigest {

  /** Header whose resolved value anchors the signed payload. */
  public static final String TIMESTAMP_HEADER = "POLY_TIMESTAMP";

  private final byte[] secret;

  private PolymarketL2Digest(byte[] secret) {
    this.secret = secret;
  }

  /**
   * @param base64Secret L2 API secret as issued by Polymarket (base64, url-safe variants
   *     accepted), or {@code null}/blank to disable signing
   * @return the digest, or {@code null} when no secret was supplied
   */
  public static PolymarketL2Digest createInstance(String base64Secret) {
    if (base64Secret == null || base64Secret.isBlank()) {
      return null;
    }
    return new PolymarketL2Digest(decodeSecret(base64Secret));
  }

  static byte[] decodeSecret(String base64Secret) {
    try {
      return Base64.getUrlDecoder().decode(base64Secret);
    } catch (IllegalArgumentException urlFailure) {
      try {
        return Base64.getDecoder().decode(base64Secret);
      } catch (IllegalArgumentException standardFailure) {
        throw new ExchangeSecurityException(
            "Polymarket L2 secret is not valid base64", standardFailure);
      }
    }
  }

  @Override
  public String digestParams(RestInvocation restInvocation) {
    String timestamp = restInvocation.getHttpHeadersFromParams().get(TIMESTAMP_HEADER);
    StringBuilder payload =
        new StringBuilder()
            .append(timestamp)
            .append(restInvocation.getHttpMethod())
            .append("/")
            .append(restInvocation.getPath());
    String body = restInvocation.getRequestBody();
    if (body != null) {
      payload.append(body);
    }
    return sign(payload.toString());
  }

  /** HMAC-SHA256 of the canonical payload; package-private seam for deterministic tests. */
  String sign(String payload) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret, "HmacSHA256"));
      return Base64.getUrlEncoder()
          .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException e) {
      throw new ExchangeSecurityException("Polymarket L2 request signing failed", e);
    }
  }
}
