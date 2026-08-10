package org.knowm.xchange.binance.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

public class BinanceRedactionTest {

  @Test
  public void testRedactsSignatureQueryParameter() {
    String url = "https://api.binance.com/api/v3/order?symbol=BTCUSDT&signature=deadbeef1234";

    assertThat(BinanceRedaction.redact(url))
        .isEqualTo("https://api.binance.com/api/v3/order?symbol=BTCUSDT&signature=<redacted>");
  }

  @Test
  public void testRedactsApiKeyQueryParameter() {
    assertThat(BinanceRedaction.redact("?apikey=supersecret&symbol=BTCUSDT"))
        .isEqualTo("?apikey=<redacted>&symbol=BTCUSDT");
  }

  @Test
  public void testRedactsApiKeyHeader() {
    assertThat(BinanceRedaction.redact("X-MBX-APIKEY: supersecret"))
        .isEqualTo("X-MBX-APIKEY: <redacted>");
    assertThat(BinanceRedaction.redact("x-mbx-apikey=supersecret"))
        .isEqualTo("x-mbx-apikey=<redacted>");
  }

  @Test
  public void testRedactsPemPrivateKeyBlock() {
    String pem =
        "-----BEGIN PRIVATE KEY-----\nMIIEvQIBADANBgkqhkiG9w0BAQEFAASC\n-----END PRIVATE KEY-----";

    String redacted = BinanceRedaction.redact(pem);

    assertThat(redacted)
        .isEqualTo(
            "-----BEGIN PRIVATE KEY-----<redacted>-----END PRIVATE KEY-----");
    assertThat(redacted).doesNotContain("MIIEvQIBADANBgkqhkiG9w0BAQEFAASC");
  }

  @Test
  public void testNullAndPlainTextAreUnchanged() {
    assertThat(BinanceRedaction.redact(null)).isNull();
    assertThat(BinanceRedaction.redact("ordinary error message"))
        .isEqualTo("ordinary error message");
  }
}
