package org.knowm.xchange.polymarket.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.exceptions.ExchangeSecurityException;
import org.knowm.xchange.polymarket.dto.trade.PolymarketSignedOrder;

/**
 * EIP-712 signer tests: a pinned address vector, RFC6979 determinism, recovery round trips, and
 * key-material redaction.
 */
class PolymarketEip712SignerTest {

  /** Well-known secp256k1 vector: private key 1 derives this address. */
  private static final String PRIVATE_KEY_ONE =
      "0x0000000000000000000000000000000000000000000000000000000000000001";

  private static final String ADDRESS_ONE = "0x7e5f4552091a69125d5dfcb7b8c2659029395bdf";

  private final PolymarketEip712Signer signer =
      PolymarketEip712Signer.fromPrivateKeyHex(PolymarketTestCredentials.PRIVATE_KEY_HEX);

  @Test
  void derivesKnownAddressVector() {
    assertEquals(
        ADDRESS_ONE, PolymarketEip712Signer.fromPrivateKeyHex(PRIVATE_KEY_ONE).getAddress());
  }

  @Test
  void orderSigningIsDeterministicAndRecoversSigner() {
    PolymarketSignedOrder order = unsignedOrder();
    String first = signer.signOrder(order);
    String second = signer.signOrder(order);
    assertEquals(first, second, "RFC6979 signing must be deterministic");
    assertEquals(
        signer.getAddress(), PolymarketEip712Signer.recoverOrderSigner(order, first));
  }

  @Test
  void clobAuthSignatureRecoversSigner() {
    String signature = signer.signClobAuth("1754230000", BigInteger.ZERO);
    assertEquals(
        signer.getAddress(),
        PolymarketEip712Signer.recoverClobAuthSigner(
            signer.getAddress(), "1754230000", BigInteger.ZERO, signature));
  }

  @Test
  void rejectsMalformedKeyWithoutEchoingMaterial() {
    String garbage = "0xZZZZnot-hex-material";
    ExchangeSecurityException e =
        assertThrows(
            ExchangeSecurityException.class, () -> PolymarketEip712Signer.fromPrivateKeyHex(garbage));
    assertFalse(e.getMessage().contains(garbage), "key material must be redacted");
  }

  @Test
  void rejectsOutOfRangeKeyWithoutEchoingMaterial() {
    String tooLarge = "0x" + "ff".repeat(32);
    ExchangeSecurityException e =
        assertThrows(
            ExchangeSecurityException.class,
            () -> PolymarketEip712Signer.fromPrivateKeyHex(tooLarge));
    assertFalse(e.getMessage().contains(tooLarge), "key material must be redacted");
  }

  @Test
  void rejectsMissingKeyNamingOnlyTheParameter() {
    ExchangeSecurityException e =
        assertThrows(
            ExchangeSecurityException.class, () -> PolymarketEip712Signer.fromPrivateKeyHex(null));
    assertEquals(
        "Polymarket signing requires a hex EOA private key in exchange-specific parameter"
            + " 'polymarket.private.key'",
        e.getMessage());
  }

  private static PolymarketSignedOrder unsignedOrder() {
    return new PolymarketSignedOrder(
        "12345",
        PolymarketTestCredentials.WALLET_ADDRESS,
        PolymarketTestCredentials.WALLET_ADDRESS,
        "713210456792522125",
        "5600000",
        "10000000",
        "BUY",
        "0",
        "1754230000000",
        PolymarketEip712Signer.SIGNATURE_TYPE_EOA,
        null,
        "0x" + "00".repeat(32),
        null);
  }
}
