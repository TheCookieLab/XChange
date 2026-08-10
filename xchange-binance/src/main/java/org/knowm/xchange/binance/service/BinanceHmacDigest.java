package org.knowm.xchange.binance.service;

import static org.knowm.xchange.utils.DigestUtils.bytesToHex;

import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import org.knowm.xchange.binance.auth.BinanceSigning;
import org.knowm.xchange.service.BaseParamsDigest;
import si.mazi.rescu.RestInvocation;

public class BinanceHmacDigest extends BaseParamsDigest {

  private BinanceHmacDigest(String secretKeyBase64) {
    super(secretKeyBase64, HMAC_SHA_256);
  }

  public static BinanceHmacDigest createInstance(String secretKeyBase64) {
    return secretKeyBase64 == null ? null : new BinanceHmacDigest(secretKeyBase64);
  }

  @Override
  public String digestParams(RestInvocation restInvocation) {
    final String input = BinanceSigning.signingPayload(restInvocation);

    Mac mac = getMac();
    mac.update(input.getBytes(StandardCharsets.UTF_8));
    String printBase64Binary = bytesToHex(mac.doFinal());
    return printBase64Binary;
  }
}
