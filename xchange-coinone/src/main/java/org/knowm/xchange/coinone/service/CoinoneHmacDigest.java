package org.knowm.xchange.coinone.service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.knowm.xchange.service.BaseParamsDigest;
import org.knowm.xchange.utils.DigestUtils;
import si.mazi.rescu.RestInvocation;

public class CoinoneHmacDigest extends BaseParamsDigest {

  private CoinoneHmacDigest(String secretKeyBase) throws IllegalArgumentException {
    super(secretKeyBase, HMAC_SHA_512);
  }

  public static CoinoneHmacDigest createInstance(String secretKeyBase) {

    return new CoinoneHmacDigest(secretKeyBase);
  }

  @Override
  public String digestParams(RestInvocation restInvocation) {
    String postBody = restInvocation.getRequestBody();
    byte[] payload = Base64.getEncoder().encode(postBody.getBytes(StandardCharsets.UTF_8));
    return DigestUtils.bytesToHex(hmac(payload));
  }
}
