package org.knowm.xchange.kalshi.client;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.PSSParameterSpec;
import java.util.Base64;
import org.knowm.xchange.exceptions.ExchangeSecurityException;
import si.mazi.rescu.ParamsDigest;
import si.mazi.rescu.RestInvocation;

/**
 * Kalshi request signer.
 *
 * <p>Authenticated Kalshi requests sign {@code timestamp + HTTP method + request path} (path
 * without query string) with an RSA private key using RSA-PSS over SHA-256 and emit the signature
 * base64-encoded in the {@code KALSHI-ACCESS-SIGNATURE} header.
 *
 * <p>The private key is accepted as an unencrypted PKCS#8 PEM document, which is the format
 * Kalshi's documented {@code openssl genpkey} flow produces. The PEM text never appears in
 * exceptions or logs.
 */
public final class KalshiDigest implements ParamsDigest {

  /** Header whose resolved value anchors the signed payload. */
  public static final String TIMESTAMP_HEADER = "KALSHI-ACCESS-TIMESTAMP";

  private static final PSSParameterSpec PSS_PARAMETERS =
      new PSSParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1);

  private final PrivateKey privateKey;

  private KalshiDigest(PrivateKey privateKey) {
    this.privateKey = privateKey;
  }

  /**
   * Creates a signer from an unencrypted PKCS#8 PEM private key.
   *
   * @param privateKeyPem PEM document, or {@code null}/blank to disable signing
   * @return the signer, or {@code null} when no key material was supplied
   */
  public static KalshiDigest createInstance(String privateKeyPem) {
    if (privateKeyPem == null || privateKeyPem.isBlank()) {
      return null;
    }
    return new KalshiDigest(parsePkcs8PrivateKey(privateKeyPem));
  }

  static PrivateKey parsePkcs8PrivateKey(String pem) {
    String base64 =
        pem.replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replaceAll("\\s", "");
    final byte[] der;
    try {
      der = Base64.getDecoder().decode(base64);
    } catch (IllegalArgumentException e) {
      throw new ExchangeSecurityException(
          "Kalshi private key is not valid base64 PEM content (unencrypted PKCS#8 required)", e);
    }
    try {
      return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    } catch (GeneralSecurityException e) {
      throw new ExchangeSecurityException(
          "Kalshi private key is not an unencrypted PKCS#8 RSA key", e);
    }
  }

  @Override
  public String digestParams(RestInvocation restInvocation) {
    String timestamp = restInvocation.getHttpHeadersFromParams().get(TIMESTAMP_HEADER);
    String payload =
        timestamp + restInvocation.getHttpMethod() + "/" + restInvocation.getPath();
    return sign(payload);
  }

  /**
   * Signs the canonical {@code timestamp + method + path} payload. Public so the WebSocket
   * handshake in {@code xchange-stream-kalshi} reuses the exact REST signing rule.
   *
   * @param payload canonical {@code timestamp + method + path} string
   * @return base64-encoded RSA-PSS signature
   */
  public String sign(String payload) {
    try {
      Signature signature = Signature.getInstance("RSASSA-PSS");
      signature.setParameter(PSS_PARAMETERS);
      signature.initSign(privateKey);
      signature.update(payload.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(signature.sign());
    } catch (GeneralSecurityException e) {
      throw new ExchangeSecurityException("Kalshi request signing failed", e);
    }
  }
}
