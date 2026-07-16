package org.knowm.xchange.coinbasederivatives;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;

public final class TestKeys {
  private TestKeys() {}

  public static String newEcPrivateKeyPem() throws GeneralSecurityException {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
    generator.initialize(new ECGenParameterSpec("secp256r1"));
    KeyPair keyPair = generator.generateKeyPair();
    String base64 =
        Base64.getMimeEncoder(64, new byte[] {'\n'})
            .encodeToString(keyPair.getPrivate().getEncoded());
    return "-----BEGIN PRIVATE KEY-----\n" + base64 + "\n-----END PRIVATE KEY-----";
  }
}
