package org.knowm.xchange.coinbase.v3;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.security.GeneralSecurityException;
import java.security.KeyPairGenerator;
import java.util.Base64;
import org.junit.Test;

public class CoinbaseV3DigestTest {

  @Test
  public void createInstanceWrapsInvalidPemAsIllegalState() {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> CoinbaseV3Digest.createInstance("test-api-key", "test-secret-key"));

    assertEquals(IllegalArgumentException.class, exception.getCause().getClass());
    assertEquals("Unknown PEM object: null", exception.getCause().getMessage());
  }

  @Test
  public void createInstanceWrapsNonEcPemAsIllegalState() throws Exception {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> CoinbaseV3Digest.createInstance("test-api-key", generateRsaPrivateKeyPem()));

    assertEquals(ClassCastException.class, exception.getCause().getClass());
  }

  private static String generateRsaPrivateKeyPem() throws GeneralSecurityException {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(1024);
    byte[] der = generator.generateKeyPair().getPrivate().getEncoded();
    String body = Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(der);
    return "-----BEGIN PRIVATE KEY-----\n" + body + "\n-----END PRIVATE KEY-----";
  }
}
