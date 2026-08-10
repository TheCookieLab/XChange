package org.knowm.xchange.binance.auth;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import org.knowm.xchange.exceptions.ExchangeException;
import si.mazi.rescu.ParamsDigest;
import si.mazi.rescu.RestInvocation;

/**
 * RSA (SHA256withRSA) request signing for Binance.
 *
 * <p>Accepts the private key as bare Base64-encoded PKCS#8 DER or as a PEM {@code PRIVATE KEY}
 * block, and emits the Base64-encoded signature Binance expects. The private key material is held
 * only in the in-memory {@link PrivateKey} instance; it is never logged or serialized.
 */
public class BinanceRsaDigest implements ParamsDigest {

  private static final String PEM_BEGIN = "-----BEGIN PRIVATE KEY-----";
  private static final String PEM_END = "-----END PRIVATE KEY-----";

  private final PrivateKey privateKey;

  private BinanceRsaDigest(String privateKeyMaterial) {
    this.privateKey = parsePrivateKey(privateKeyMaterial);
  }

  /**
   * @param privateKeyMaterial PKCS#8 private key as PEM or bare Base64, or {@code null}.
   * @return the digest, or {@code null} when no key material was supplied.
   */
  public static BinanceRsaDigest createInstance(String privateKeyMaterial) {
    return privateKeyMaterial == null ? null : new BinanceRsaDigest(privateKeyMaterial);
  }

  @Override
  public String digestParams(RestInvocation restInvocation) {
    final byte[] payload =
        BinanceSigning.signingPayload(restInvocation).getBytes(StandardCharsets.UTF_8);
    try {
      Signature signer = Signature.getInstance("SHA256withRSA");
      signer.initSign(privateKey);
      signer.update(payload);
      return Base64.getEncoder().encodeToString(signer.sign());
    } catch (Exception e) {
      // SignatureException, InvalidKeyException, NoSuchAlgorithmException
      throw new ExchangeException("Failed to sign Binance request with RSA key", e);
    }
  }

  private static PrivateKey parsePrivateKey(String material) {
    final String normalized = normalize(material);
    final byte[] der;
    try {
      der = Base64.getDecoder().decode(normalized);
    } catch (IllegalArgumentException e) {
      throw new ExchangeException(
          "Binance RSA private key is not valid Base64-encoded PKCS#8 material", e);
    }
    try {
      KeyFactory keyFactory = KeyFactory.getInstance("RSA");
      return keyFactory.generatePrivate(new PKCS8EncodedKeySpec(der));
    } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
      throw new ExchangeException(
          "Binance RSA private key could not be parsed as a PKCS#8 RSA key", e);
    }
  }

  private static String normalize(String material) {
    String trimmed = material.trim();
    if (trimmed.startsWith(PEM_BEGIN)) {
      int end = trimmed.indexOf(PEM_END);
      if (end < 0) {
        throw new ExchangeException("Binance RSA private key PEM block is missing its end marker");
      }
      trimmed = trimmed.substring(PEM_BEGIN.length(), end);
    }
    return trimmed.replaceAll("\\s+", "");
  }
}
