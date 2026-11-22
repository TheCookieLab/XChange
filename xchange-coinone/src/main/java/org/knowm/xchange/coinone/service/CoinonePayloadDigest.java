package org.knowm.xchange.coinone.service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import si.mazi.rescu.ParamsDigest;
import si.mazi.rescu.RestInvocation;

public class CoinonePayloadDigest implements ParamsDigest {

  private CoinonePayloadDigest() {}

  public static CoinonePayloadDigest createInstance() {

    return new CoinonePayloadDigest();
  }

  @Override
  public String digestParams(RestInvocation restInvocation) {

    String postBody = restInvocation.getRequestBody();
    String payload = Base64.getEncoder().encodeToString(postBody.getBytes(StandardCharsets.UTF_8));
    return payload;
  }
}
