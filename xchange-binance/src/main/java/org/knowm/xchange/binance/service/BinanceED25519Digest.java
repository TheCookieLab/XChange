package org.knowm.xchange.binance.service;

import java.security.PrivateKey;
import org.knowm.xchange.binance.auth.BinanceSigning;
import si.mazi.rescu.ParamsDigest;
import si.mazi.rescu.RestInvocation;

public class BinanceED25519Digest implements ParamsDigest {

  private final PrivateKey privateKey;

  private BinanceED25519Digest(String secretKeyBase64) {
    this.privateKey = BinanceEd25519Signer.parsePrivateKey(secretKeyBase64);
  }

  public static BinanceED25519Digest createInstance(String secretKeyBase64) {
    return secretKeyBase64 == null ? null : new BinanceED25519Digest(secretKeyBase64);
  }

  @Override
  public String digestParams(RestInvocation restInvocation) {
    final String input = BinanceSigning.signingPayload(restInvocation);
    return BinanceEd25519Signer.signBase64(privateKey, input);
  }
}
