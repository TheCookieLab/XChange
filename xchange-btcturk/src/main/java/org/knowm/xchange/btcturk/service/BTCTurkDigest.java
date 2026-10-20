package org.knowm.xchange.btcturk.service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Mac;
import org.knowm.xchange.service.BaseParamsDigest;
import si.mazi.rescu.RestInvocation;

/**
 * @author mertguner
 */
public class BTCTurkDigest extends BaseParamsDigest {

  private BTCTurkDigest(String secretKey) {
    super(decodeBase64(secretKey), HMAC_SHA_256);
  }

  public static BTCTurkDigest createInstance(String secretKey, String apiKey) {
    return secretKey == null ? null : new BTCTurkDigest(secretKey);
  }

  @Override
  public String digestParams(RestInvocation restInvocation) {
    String apiKey = restInvocation.getHttpHeadersFromParams().get("X-PCK");
    String timestamp = restInvocation.getHttpHeadersFromParams().get("X-Stamp");

    return generateHash(apiKey, timestamp);
  }

  private String generateHash(String apiKey, String timestamp) {
    String message = apiKey + timestamp;
    Mac mac = getMac();
    byte[] hmacBytes = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
    return Base64.getEncoder().encodeToString(hmacBytes);
  }
}
