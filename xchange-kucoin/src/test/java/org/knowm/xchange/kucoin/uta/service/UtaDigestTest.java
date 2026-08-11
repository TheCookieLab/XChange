package org.knowm.xchange.kucoin.uta.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Deterministic wire fixtures for UTA signing, verified against an independent HMAC-SHA256
 * implementation.
 */
class UtaDigestTest {

  private static final String SECRET = "test-secret-key";
  private static final String TIMESTAMP = "1710000000000";

  @Test
  void buildsPostPrehashWithUppercasedMethodAndBody() {
    String body =
        "{\"tradeType\":\"SPOT\",\"symbol\":\"BTC-USDT\",\"clientOid\":\"ord-123\","
            + "\"side\":\"BUY\",\"orderType\":\"LIMIT\",\"size\":\"0.001\","
            + "\"sizeUnit\":\"BASECCY\",\"price\":\"65000\"}";
    String prehash =
        UtaDigest.buildMessage(
            TIMESTAMP, "post", "/api/ua/v1/unified/order/place", body);
    assertEquals(TIMESTAMP + "POST/api/ua/v1/unified/order/place" + body, prehash);
    assertEquals(
        "OeVIOT57xyILygt45vyI3LVpxzRdfk9Q6GcQXbTSqrQ=",
        UtaDigest.signMessage(prehash, SECRET));
  }

  @Test
  void buildsGetPrehashWithEmptyBody() {
    String prehash =
        UtaDigest.buildMessage(
            "1710000000001",
            "GET",
            "/api/ua/v1/unified/order/detail?tradeType=SPOT&symbol=BTC-USDT&clientOid=ord-123",
            null);
    assertEquals(
        "rg0FwnLwI0bb5O8ksOA5T2LiGhhc56dH0oixlfoCdeA=",
        UtaDigest.signMessage(prehash, SECRET));
  }

  @Test
  void encryptsPassphraseWithSecretKeyedHmac() {
    assertEquals(
        "3xDHnOa0/Xy0DMROwGI0xOnKFMCYWFgtmYHNy5zfT5E=",
        UtaDigest.encryptPassphrase("test-passphrase", SECRET));
  }

  @Test
  void nullPassphraseOrSecretYieldsNullEncryption() {
    assertEquals(null, UtaDigest.encryptPassphrase(null, SECRET));
    assertEquals(null, UtaDigest.encryptPassphrase("pass", null));
  }

  @Test
  void createInstanceReturnsNullForBlankSecret() {
    assertEquals(null, UtaDigest.createInstance(null));
    assertEquals(null, UtaDigest.createInstance(""));
  }
}
