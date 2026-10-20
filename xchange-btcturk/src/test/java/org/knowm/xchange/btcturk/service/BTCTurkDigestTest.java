package org.knowm.xchange.btcturk.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import si.mazi.rescu.RestInvocation;

public class BTCTurkDigestTest {

  @Test
  public void testDigestParams() {
    String rawSecret = "secret";
    String secretKeyBase64 = Base64.getEncoder().encodeToString(rawSecret.getBytes());
    BTCTurkDigest digest = BTCTurkDigest.createInstance(secretKeyBase64, "apiKey");

    RestInvocation restInvocation = mock(RestInvocation.class);
    Map<String, String> headers = new HashMap<>();
    headers.put("X-PCK", "my-api-key");
    headers.put("X-Stamp", "1600000000");

    when(restInvocation.getHttpHeadersFromParams()).thenReturn(headers);

    String signature = digest.digestParams(restInvocation);
    assertThat(signature).isNotNull().isNotEmpty();

    // Verify deterministic HMAC SHA256 output for 'my-api-key1600000000' with secret 'secret'
    assertThat(signature).isEqualTo("+wbWVMTcDlLSlZ6cRMuj/4nmY8j1R1RMbftAGsiY5iU=");
  }

  @Test
  public void testCreateInstanceNullSecret() {
    BTCTurkDigest digest = BTCTurkDigest.createInstance(null, "apiKey");
    assertThat(digest).isNull();
  }
}
