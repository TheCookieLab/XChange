package org.knowm.xchange.kalshi.client;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.Signature;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.exceptions.ExchangeSecurityException;
import org.knowm.xchange.kalshi.KalshiTestKeys;

/** RSA-PSS signing and PKCS#8 PEM handling for {@link KalshiDigest}. */
class KalshiDigestTest {

  private static final PSSParameterSpec PSS_PARAMETERS =
      new PSSParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1);

  @Test
  void missingKeyMaterialDisablesSigning() {
    assertNull(KalshiDigest.createInstance(null));
    assertNull(KalshiDigest.createInstance("   "));
  }

  @Test
  void malformedPemFailsWithoutLeakingKeyMaterial() {
    ExchangeSecurityException exception =
        assertThrows(
            ExchangeSecurityException.class, () -> KalshiDigest.createInstance("not-a-pem"));
    assertTrue(exception.getMessage().contains("base64"));
  }

  @Test
  void signatureVerifiesAgainstPublicKey() throws Exception {
    KalshiDigest digest =
        assertDoesNotThrow(() -> KalshiDigest.createInstance(KalshiTestKeys.privateKeyPem()));
    assertNotNull(digest);

    String payload = "1754230000000POST/trade-api/v2/portfolio/events/orders";
    String signatureBase64 = digest.sign(payload);

    Signature verifier = Signature.getInstance("RSASSA-PSS");
    verifier.setParameter(PSS_PARAMETERS);
    verifier.initVerify(KalshiTestKeys.publicKey());
    verifier.update(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    assertTrue(verifier.verify(Base64.getDecoder().decode(signatureBase64)));
  }
}
