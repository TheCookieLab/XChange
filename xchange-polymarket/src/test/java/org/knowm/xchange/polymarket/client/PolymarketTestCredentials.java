package org.knowm.xchange.polymarket.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.knowm.xchange.polymarket.dto.trade.PolymarketSignedOrder;

/**
 * Fixed test credentials plus request-signature verifiers shared by the wire-level tests. The key
 * material is throwaway and only ever used against WireMock.
 */
public final class PolymarketTestCredentials {

  /** Throwaway secp256k1 private key ({@code 0x0101…01}). */
  public static final String PRIVATE_KEY_HEX = "0x" + "01".repeat(32);

  /** Wallet address derived from {@link #PRIVATE_KEY_HEX}. */
  public static final String WALLET_ADDRESS =
      PolymarketEip712Signer.fromPrivateKeyHex(PRIVATE_KEY_HEX).getAddress();

  /** Throwaway L2 HMAC secret (32 zero bytes, url-safe base64). */
  public static final byte[] L2_SECRET_BYTES = new byte[32];

  public static final String L2_SECRET_BASE64 =
      Base64.getUrlEncoder().encodeToString(L2_SECRET_BYTES);

  public static final String API_KEY = "test-api-key-uuid";
  public static final String PASSPHRASE = "test-passphrase";

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private PolymarketTestCredentials() {}

  /**
   * Recomputes the L2 HMAC from the captured request and asserts it equals the {@code
   * POLY_SIGNATURE} header, proving the wire payload was {@code timestamp + method + path + body}.
   */
  public static void assertL2Signature(LoggedRequest request, String method) throws Exception {
    assertEquals(WALLET_ADDRESS, request.getHeader("POLY_ADDRESS"));
    assertEquals(API_KEY, request.getHeader("POLY_API_KEY"));
    assertEquals(PASSPHRASE, request.getHeader("POLY_PASSPHRASE"));
    String timestamp = request.getHeader("POLY_TIMESTAMP");
    assertNotNull(timestamp, "POLY_TIMESTAMP header required");

    String path = request.getUrl();
    int queryStart = path.indexOf('?');
    if (queryStart >= 0) {
      path = path.substring(0, queryStart);
    }
    StringBuilder payload = new StringBuilder().append(timestamp).append(method).append(path);
    String body = request.getBodyAsString();
    if (body != null && !body.isEmpty()) {
      payload.append(body);
    }
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(L2_SECRET_BYTES, "HmacSHA256"));
    String expected =
        Base64.getUrlEncoder()
            .encodeToString(mac.doFinal(payload.toString().getBytes(StandardCharsets.UTF_8)));
    assertEquals(
        expected, request.getHeader("POLY_SIGNATURE"), "POLY_SIGNATURE must cover " + payload);
  }

  /**
   * Rebuilds the unsigned order from a captured create-order body and asserts its EIP-712
   * signature recovers the test wallet.
   */
  public static void assertOrderSignature(String requestBody) throws Exception {
    JsonNode order = MAPPER.readTree(requestBody).get("order");
    PolymarketSignedOrder unsigned =
        new PolymarketSignedOrder(
            order.get("salt").asText(),
            order.get("maker").asText(),
            order.get("signer").asText(),
            order.get("tokenId").asText(),
            order.get("makerAmount").asText(),
            order.get("takerAmount").asText(),
            order.get("side").asText(),
            order.get("expiration").asText(),
            order.get("timestamp").asText(),
            order.get("signatureType").asInt(),
            order.hasNonNull("metadata") ? order.get("metadata").asText() : null,
            order.get("builder").asText(),
            null);
    String recovered =
        PolymarketEip712Signer.recoverOrderSigner(unsigned, order.get("signature").asText());
    assertEquals(
        WALLET_ADDRESS.toLowerCase(),
        recovered.toLowerCase(),
        "order EIP-712 signature must recover the test wallet");
  }

  /**
   * Asserts the L1 ClobAuth signature on a captured derive-api-key request recovers the test
   * wallet from the {@code POLY_TIMESTAMP}/{@code POLY_NONCE} headers.
   */
  public static void assertL1Signature(LoggedRequest request) {
    assertEquals(WALLET_ADDRESS, request.getHeader("POLY_ADDRESS"));
    String recovered =
        PolymarketEip712Signer.recoverClobAuthSigner(
            request.getHeader("POLY_ADDRESS"),
            request.getHeader("POLY_TIMESTAMP"),
            new BigInteger(request.getHeader("POLY_NONCE")),
            request.getHeader("POLY_SIGNATURE"));
    assertEquals(
        WALLET_ADDRESS.toLowerCase(),
        recovered.toLowerCase(),
        "L1 ClobAuth signature must recover the test wallet");
  }
}
