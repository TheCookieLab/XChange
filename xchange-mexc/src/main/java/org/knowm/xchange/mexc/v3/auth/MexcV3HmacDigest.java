package org.knowm.xchange.mexc.v3.auth;

import static org.knowm.xchange.utils.DigestUtils.bytesToHex;

import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import org.knowm.xchange.service.BaseParamsDigest;
import si.mazi.rescu.RestInvocation;

/**
 * HMAC-SHA256 request signer for MEXC Spot v3.
 *
 * <p>The digest is computed over {@link MexcV3Signing#signingPayload(RestInvocation)} and emitted
 * as lowercase hex, matching the provider's "signatures currently support lowercase only" rule.
 */
public class MexcV3HmacDigest extends BaseParamsDigest {

  private MexcV3HmacDigest(String secretKeyBase64) {
    super(secretKeyBase64, HMAC_SHA_256);
  }

  /** Creates a signer for the given Base64 secret key, or {@code null} when it is absent. */
  public static MexcV3HmacDigest createInstance(String secretKeyBase64) {
    return secretKeyBase64 == null ? null : new MexcV3HmacDigest(secretKeyBase64);
  }

  @Override
  public String digestParams(RestInvocation restInvocation) {
    final String input = MexcV3Signing.signingPayload(restInvocation);
    Mac mac = getMac();
    mac.update(input.getBytes(StandardCharsets.UTF_8));
    return bytesToHex(mac.doFinal());
  }
}
