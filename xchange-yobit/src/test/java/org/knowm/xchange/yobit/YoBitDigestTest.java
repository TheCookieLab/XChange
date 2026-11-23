package org.knowm.xchange.yobit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.ws.rs.FormParam;
import java.lang.annotation.Annotation;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import si.mazi.rescu.Params;
import si.mazi.rescu.RestInvocation;

public class YoBitDigestTest {

  @Test
  public void testDigestParams() {
    assertThat(signature(Params.of("method", "getInfo")))
        .isEqualTo(
            "7d5fe5b51d83cfc870c987e4a68609b6566128422b9726cee70c3ee17b96d05a"
                + "1e99cb6b96d0b504a848c475a1264ca1c449f70c159b229eeac1bb2552fd01bb");
    assertThat(signature(Params.of("method", "getInfo", "nonce", "12345")))
        .isEqualTo(
            "902a09480791d537b81551d6e8922b49d5e4544474129592f9338338d8945ab5"
                + "b57f0cd63c0f4720f4be542ccb933a8db4efbaca39409a36c27f7f315f97f1d5");
  }

  @Test
  public void preservesLeadingZeroBytesInSignature() {
    assertThat(signature(Params.of("nonce", "76")))
        .isEqualTo(
            "001ae308c8398fdbb6fbef28cbfb567f18b3df7ef46a6f9ab8488729571b6cc"
                + "8b1e8697d708f809f7daaf92086c5871b7d749aad197893d41ad94cc3a462e899");
  }

  @Test
  public void testCreateInstanceWithNullSecretKey() {
    YoBitDigest digest = YoBitDigest.createInstance(null, "apiKey");
    assertThat(digest).isNotNull();
  }

  private static String signature(Params params) {
    YoBitDigest digest = YoBitDigest.createInstance("secretKey", "apiKey");
    Map<Class<? extends Annotation>, Params> paramsMap = new LinkedHashMap<>();
    paramsMap.put(FormParam.class, params);
    RestInvocation restInvocation = mock(RestInvocation.class);
    when(restInvocation.getParamsMap()).thenReturn(paramsMap);
    return digest.digestParams(restInvocation);
  }
}
