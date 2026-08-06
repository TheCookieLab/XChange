package org.knowm.xchange.polymarket.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.exceptions.ExchangeSecurityException;
import org.knowm.xchange.polymarket.dto.trade.PolymarketSignedOrder;

/**
 * EIP-712 signer tests: golden vectors produced by independent implementations, a pinned address
 * vector, RFC6979 determinism, recovery round trips, and key-material redaction.
 *
 * <p>Golden vectors (not self-consistent round trips):
 *
 * <ul>
 *   <li>{@link #CLOB_AUTH_SIGNATURE} / {@link #CLOB_AUTH_DIGEST} — produced by the official
 *       py-clob-client (https://github.com/Polymarket/py-clob-client) for the publicly known key
 *       {@code 0xac09...ff80}, timestamp {@code 10000000}, nonce {@code 23} on the production
 *       chain 137. The identical key/timestamp/nonce vector for chain 80002 is published in
 *       {@code tests/signing/test_eip712.py}
 *       (https://github.com/Polymarket/py-clob-client/blob/main/tests/signing/test_eip712.py); the
 *       pinned production values were generated with the same official client and chain id 137
 *       (https://docs.polymarket.com/resources/contracts: all contracts are deployed on Polygon,
 *       chain ID 137).
 *   <li>{@link #ORDER_STANDARD_SIGNATURE} / {@link #ORDER_STANDARD_DIGEST} — computed with the
 *       independent {@code eth_account} library from the published CTF Exchange V2 constants:
 *       order typehash from
 *       https://github.com/Polymarket/ctf-exchange-v2/blob/main/src/exchange/libraries/Structs.sol,
 *       domain "Polymarket CTF Exchange" v2 chain 137 (Hashing.sol), verifying contract
 *       {@code 0xE111...B996B} (repo README).
 *   <li>{@link #ORDER_NEG_RISK_SIGNATURE} / {@link #ORDER_NEG_RISK_DIGEST} — same computation with
 *       the NegRisk CTF Exchange verifying contract {@code 0xe222...0F59} (same source repo).
 * </ul>
 */
class PolymarketEip712SignerTest {

  /** Well-known secp256k1 vector: private key 1 derives this address. */
  private static final String PRIVATE_KEY_ONE =
      "0x0000000000000000000000000000000000000000000000000000000000000001";

  private static final String ADDRESS_ONE = "0x7e5f4552091a69125d5dfcb7b8c2659029395bdf";

  /** Publicly known test key used by the official py-clob-client test suite (Anvil account 0). */
  private static final String PUBLISHED_KEY =
      "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";

  private static final String PUBLISHED_KEY_ADDRESS = "0xf39fd6e51aad88f6f4ce6ab8827279cfffb92266";

  /** Published order typehash constant from CTF Exchange V2 Structs.sol. */
  private static final String PUBLISHED_ORDER_TYPEHASH =
      "0xbb86318a2138f5fa8ae32fbe8e659f8fcf13cc6ae4014a707893055433818589";

  /** ClobAuth production (chain 137) digest and signature from the official py-clob-client. */
  private static final String CLOB_AUTH_DIGEST =
      "0x577908bf2b712def8592d5078487f59ab9aa28b656e6fda011c8ac4333792037";
  private static final String CLOB_AUTH_SIGNATURE =
      "0x1a7118db6100dfd8efd102be36f472b59475dcac56eb4c9a2a94748d3655ba7c"
          + "3c89deb8c19ee79eceb0a531122fbfbe88ed118034f9d8212e2b725e7b296b9d1c";

  /** Standard-domain order digest and signature (eth_account, published contract constants). */
  private static final String ORDER_STANDARD_DIGEST =
      "0x3962d3bd548361819754cb725c8d94642fe9bae68ca0691c0129f149bf8643c1";
  private static final String ORDER_STANDARD_SIGNATURE =
      "0x5c586a0a09b90baf0bf517358bffb4d911597f04d914f56a2673787f49a7a257"
          + "20beb68700cb1075e211c67c3d064fd7c863e4dcc4a236b3d5e6f7e5d8a8126c1c";

  /** Negative-risk domain order digest and signature (eth_account, published contract constants). */
  private static final String ORDER_NEG_RISK_DIGEST =
      "0xe741228799f74f0cc622a7e41f54846e857d82d77b15debc3679375ed3183251";
  private static final String ORDER_NEG_RISK_SIGNATURE =
      "0xbaa141e29bf05e4e5b38cea4b6bbc6fbd75eac18064bdd0cf79f168321a902ec"
          + "14977ae3961eb71ee622f0f9ccb15042c98be78df335dbd45a39ee372604ea471c";

  private final PolymarketEip712Signer signer =
      PolymarketEip712Signer.fromPrivateKeyHex(PolymarketTestCredentials.PRIVATE_KEY_HEX);

  @Test
  void derivesKnownAddressVector() {
    assertEquals(
        ADDRESS_ONE, PolymarketEip712Signer.fromPrivateKeyHex(PRIVATE_KEY_ONE).getAddress());
  }

  @Test
  void orderTypehashMatchesThePublishedContractConstant() {
    // https://github.com/Polymarket/ctf-exchange-v2/blob/main/src/exchange/libraries/Structs.sol
    assertEquals(PUBLISHED_ORDER_TYPEHASH, PolymarketEip712Signer.orderTypehashHex());
  }

  @Test
  void clobAuthMatchesOfficialPyClientVector() {
    PolymarketEip712Signer publishedKeySigner =
        PolymarketEip712Signer.fromPrivateKeyHex(PUBLISHED_KEY);
    assertEquals(PUBLISHED_KEY_ADDRESS, publishedKeySigner.getAddress().toLowerCase());
    assertEquals(
        CLOB_AUTH_DIGEST,
        PolymarketEip712Signer.clobAuthDigestHex(
            publishedKeySigner.getAddress(), "10000000", BigInteger.valueOf(23)),
        "digest must match the official client byte for byte");
    assertEquals(
        CLOB_AUTH_SIGNATURE,
        publishedKeySigner.signClobAuth("10000000", BigInteger.valueOf(23)),
        "signature must match the official client byte for byte");
  }

  @Test
  void orderStandardDomainMatchesIndependentVector() {
    PolymarketEip712Signer publishedKeySigner =
        PolymarketEip712Signer.fromPrivateKeyHex(PUBLISHED_KEY);
    PolymarketSignedOrder order =
        publishedOrder(publishedKeySigner.getAddress(), Boolean.FALSE);
    assertEquals(
        ORDER_STANDARD_DIGEST,
        PolymarketEip712Signer.orderDigestHex(order),
        "digest must match the independent computation byte for byte");
    assertEquals(
        ORDER_STANDARD_SIGNATURE,
        publishedKeySigner.signOrder(order),
        "signature must match the independent computation byte for byte");
  }

  @Test
  void orderNegRiskDomainMatchesIndependentVector() {
    PolymarketEip712Signer publishedKeySigner =
        PolymarketEip712Signer.fromPrivateKeyHex(PUBLISHED_KEY);
    PolymarketSignedOrder order =
        publishedOrder(publishedKeySigner.getAddress(), Boolean.TRUE);
    assertEquals(
        ORDER_NEG_RISK_DIGEST,
        PolymarketEip712Signer.orderDigestHex(order),
        "neg-risk digest must use the NegRisk verifying contract");
    assertEquals(
        ORDER_NEG_RISK_SIGNATURE,
        publishedKeySigner.signOrder(order),
        "neg-risk signature must match the independent computation byte for byte");
  }

  @Test
  void standardAndNegRiskDomainsProduceDifferentSignatures() {
    PolymarketEip712Signer publishedKeySigner =
        PolymarketEip712Signer.fromPrivateKeyHex(PUBLISHED_KEY);
    assertFalse(
        ORDER_STANDARD_SIGNATURE.equals(ORDER_NEG_RISK_SIGNATURE),
        "different verifying contracts must yield different digests and signatures");
    assertEquals(PUBLISHED_KEY_ADDRESS, publishedKeySigner.getAddress().toLowerCase());
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
  void negRiskOrderRecoversUnderItsOwnDomain() {
    PolymarketSignedOrder order =
        new PolymarketSignedOrder(
            unsignedOrder().salt(),
            unsignedOrder().maker(),
            unsignedOrder().signer(),
            unsignedOrder().tokenId(),
            unsignedOrder().makerAmount(),
            unsignedOrder().takerAmount(),
            unsignedOrder().side(),
            unsignedOrder().expiration(),
            unsignedOrder().timestamp(),
            unsignedOrder().signatureType(),
            unsignedOrder().metadata(),
            unsignedOrder().builder(),
            null,
            Boolean.TRUE);
    String signature = signer.signOrder(order);
    assertEquals(signer.getAddress(), PolymarketEip712Signer.recoverOrderSigner(order, signature));
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

  private static PolymarketSignedOrder publishedOrder(String address, Boolean negRisk) {
    return new PolymarketSignedOrder(
        "12345",
        address,
        address,
        "713210456792522125",
        "5600000",
        "10000000",
        "BUY",
        "0",
        "1754230000000",
        PolymarketEip712Signer.SIGNATURE_TYPE_EOA,
        null,
        "0x" + "00".repeat(32),
        null,
        negRisk);
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
        null,
        Boolean.FALSE);
  }
}
