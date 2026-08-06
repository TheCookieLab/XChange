package org.knowm.xchange.kalshi;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.util.Base64;

/** Deterministic-in-shape (freshly generated per JVM) RSA test key material. */
public final class KalshiTestKeys {

  private static KeyPair keyPair;

  private KalshiTestKeys() {}

  /** Lazily generated 2048-bit RSA key pair reused across tests in this JVM. */
  public static synchronized KeyPair keyPair() {
    if (keyPair == null) {
      try {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
      } catch (NoSuchAlgorithmException e) {
        throw new IllegalStateException(e);
      }
    }
    return keyPair;
  }

  /** PKCS#8 PEM encoding of the test private key, as accepted by {@code KalshiDigest}. */
  public static String privateKeyPem() {
    String base64 =
        Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(keyPair().getPrivate().getEncoded());
    return "-----BEGIN PRIVATE KEY-----\n" + base64 + "\n-----END PRIVATE KEY-----\n";
  }

  /** Public key matching {@link #privateKeyPem()}. */
  public static PublicKey publicKey() {
    return keyPair().getPublic();
  }
}
