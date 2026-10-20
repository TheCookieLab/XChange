package org.knowm.xchange.yobit;

import jakarta.ws.rs.FormParam;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import javax.crypto.Mac;
import org.knowm.xchange.service.BaseParamsDigest;
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

    StringBuilder queryString = new StringBuilder(params.asQueryString());

    Mac mac = getMac();

    byte[] source = mac.doFinal(queryString.toString().getBytes(StandardCharsets.UTF_8));

    return String.format("%0128x", new BigInteger(1, source));
  }

  private static String getRandomSecretKey() {
    byte[] array = new byte[16];
    new SecureRandom().nextBytes(array);
    StringBuilder sb = new StringBuilder();
    for (byte b : array) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }
}
