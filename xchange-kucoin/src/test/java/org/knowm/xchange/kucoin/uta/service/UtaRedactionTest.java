package org.knowm.xchange.kucoin.uta.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UtaRedactionTest {

  @Test
  void masksSignatureHeaders() {
    String redacted =
        UtaRedaction.sanitize("KC-API-KEY: abc123 KC-API-SIGN: YWJjZA== KC-API-PASSPHRASE: eHl6");
    assertFalse(redacted.contains("YWJjZA=="));
    assertFalse(redacted.contains("eHl6"));
    assertFalse(redacted.contains("abc123"));
    assertTrue(redacted.contains("KC-API-SIGN: ***"));
    assertTrue(redacted.contains("KC-API-PASSPHRASE: ***"));
    assertTrue(redacted.contains("KC-API-KEY: ***"));
  }

  @Test
  void masksWebSocketTokens() {
    String redacted = UtaRedaction.sanitize("wss://wsapi-push.kucoin.com/?token=secretToken&x=1");
    assertFalse(redacted.contains("secretToken"));
    assertTrue(redacted.contains("token=***"));
  }

  @Test
  void masksJsonSecretFields() {
    String redacted =
        UtaRedaction.sanitize("{\"passphrase\":\"topsecret\",\"token\":\"tok123\",\"amount\":\"9\"}");
    assertFalse(redacted.contains("topsecret"));
    assertFalse(redacted.contains("tok123"));
    assertTrue(redacted.contains("\"passphrase\":***"));
    assertTrue(redacted.contains("\"token\":***"));
  }

  @Test
  void nullAndEmptyArePassthrough() {
    assertEquals(null, UtaRedaction.sanitize(null));
    assertEquals("", UtaRedaction.sanitize(""));
  }
}
