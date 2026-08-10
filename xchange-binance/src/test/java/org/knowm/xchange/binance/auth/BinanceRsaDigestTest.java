package org.knowm.xchange.binance.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.ws.rs.QueryParam;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;
import org.knowm.xchange.exceptions.ExchangeException;
import si.mazi.rescu.Params;
import si.mazi.rescu.RestInvocation;

/**
 * Deterministic RSA signature vector: the key, payload, and expected signature are committed
 * resources generated once with OpenSSL ({@code openssl dgst -sha256 -sign}). Any change to the
 * payload assembly breaks this test.
 */
public class BinanceRsaDigestTest {

  private static final String PAYLOAD = resource("rsa-test-payload.txt");
  private static final String EXPECTED_SIGNATURE = resource("rsa-test-signature.b64");

  @Test
  public void testSignsCommittedVectorWithBarePkcs8Key() {
    BinanceRsaDigest digest = BinanceRsaDigest.createInstance(resource("rsa-test-key.b64"));

    String signature = digest.digestParams(invocationWithPayload(PAYLOAD));

    assertThat(signature).isEqualTo(EXPECTED_SIGNATURE);
  }

  @Test
  public void testSignsCommittedVectorWithPemKey() {
    String bareKey = resource("rsa-test-key.b64");
    String pemKey = "-----BEGIN PRIVATE KEY-----\n" + bareKey + "\n-----END PRIVATE KEY-----";
    BinanceRsaDigest digest = BinanceRsaDigest.createInstance(pemKey);

    String signature = digest.digestParams(invocationWithPayload(PAYLOAD));

    assertThat(signature).isEqualTo(EXPECTED_SIGNATURE);
  }

  @Test
  public void testCreateInstanceReturnsNullWithoutKeyMaterial() {
    assertThat(BinanceRsaDigest.createInstance(null)).isNull();
  }

  @Test
  public void testRejectsGarbageKeyMaterial() {
    assertThatThrownBy(() -> BinanceRsaDigest.createInstance("not-a-key"))
        .isInstanceOf(ExchangeException.class)
        .hasMessageContaining("RSA");
  }

  private static RestInvocation invocationWithPayload(String queryString) {
    RestInvocation invocation = mock(RestInvocation.class);
    Params queryParams = mock(Params.class);
    Map<String, String> headers = new LinkedHashMap<>();
    for (String pair : queryString.split("&")) {
      String[] kv = pair.split("=", 2);
      headers.put(kv[0], kv.length > 1 ? kv[1] : "");
    }
    when(queryParams.asHttpHeaders()).thenReturn(headers);
    when(invocation.getParamsMap()).thenReturn(Map.of(QueryParam.class, queryParams));
    when(invocation.getHttpMethod()).thenReturn("GET");
    when(invocation.getRequestBody()).thenReturn("");
    return invocation;
  }

  private static String resource(String name) {
    try (InputStream in = BinanceRsaDigestTest.class.getResourceAsStream("/" + name)) {
      assertThat(in).as("missing test resource %s", name).isNotNull();
      return new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
    } catch (IOException e) {
      throw new IllegalStateException("Failed to read test resource " + name, e);
    }
  }
}
