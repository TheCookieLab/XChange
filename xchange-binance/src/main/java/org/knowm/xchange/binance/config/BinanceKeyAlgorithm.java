package org.knowm.xchange.binance.config;

/**
 * The private-key algorithms Binance accepts for request signing.
 *
 * <p>HMAC-SHA256 signs the payload with the shared secret and emits a lowercase hex digest. RSA
 * signs with an RSA private key (SHA256withRSA) and emits base64. Ed25519 signs with an Ed25519
 * private key (PKCS#8) and emits base64.
 */
public enum BinanceKeyAlgorithm {
  HMAC_SHA_256,
  RSA,
  ED25519
}
