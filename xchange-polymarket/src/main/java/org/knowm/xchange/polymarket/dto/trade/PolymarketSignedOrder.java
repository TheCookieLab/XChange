package org.knowm.xchange.polymarket.dto.trade;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Signed-order payload nested in the create-order request. {@code makerAmount} and {@code
 * takerAmount} are 6-decimal fixed-point integer strings, {@code timestamp} is unix milliseconds,
 * and {@code salt} a caller-controlled integer that scopes retry identity. Instances built by
 * {@code PolymarketAdapters.toSignedOrder} are unsigned; the trade service attaches the EIP-712
 * signature via {@link #withSignature(String)}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PolymarketSignedOrder(
    @JsonProperty("salt") String salt,
    @JsonProperty("maker") String maker,
    @JsonProperty("signer") String signer,
    @JsonProperty("tokenId") String tokenId,
    @JsonProperty("makerAmount") String makerAmount,
    @JsonProperty("takerAmount") String takerAmount,
    @JsonProperty("side") String side,
    @JsonProperty("expiration") String expiration,
    @JsonProperty("timestamp") String timestamp,
    @JsonProperty("signatureType") Integer signatureType,
    @JsonProperty("metadata") String metadata,
    @JsonProperty("builder") String builder,
    @JsonProperty("signature") String signature) {

  /** Returns a copy with the EIP-712 signature attached. */
  public PolymarketSignedOrder withSignature(String signatureHex) {
    return new PolymarketSignedOrder(
        salt, maker, signer, tokenId, makerAmount, takerAmount, side, expiration, timestamp,
        signatureType, metadata, builder, signatureHex);
  }
}
