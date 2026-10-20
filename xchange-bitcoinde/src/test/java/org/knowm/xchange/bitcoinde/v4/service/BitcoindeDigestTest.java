package org.knowm.xchange.bitcoinde.v4.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import si.mazi.rescu.RestInvocation;

public class BitcoindeDigestTest {

  private static final String SECRET = "c2VjcmV0S2V5";
  private static final String URL = "https://api.bitcoin.de/v4/orders";

  @Test
  public void testDigestParamsGet() {
    String result = digest("testApiKey", "GET", null);

    assertThat(result)
        .isEqualTo("64dc73f3cdd14997c21a5fd42747fe1994c699899ed822f2764ce34fc6432c18");
  }

  @Test
  public void testDigestParamsPostWithNonAsciiBody() {
    // Umlauts, euro sign and en dash: their bytes differ between UTF-8 and single-byte charsets.
    String result =
        digest(
            "testApiKey", "POST", "type=buy&memo=Z\u00fcrich \u20ac1,50 \u2013 gr\u00f6\u00dfer");

    assertThat(result)
        .isEqualTo("eb82d6de3e28cddc44bf949ec51655101e741c0fbbd6d8014d6bf9b0caddcfe2");
  }

  @Test
  public void testDigestParamsGetWithNonAsciiApiKey() {
    String result = digest("t\u00ebst\u00c4piKey\u20ac", "GET", null);

    assertThat(result)
        .isEqualTo("eb34c86a54159ab50fef8fd822b448c51f0af2ac8dfae838e02fea2e25d73114");
  }

  private static String digest(String apiKey, String httpMethod, String requestBody) {
    BitcoindeDigest digest = BitcoindeDigest.createInstance(SECRET, apiKey);
    RestInvocation restInvocation = mock(RestInvocation.class);

    when(restInvocation.getInvocationUrl()).thenReturn(URL);
    when(restInvocation.getHttpMethod()).thenReturn(httpMethod);
    when(restInvocation.getRequestBody()).thenReturn(requestBody);

    Map<String, String> headers = new HashMap<>();
    headers.put("X-API-NONCE", "123456");
    when(restInvocation.getHttpHeadersFromParams()).thenReturn(headers);

    return digest.digestParams(restInvocation);
  }
}
