package org.knowm.xchange.coinbase.v3;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

/** Test support for building valid EC P-256 key material. */
final class CoinbaseV3DigestTestSupport {

  private CoinbaseV3DigestTestSupport() {}

  /** Generates a fresh PEM-encoded P-256 private key (PKCS#8). */
  static String validEcPrivateKeyPem() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
    generator.initialize(256);
    KeyPair keyPair = generator.generateKeyPair();
    byte[] der = keyPair.getPrivate().getEncoded();
    String body = Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(der);
    return "-----BEGIN PRIVATE KEY-----\n" + body + "\n-----END PRIVATE KEY-----";
  }
}
