package org.knowm.xchange.yobit;

import jakarta.ws.rs.FormParam;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import org.knowm.xchange.service.BaseParamsDigest;
import org.knowm.xchange.utils.DigestUtils;
import si.mazi.rescu.Params;
import si.mazi.rescu.RestInvocation;

public class YoBitDigest extends BaseParamsDigest {

  private YoBitDigest(String secretKeyBase64, String apiKey) throws IOException {
    super(secretKeyBase64, HMAC_SHA_512);
  }

  public static YoBitDigest createInstance(String secretKeyBase64, String apiKey) {
    try {
      String checkedSecretKeyBase64 =
          secretKeyBase64 != null ? secretKeyBase64 : getRandomSecretKey();
      return new YoBitDigest(checkedSecretKeyBase64, apiKey);
    } catch (Exception e) {
      throw new IllegalStateException("cannot create digest", e);
    }
  }

  @Override
  public String digestParams(RestInvocation restInvocation) {
    Params params = restInvocation.getParamsMap().get(FormParam.class);

    byte[] signature = hmac(params.asQueryString().getBytes(StandardCharsets.UTF_8));
    return DigestUtils.bytesToHex(signature);
  }

  private static String getRandomSecretKey() {
    byte[] array = new byte[16];
    new SecureRandom().nextBytes(array);
    return DigestUtils.bytesToHex(array);
  }
}
