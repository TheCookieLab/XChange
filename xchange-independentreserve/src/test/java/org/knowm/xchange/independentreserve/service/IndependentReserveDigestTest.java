package org.knowm.xchange.independentreserve.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;
import org.knowm.xchange.independentreserve.util.ExchangeEndpoint;

public class IndependentReserveDigestTest {

  @Test
  public void testDigestParamsToString() {
    String secretKey = "c2VjcmV0S2V5RGF0YQ==";
    String apiKey = "test-api-key-123";
    String sslUri = "https://api.independentreserve.com";

    IndependentReserveDigest digest =
        IndependentReserveDigest.createInstance(secretKey, apiKey, sslUri);
    assertThat(digest).isNotNull();

    Map<String, Object> params = new LinkedHashMap<>();
    params.put("pageIndex", 1);

    String result = digest.digestParamsToString(ExchangeEndpoint.GET_ACCOUNTS, 123456789L, params);
    assertThat(result).isNotNull();
    assertThat(result).isNotEmpty();
  }

  @Test
  public void testNullSecretKeyReturnsNullInstance() {
    IndependentReserveDigest digest =
        IndependentReserveDigest.createInstance(null, "key", "https://api.independentreserve.com");
    assertThat(digest).isNull();
  }
}
