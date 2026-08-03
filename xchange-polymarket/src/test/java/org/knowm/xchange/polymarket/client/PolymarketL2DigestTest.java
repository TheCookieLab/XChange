package org.knowm.xchange.polymarket.client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.exceptions.ExchangeSecurityException;

/** L2 HMAC digest tests: secret decoding variants, a pinned signature, and redaction. */
class PolymarketL2DigestTest {

  @Test
  void blankSecretsDisableSigning() {
    assertNull(PolymarketL2Digest.createInstance(null));
    assertNull(PolymarketL2Digest.createInstance("  "));
  }

  @Test
  void decodesUrlSafeAndStandardBase64() {
    byte[] expected = {(byte) 0xfb, (byte) 0xff, (byte) 0xfe};
    assertArrayEquals(expected, PolymarketL2Digest.decodeSecret("-__-"));
    assertArrayEquals(expected, PolymarketL2Digest.decodeSecret("+//+"));
  }

  @Test
  void invalidBase64IsRejectedWithoutEchoingSecret() {
    String garbage = "!!!not-base64!!!";
    ExchangeSecurityException e =
        assertThrows(
            ExchangeSecurityException.class, () -> PolymarketL2Digest.createInstance(garbage));
    assertFalse(e.getMessage().contains(garbage), "secret material must be redacted");
  }

  @Test
  void signsWithHmacSha256UrlSafeBase64() throws Exception {
    byte[] key = new byte[32];
    for (int i = 0; i < key.length; i++) {
      key[i] = (byte) i;
    }
    PolymarketL2Digest digest =
        PolymarketL2Digest.createInstance(Base64.getUrlEncoder().encodeToString(key));
    String payload = "1754230000POST/order{\"order\":{}}";

    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(key, "HmacSHA256"));
    String expected =
        Base64.getUrlEncoder()
            .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    assertEquals(expected, digest.sign(payload));
  }
}
