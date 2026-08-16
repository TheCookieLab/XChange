package org.knowm.xchange.cryptocom;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.utils.DigestUtils;

public class CryptoComDigestTest {

  @Test
  public void nullParamValue_isRenderedAsLiteralNullInSignaturePayload() throws Exception {
    Map<String, Object> params = new LinkedHashMap<>();
    params.put("client_oid", null);

    String actual =
        CryptoComDigest.signature(
            "private/create-order", 1L, "key", 42L, params, "secret");

    String expectedPayload = "private/create-order" + 1L + "key" + "client_oidnull" + 42L;
    String expected = hmacSha256Hex(expectedPayload, "secret");

    assertThat(actual).isEqualTo(expected);
  }

  @Test
  public void noParams_matchesPlainConcatenation() throws Exception {
    String actual =
        CryptoComDigest.signature(
            "public/get-instruments", 1L, "key", 42L, Collections.emptyMap(), "secret");

    String expected = hmacSha256Hex("public/get-instruments" + 1L + "key" + 42L, "secret");

    assertThat(actual).isEqualTo(expected);
  }

  /**
   * Independent hardcoded vectors (computed with Python's hmac against Crypto.com's documented
   * envelope layout method+id+apiKey+params+nonce), so the Java implementation is not verified
   * against itself.
   */
  @Test
  public void multiParamPayload_matchesIndependentVector() throws Exception {
    Map<String, Object> params = new LinkedHashMap<>();
    params.put("instrument_name", "BTC_USDT");
    params.put("side", "BUY");
    params.put("type", "LIMIT");
    params.put("price", "50000");
    params.put("quantity", "0.5");
    params.put("time_in_force", "GOOD_TILL_CANCEL");
    params.put("client_oid", "oid-test-1");

    String actual =
        CryptoComDigest.signature(
            "private/create-order", 42L, "apiKeyTest", 1700000000000L, params, "secretKey-vector-1");

    assertThat(actual)
        .isEqualTo("16d4a14f758f32fd56116e97617d3a0e562f82a53e01f40608ce37f6d3cdd266");
  }

  @Test
  public void emptyParamsPayload_matchesIndependentVector() throws Exception {
    String actual =
        CryptoComDigest.signature("private/create-order", 7L, "k", 12345L, Collections.emptyMap(), "s");

    assertThat(actual)
        .isEqualTo("00e8e60fd087245049393fdd58745dfe67893732f89942d21e9712bec6de93b6");
  }

  private static String hmacSha256Hex(String data, String secret)
      throws NoSuchAlgorithmException, InvalidKeyException {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    return DigestUtils.bytesToHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
  }
}
