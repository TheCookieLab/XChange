package org.knowm.xchange.yobit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.ws.rs.FormParam;
import java.lang.annotation.Annotation;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;
import si.mazi.rescu.Params;
import si.mazi.rescu.RestInvocation;

public class YoBitDigestTest {

  @Test
  public void testDigestParams() {
    YoBitDigest digest = YoBitDigest.createInstance("secretKey", "apiKey");

    Map<Class<? extends Annotation>, Params> paramsMap = new LinkedHashMap<>();
    paramsMap.put(FormParam.class, Params.of("method", "getInfo", "nonce", "12345"));

    RestInvocation restInvocation = mock(RestInvocation.class);
    when(restInvocation.getParamsMap()).thenReturn(paramsMap);

    String sign = digest.digestParams(restInvocation);

    assertThat(sign).isNotNull();
    assertThat(sign.length()).isEqualTo(128);
  }

  @Test
  public void testCreateInstanceWithNullSecretKey() {
    YoBitDigest digest = YoBitDigest.createInstance(null, "apiKey");
    assertThat(digest).isNotNull();
  }
}
