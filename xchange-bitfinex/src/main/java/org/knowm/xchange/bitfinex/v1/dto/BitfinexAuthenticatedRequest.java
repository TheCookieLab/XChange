package org.knowm.xchange.bitfinex.v1.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;

/**
 * Base of every Bitfinex v1 authenticated request body.
 *
 * <p>Bitfinex requires the nonce to strictly increase per API key, and the nonce factory is shared
 * with the v2 calls of the same exchange, so a nonce taken while a request is still waiting in rate
 * limit admission would be older than the nonces sent meanwhile. The nonce is therefore never set
 * by the caller: {@link org.knowm.xchange.bitfinex.service.BitfinexPayloadDigest} stamps it when
 * the admitted wire attempt is signed, immediately before the body is serialized, payload-digested,
 * signed and sent.
 */
@EqualsAndHashCode
public abstract class BitfinexAuthenticatedRequest {

  @JsonProperty("nonce")
  private String nonce;

  public String getNonce() {
    return nonce;
  }

  public void setNonce(String nonce) {
    this.nonce = nonce;
  }
}
