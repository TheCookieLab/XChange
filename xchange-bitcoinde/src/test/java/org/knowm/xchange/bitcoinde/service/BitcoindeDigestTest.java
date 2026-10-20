package org.knowm.xchange.bitcoinde.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import si.mazi.rescu.RestInvocation;

public class BitcoindeDigestTest {

  private static final String SECRET = "c2VjcmV0S2V5";
  private static final String URL = "https://api.bitcoin.de/v1/orders";

  @Test
  public void testDigestParamsGet() {
    String result = digest("testApiKey", "GET", null);

    assertThat(result)
        .isEqualTo("d4afc5979d3caaee5dc99ee0c514740c2ac201b3a7c2bc431820479dfeb825c0");
  }

  @Test
  public void testDigestParamsPostWithNonAsciiBody() {
    // Umlauts, euro sign and en dash: their bytes differ between UTF-8 and single-byte charsets.
    String result =
        digest(
            "testApiKey", "POST", "type=buy&memo=Z\u00fcrich \u20ac1,50 \u2013 gr\u00f6\u00dfer");

    assertThat(result)
        .isEqualTo("d056c76e560e34f82ff4f618d286c7dd6f13f911196d7a0ba3aa7b6809906a75");
  }

  @Test
  public void testDigestParamsGetWithNonAsciiApiKey() {
    String result = digest("t\u00ebst\u00c4piKey\u20ac", "GET", null);

    assertThat(result)
        .isEqualTo("9f0259471155792132f0f88c4dcbabbf09c24e8e30cfe3f9b283720fb18c6668");
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
