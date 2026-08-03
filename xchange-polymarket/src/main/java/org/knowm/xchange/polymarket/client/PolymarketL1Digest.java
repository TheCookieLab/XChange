package org.knowm.xchange.polymarket.client;

import java.math.BigInteger;
import org.knowm.xchange.exceptions.ExchangeSecurityException;
import si.mazi.rescu.ParamsDigest;
import si.mazi.rescu.RestInvocation;

/**
 * Polymarket L1 signer: EIP-712 ClobAuth attestation over the sibling {@code POLY_TIMESTAMP} and
 * {@code POLY_NONCE} headers, used only by the API-key derivation endpoints. Key material is
 * never echoed into exceptions or logs.
 */
public final class PolymarketL1Digest implements ParamsDigest {

  /** Header whose resolved value anchors the attestation timestamp. */
  public static final String TIMESTAMP_HEADER = "POLY_TIMESTAMP";

  /** Header whose resolved value anchors the attestation nonce. */
  public static final String NONCE_HEADER = "POLY_NONCE";

  private final PolymarketEip712Signer signer;

  private PolymarketL1Digest(PolymarketEip712Signer signer) {
    this.signer = signer;
  }

  /**
   * @param privateKeyHex hex EOA private key, or {@code null}/blank to disable signing
   * @return the digest, or {@code null} when no key material was supplied
   */
  public static PolymarketL1Digest createInstance(String privateKeyHex) {
    if (privateKeyHex == null || privateKeyHex.isBlank()) {
      return null;
    }
    return new PolymarketL1Digest(PolymarketEip712Signer.fromPrivateKeyHex(privateKeyHex));
  }

  /** Wallet address derived from the configured key. */
  public String getAddress() {
    return signer.getAddress();
  }

  @Override
  public String digestParams(RestInvocation restInvocation) {
    String timestamp = restInvocation.getHttpHeadersFromParams().get(TIMESTAMP_HEADER);
    String nonceValue = restInvocation.getHttpHeadersFromParams().get(NONCE_HEADER);
    if (timestamp == null || nonceValue == null) {
      throw new ExchangeSecurityException(
          "Polymarket L1 signing requires POLY_TIMESTAMP and POLY_NONCE headers");
    }
    return signer.signClobAuth(timestamp, new BigInteger(nonceValue));
  }
}
