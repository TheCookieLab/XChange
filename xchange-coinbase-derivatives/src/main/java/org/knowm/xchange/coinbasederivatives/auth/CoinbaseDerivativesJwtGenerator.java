package org.knowm.xchange.coinbasederivatives.auth;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import java.io.IOException;
import java.io.StringReader;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.security.Security;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECFieldFp;
import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.knowm.xchange.exceptions.ExchangeSecurityException;

/** Creates short-lived ES256 CDP JWTs for a Starbase {@code public/auth} exchange. */
public final class CoinbaseDerivativesJwtGenerator {
  public static final int JWT_LIFETIME_SECONDS = 120;

  private final String keyName;
  private final Algorithm algorithm;
  private final Clock clock;
  private final SecureRandom random;

  /**
   * Creates a generator from a Coinbase CDP key name and EC private key PEM.
   *
   * @param keyName CDP API key resource name
   * @param privateKeyPem P-256 private key in PKCS#8 or SEC1 PEM form
   */
  public CoinbaseDerivativesJwtGenerator(String keyName, String privateKeyPem) {
    this(keyName, privateKeyPem, Clock.systemUTC(), new SecureRandom());
  }

  CoinbaseDerivativesJwtGenerator(
      String keyName, String privateKeyPem, Clock clock, SecureRandom random) {
    if (keyName == null || keyName.isBlank() || privateKeyPem == null || privateKeyPem.isBlank()) {
      throw new ExchangeSecurityException("Coinbase CDP credentials must not be empty");
    }
    try {
      ensureProvider();
      KeyPair keyPair = loadKeyPair(normalizePem(privateKeyPem));
      algorithm =
          Algorithm.ECDSA256(
              (ECPublicKey) keyPair.getPublic(), (ECPrivateKey) keyPair.getPrivate());
    } catch (IOException
        | GeneralSecurityException
        | IllegalArgumentException
        | ClassCastException e) {
      throw new ExchangeSecurityException("Invalid Coinbase CDP private key", e);
    }
    this.keyName = keyName;
    this.clock = clock;
    this.random = random;
  }

  /** Returns a new JWT. JWT values must never be logged or reused across auth exchanges. */
  public String generate() {
    Instant now = clock.instant();
    return JWT.create()
        .withKeyId(keyName)
        .withIssuer("cdp")
        .withSubject(keyName)
        .withNotBefore(Date.from(now))
        .withExpiresAt(Date.from(now.plusSeconds(JWT_LIFETIME_SECONDS)))
        .withHeader(Collections.singletonMap("nonce", randomHex(16)))
        .sign(algorithm);
  }

  private String randomHex(int bytes) {
    byte[] buffer = new byte[bytes];
    random.nextBytes(buffer);
    StringBuilder result = new StringBuilder(bytes * 2);
    for (byte value : buffer) {
      result.append(String.format("%02x", value));
    }
    return result.toString();
  }

  private static void ensureProvider() {
    if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
      Security.addProvider(new BouncyCastleProvider());
    }
  }

  private static String normalizePem(String pem) {
    return pem.replace("\\n", "\n")
        .replaceAll("-----BEGIN (.*) KEY-----\\s+", "-----BEGIN $1 KEY-----\n")
        .replaceAll("\\s+-----END (.*) KEY-----", "\n-----END $1 KEY-----");
  }

  private static KeyPair loadKeyPair(String pem) throws IOException, GeneralSecurityException {
    try (PEMParser parser = new PEMParser(new StringReader(pem))) {
      Object parsed = parser.readObject();
      JcaPEMKeyConverter converter =
          new JcaPEMKeyConverter().setProvider(BouncyCastleProvider.PROVIDER_NAME);
      if (parsed instanceof PEMKeyPair pemKeyPair) {
        return converter.getKeyPair(pemKeyPair);
      }
      if (!(parsed instanceof PrivateKeyInfo privateKeyInfo)) {
        throw new GeneralSecurityException("Unsupported EC private key PEM format");
      }

      ECPrivateKey privateKey = (ECPrivateKey) converter.getPrivateKey(privateKeyInfo);
      ECNamedCurveParameterSpec curve = ECNamedCurveTable.getParameterSpec("P-256");
      org.bouncycastle.math.ec.ECPoint point = curve.getG().multiply(privateKey.getS()).normalize();
      java.security.spec.ECPoint publicPoint =
          new java.security.spec.ECPoint(
              point.getAffineXCoord().toBigInteger(), point.getAffineYCoord().toBigInteger());
      java.security.spec.EllipticCurve jcaCurve =
          new java.security.spec.EllipticCurve(
              new ECFieldFp(curve.getCurve().getField().getCharacteristic()),
              curve.getCurve().getA().toBigInteger(),
              curve.getCurve().getB().toBigInteger(),
              curve.getSeed());
      java.security.spec.ECParameterSpec parameters =
          new java.security.spec.ECParameterSpec(
              jcaCurve,
              new java.security.spec.ECPoint(
                  curve.getG().getAffineXCoord().toBigInteger(),
                  curve.getG().getAffineYCoord().toBigInteger()),
              curve.getN(),
              curve.getH().intValue());
      ECPublicKey publicKey =
          (ECPublicKey)
              KeyFactory.getInstance("EC", BouncyCastleProvider.PROVIDER_NAME)
                  .generatePublic(new java.security.spec.ECPublicKeySpec(publicPoint, parameters));
      return new KeyPair(publicKey, privateKey);
    }
  }
}
