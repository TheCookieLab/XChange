package org.knowm.xchange.bitcoinde.v4.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import si.mazi.rescu.RestInvocation;

public class BitcoindeDigestTest {

  @Test
  public void testDigestParamsGet() {
    BitcoindeDigest digest = BitcoindeDigest.createInstance("c2VjcmV0S2V5", "testApiKey");
    RestInvocation restInvocation = mock(RestInvocation.class);

    when(restInvocation.getInvocationUrl()).thenReturn("https://api.bitcoin.de/v4/orders");
    when(restInvocation.getHttpMethod()).thenReturn("GET");
    when(restInvocation.getRequestBody()).thenReturn(null);

    Map<String, String> headers = new HashMap<>();
    headers.put("X-API-NONCE", "123456");
    when(restInvocation.getHttpHeadersFromParams()).thenReturn(headers);

    String result = digest.digestParams(restInvocation);
    assertThat(result).isNotNull();
    assertThat(result.length()).isEqualTo(64);
  }

  @Test
  public void testDigestParamsPost() {
    BitcoindeDigest digest = BitcoindeDigest.createInstance("c2VjcmV0S2V5", "testApiKey");
    RestInvocation restInvocation = mock(RestInvocation.class);

    when(restInvocation.getInvocationUrl()).thenReturn("https://api.bitcoin.de/v4/orders");
    when(restInvocation.getHttpMethod()).thenReturn("POST");
    when(restInvocation.getRequestBody()).thenReturn("type=buy&amount=1.0");

    Map<String, String> headers = new HashMap<>();
    headers.put("X-API-NONCE", "123456");
    when(restInvocation.getHttpHeadersFromParams()).thenReturn(headers);

    String result = digest.digestParams(restInvocation);
    assertThat(result).isNotNull();
    assertThat(result.length()).isEqualTo(64);
  }
}
