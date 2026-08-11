package org.knowm.xchange.kucoin.uta.service;

import com.google.common.base.Strings;
import jakarta.ws.rs.HeaderParam;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import javax.crypto.Mac;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.service.BaseParamsDigest;
import si.mazi.rescu.RestInvocation;

/**
 * UTA request signing.
 *
 * <p>Signing rule per the official authentication documentation: the prehash is the concatenation
 * of the millisecond timestamp, the HTTP method in upper case, the endpoint path with query string,
 * and the JSON request body (empty for GET/DELETE); the signature is HMAC-SHA256 over that prehash
 * keyed with the API secret, Base64-encoded. The passphrase header is the passphrase HMAC-SHA256
 * keyed with the API secret, Base64-encoded. The path is signed unencoded.
 */
public class UtaDigest extends BaseParamsDigest {

  private String signature = "";

  private UtaDigest(byte[] secretKey) {
    super(secretKey, HMAC_SHA_256);
  }

  public static UtaDigest createInstance(String secretKey) {
    return Strings.isNullOrEmpty(secretKey)
        ? null
        : new UtaDigest(secretKey.getBytes(StandardCharsets.UTF_8));
  }

  @Override
  public String digestParams(RestInvocation restInvocation) {
    String pathWithQueryString =
        restInvocation.getInvocationUrl().replace(restInvocation.getBaseUrl(), "");
    String message =
        buildMessage(
            restInvocation
                .getParamValue(HeaderParam.class, UtaConstants.API_HEADER_TIMESTAMP)
                .toString(),
            restInvocation.getHttpMethod(),
            pathWithQueryString,
            restInvocation.getRequestBody());

    Mac mac256 = getMac();
    try {
      mac256.update(message.getBytes(StandardCharsets.UTF_8));
    } catch (Exception e) {
      throw new ExchangeException("Digest encoding exception", e);
    }
    signature = Base64.getEncoder().encodeToString(mac256.doFinal());
    return signature;
  }

  /**
   * Signs a prehash with the API secret using HMAC-SHA256 and Base64-encodes the result.
   *
   * @param prehash the prehash string built by {@link #buildMessage}
   * @param secretKey the API secret
   * @return Base64-encoded signature
   */
  static String signMessage(String prehash, String secretKey) {
    try {
      Mac mac = Mac.getInstance(HMAC_SHA_256);
      mac.init(
          new javax.crypto.spec.SecretKeySpec(
              secretKey.getBytes(StandardCharsets.UTF_8), HMAC_SHA_256));
      return Base64.getEncoder().encodeToString(mac.doFinal(prehash.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new ExchangeException("Digest encoding exception", e);
    }
  }

  /**
   * Builds the UTA prehash string: millisecond timestamp + upper-cased HTTP method + endpoint path
   * with query string + JSON request body (empty for bodyless calls).
   *
   * <p>Exposed as a static helper so deterministic wire fixtures can pin the exact prehash layout
   * without constructing a Rescu invocation.
   */
  static String buildMessage(String timestamp, String method, String pathWithQueryString, String body) {
    return timestamp
        + method.toUpperCase(Locale.ROOT)
        + pathWithQueryString
        + (body != null ? body : "");
  }

  public String getSignature() {
    return signature;
  }

  /**
   * Encrypts the API passphrase for the {@code KC-API-PASSPHRASE} header.
   *
   * @param passphrase the passphrase configured when the API key was created
   * @param secretKey the API secret
   * @return Base64-encoded HMAC-SHA256 of the passphrase keyed with the secret, or {@code null}
   *     when the passphrase is blank
   */
  public static String encryptPassphrase(String passphrase, String secretKey) {
    if (Strings.isNullOrEmpty(passphrase) || Strings.isNullOrEmpty(secretKey)) {
      return null;
    }
    try {
      Mac mac = Mac.getInstance(HMAC_SHA_256);
      mac.init(new javax.crypto.spec.SecretKeySpec(
          secretKey.getBytes(StandardCharsets.UTF_8), HMAC_SHA_256));
      return Base64.getEncoder().encodeToString(mac.doFinal(passphrase.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new ExchangeException("Passphrase encryption exception", e);
    }
  }
}
