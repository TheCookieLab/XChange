package org.knowm.xchange.binance.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.knowm.xchange.utils.DigestUtils.bytesToHex;

import jakarta.ws.rs.QueryParam;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.Test;
import org.knowm.xchange.binance.config.BinanceKeyAlgorithm;
import org.knowm.xchange.binance.service.BinanceED25519Digest;
import org.knowm.xchange.binance.service.BinanceHmacDigest;
import si.mazi.rescu.Params;
import si.mazi.rescu.RestInvocation;

/**
 * Signature vectors for the HMAC-SHA256 and Ed25519 algorithms plus payload-assembly rules.
 *
 * <p>The payload string, HMAC secret, Ed25519 key, and expected signatures are committed
 * resources generated once with OpenSSL; any change to canonical payload assembly breaks these
 * tests.
 */
public class BinanceSigningTest {

  private static final String PAYLOAD = "symbol=BTCUSDT&side=BUY&type=LIMIT&quantity=0.001&timestamp=1723200000000";
  private static final String HMAC_SECRET = "test-secret";

  @Test
  public void testHmacVector() throws Exception {
    BinanceHmacDigest digest = BinanceHmacDigest.createInstance(HMAC_SECRET);

    String signature = digest.digestParams(invocation("GET", PAYLOAD, ""));

    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(HMAC_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    String expected = bytesToHex(mac.doFinal(PAYLOAD.getBytes(StandardCharsets.UTF_8)));
    assertThat(signature).isEqualTo(expected);
    assertThat(signature).isEqualTo("34023a5bfb792479aa3811806432b663642ce247afeca08d8aaae8cbdf26d73b");
  }

  @Test
  public void testEd25519Vector() {
    BinanceED25519Digest digest =
        BinanceED25519Digest.createInstance(
            "MC4CAQAwBQYDK2VwBCIEIIizRD0PmmJxLeXRznhlgMEakP4jd62m9c6D/Ry+zYZu");

    String signature = digest.digestParams(invocation("GET", PAYLOAD, ""));

    assertThat(signature)
        .isEqualTo("eMncYAWDRr4CGMhn7lA5C4iKT3xOhc1LUJW/0l0imPK1P+HcL2ZASM5J2/KhDhmZUYx6GTRbWv48piRuAeRQDA==");
  }

  @Test
  public void testPostPayloadAppendsBody() {
    String query = "symbol=BTCUSDT&side=BUY";
    String body = "quantity=0.001";
    assertThat(BinanceSigning.signingPayload(invocation("POST", query, body)))
        .isEqualTo("symbol=BTCUSDT&side=BUYquantity=0.001");
  }

  @Test
  public void testPayloadExcludesSignatureParam() {
    String query = "symbol=BTCUSDT&signature=abc123&timestamp=1723200000000";
    assertThat(BinanceSigning.signingPayload(invocation("GET", query, "")))
        .isEqualTo("symbol=BTCUSDT&timestamp=1723200000000");
  }

  @Test
  public void testCreateDigestSelectsAlgorithm() throws Exception {
    assertThat(BinanceSigning.createDigest(BinanceKeyAlgorithm.HMAC_SHA_256, "secret"))
        .isInstanceOf(BinanceHmacDigest.class);
    String rsaKey;
    try (InputStream in =
        BinanceSigningTest.class.getResourceAsStream("/rsa-test-key.b64")) {
      rsaKey = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
    }
    assertThat(BinanceSigning.createDigest(BinanceKeyAlgorithm.RSA, rsaKey))
        .isInstanceOf(BinanceRsaDigest.class);
    assertThat(BinanceSigning.createDigest(BinanceKeyAlgorithm.ED25519, "MC4CAQAwBQYDK2VwBCIEIIizRD0PmmJxLeXRznhlgMEakP4jd62m9c6D/Ry+zYZu"))
        .isInstanceOf(BinanceED25519Digest.class);
    assertThat(BinanceSigning.createDigest(BinanceKeyAlgorithm.HMAC_SHA_256, null)).isNull();
  }

  private static RestInvocation invocation(String method, String queryString, String body) {
    RestInvocation invocation = org.mockito.Mockito.mock(RestInvocation.class);
    Params queryParams = org.mockito.Mockito.mock(Params.class);
    Map<String, String> headers = new LinkedHashMap<>();
    for (String pair : queryString.split("&")) {
      String[] kv = pair.split("=", 2);
      headers.put(kv[0], kv.length > 1 ? kv[1] : "");
    }
    org.mockito.Mockito.when(queryParams.asHttpHeaders()).thenReturn(headers);
    org.mockito.Mockito.when(invocation.getParamsMap())
        .thenReturn(Map.of(QueryParam.class, queryParams));
    org.mockito.Mockito.when(invocation.getHttpMethod()).thenReturn(method);
    org.mockito.Mockito.when(invocation.getRequestBody()).thenReturn(body);
    return invocation;
  }
}
