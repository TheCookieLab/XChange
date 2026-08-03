package org.knowm.xchange.polymarket.client;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.util.Arrays;
import org.bouncycastle.asn1.sec.SECNamedCurves;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.digests.KeccakDigest;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.signers.ECDSASigner;
import org.bouncycastle.crypto.signers.HMacDSAKCalculator;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.util.BigIntegers;
import org.knowm.xchange.exceptions.ExchangeSecurityException;
import org.knowm.xchange.polymarket.dto.trade.PolymarketSignedOrder;

/**
 * Minimal EIP-712 typed-data signer for Polymarket CLOB auth and order messages.
 *
 * <p>Implements exactly the two signed structures the CLOB requires, with BouncyCastle
 * Keccak-256 and RFC6979-deterministic secp256k1 ECDSA:
 *
 * <ul>
 *   <li>{@code ClobAuth(address address, string timestamp, uint256 nonce, string message)} under
 *       domain {@code ClobAuthDomain} v1 on chain 137 (L1 credential derivation).
 *   <li>{@code Order(uint256 salt, address maker, address signer, uint256 tokenId, uint256
 *       makerAmount, uint256 takerAmount, uint8 side, uint8 signatureType, uint256 timestamp,
 *       bytes32 metadata, bytes32 builder)} under domain {@code Polymarket CTF Exchange} v2 on
 *       chain 137 (order placement).
 * </ul>
 *
 * <p>Private-key material never appears in exception messages or logs (redaction is covered by
 * tests).
 */
public final class PolymarketEip712Signer {

  /** Fixed attestation string of the ClobAuth struct. */
  public static final String CLOB_AUTH_MESSAGE =
      "This message attests that I control the given wallet";

  /** Verifying contract of the standard-market exchange domain (negative risk unsupported). */
  public static final String CTF_EXCHANGE_ADDRESS = "0xE111180000d2663C0091e4f400237545B87B996B";

  /** Signature type value for a plain EOA signer. */
  public static final int SIGNATURE_TYPE_EOA = 0;

  private static final ECDomainParameters SECP256K1;
  private static final BigInteger HALF_N;

  private static final byte[] DOMAIN_TYPEHASH =
      keccak256(
          "EIP712Domain(string name,string version,uint256 chainId,address verifyingContract)"
              .getBytes(StandardCharsets.UTF_8));
  private static final byte[] DOMAIN_TYPEHASH_NO_CONTRACT =
      keccak256(
          "EIP712Domain(string name,string version,uint256 chainId)"
              .getBytes(StandardCharsets.UTF_8));
  private static final byte[] CLOB_AUTH_TYPEHASH =
      keccak256(
          "ClobAuth(address address,string timestamp,uint256 nonce,string message)"
              .getBytes(StandardCharsets.UTF_8));
  private static final byte[] ORDER_TYPEHASH =
      keccak256(
          ("Order(uint256 salt,address maker,address signer,uint256 tokenId,uint256 makerAmount,"
                  + "uint256 takerAmount,uint8 side,uint8 signatureType,uint256 timestamp,"
                  + "bytes32 metadata,bytes32 builder)")
              .getBytes(StandardCharsets.UTF_8));

  private static final byte[] AUTH_DOMAIN_SEPARATOR =
      domainSeparator(
          DOMAIN_TYPEHASH_NO_CONTRACT,
          keccak256("ClobAuthDomain".getBytes(StandardCharsets.UTF_8)),
          keccak256("1".getBytes(StandardCharsets.UTF_8)),
          null);
  private static final byte[] ORDER_DOMAIN_SEPARATOR =
      domainSeparator(
          DOMAIN_TYPEHASH,
          keccak256("Polymarket CTF Exchange".getBytes(StandardCharsets.UTF_8)),
          keccak256("2".getBytes(StandardCharsets.UTF_8)),
          addressWord(CTF_EXCHANGE_ADDRESS));

  static {
    X9ECParameters params = SECNamedCurves.getByName("secp256k1");
    SECP256K1 = new ECDomainParameters(params.getCurve(), params.getG(), params.getN(), params.getH());
    HALF_N = params.getN().shiftRight(1);
    if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
      Security.addProvider(new BouncyCastleProvider());
    }
  }

  private final BigInteger privateKey;
  private final byte[] address;

  private PolymarketEip712Signer(BigInteger privateKey) {
    this.privateKey = privateKey;
    this.address = deriveAddress(privateKey);
  }

  /**
   * Creates a signer from a hex-encoded secp256k1 private key. The key material is never echoed
   * into exception messages.
   *
   * @param privateKeyHex 32-byte hex key, with or without {@code 0x} prefix
   */
  public static PolymarketEip712Signer fromPrivateKeyHex(String privateKeyHex) {
    if (privateKeyHex == null || privateKeyHex.isBlank()) {
      throw new ExchangeSecurityException(
          "Polymarket signing requires a hex EOA private key in exchange-specific parameter '"
              + org.knowm.xchange.polymarket.PolymarketExchange.PARAM_PRIVATE_KEY
              + "'");
    }
    String hex = privateKeyHex.startsWith("0x") ? privateKeyHex.substring(2) : privateKeyHex;
    final BigInteger d;
    try {
      d = new BigInteger(hex, 16);
    } catch (NumberFormatException e) {
      throw new ExchangeSecurityException("Polymarket private key is not valid hex", e);
    }
    if (d.signum() <= 0 || d.compareTo(SECP256K1.getN()) >= 0 || hex.length() > 64) {
      throw new ExchangeSecurityException(
          "Polymarket private key is outside the secp256k1 scalar range");
    }
    return new PolymarketEip712Signer(d);
  }

  /** Wallet address derived from the private key, {@code 0x}-prefixed hex. */
  public String getAddress() {
    return "0x" + toHex(address);
  }

  /**
   * Signs the L1 ClobAuth attestation for API-key derivation.
   *
   * @param timestampSeconds unix seconds string carried in the {@code POLY_TIMESTAMP} header
   * @param nonce nonce carried in the {@code POLY_NONCE} header
   * @return {@code 0x}-prefixed EIP-712 signature (r ‖ s ‖ v)
   */
  public String signClobAuth(String timestampSeconds, BigInteger nonce) {
    byte[] structHash =
        keccak256(
            concat(
                CLOB_AUTH_TYPEHASH,
                addressWord(getAddress()),
                keccak256(timestampSeconds.getBytes(StandardCharsets.UTF_8)),
                word(nonce),
                keccak256(CLOB_AUTH_MESSAGE.getBytes(StandardCharsets.UTF_8))));
    return sign(AUTH_DOMAIN_SEPARATOR, structHash);
  }

  /**
   * Signs an unsigned order built by {@code PolymarketAdapters}.
   *
   * @param order order fields as they will be submitted (sans signature)
   * @return {@code 0x}-prefixed EIP-712 signature (r ‖ s ‖ v)
   */
  public String signOrder(PolymarketSignedOrder order) {
    byte[] digest = orderDigest(order);
    return signDigest(digest);
  }

  private static byte[] orderDigest(PolymarketSignedOrder order) {
    byte[] structHash =
        keccak256(
            concat(
                ORDER_TYPEHASH,
                word(new BigInteger(order.salt())),
                addressWord(order.maker()),
                addressWord(order.signer()),
                word(new BigInteger(order.tokenId())),
                word(new BigInteger(order.makerAmount())),
                word(new BigInteger(order.takerAmount())),
                word(BigInteger.valueOf("BUY".equals(order.side()) ? 0 : 1)),
                word(BigInteger.valueOf(order.signatureType())),
                word(new BigInteger(order.timestamp())),
                bytes32(order.metadata()),
                bytes32(order.builder())));
    return eip712Digest(ORDER_DOMAIN_SEPARATOR, structHash);
  }

  private String sign(byte[] domainSeparator, byte[] structHash) {
    return signDigest(eip712Digest(domainSeparator, structHash));
  }

  private String signDigest(byte[] digest) {
    ECDSASigner signer = new ECDSASigner(new HMacDSAKCalculator(new SHA256Digest()));
    signer.init(true, new ECPrivateKeyParameters(privateKey, SECP256K1));
    BigInteger[] rs = signer.generateSignature(digest);
    BigInteger r = rs[0];
    BigInteger s = rs[1];
    if (s.compareTo(HALF_N) > 0) {
      s = SECP256K1.getN().subtract(s);
    }
    int recId = -1;
    for (int candidate = 0; candidate < 2; candidate++) {
      byte[] recovered = recoverAddress(digest, r, s, candidate);
      if (Arrays.equals(recovered, address)) {
        recId = candidate;
        break;
      }
    }
    if (recId < 0) {
      throw new ExchangeSecurityException(
          "Polymarket signing failed: could not recover signer address");
    }
    return "0x" + toHex(BigIntegers.asUnsignedByteArray(32, r))
        + toHex(BigIntegers.asUnsignedByteArray(32, s))
        + String.format("%02x", 27 + recId);
  }

  /** Recovers the signer address of a ClobAuth signature; package-private test seam. */
  static String recoverClobAuthSigner(
      String expectedAddress, String timestampSeconds, BigInteger nonce, String signatureHex) {
    byte[] structHash =
        keccak256(
            concat(
                CLOB_AUTH_TYPEHASH,
                addressWord(expectedAddress),
                keccak256(timestampSeconds.getBytes(StandardCharsets.UTF_8)),
                word(nonce),
                keccak256(CLOB_AUTH_MESSAGE.getBytes(StandardCharsets.UTF_8))));
    return recoverSigner(eip712Digest(AUTH_DOMAIN_SEPARATOR, structHash), signatureHex);
  }

  /** Recovers the signer address of an order signature; package-private test seam. */
  static String recoverOrderSigner(PolymarketSignedOrder order, String signatureHex) {
    return recoverSigner(orderDigest(order), signatureHex);
  }

  private static String recoverSigner(byte[] digest, String signatureHex) {
    byte[] sig = fromHex(signatureHex.startsWith("0x") ? signatureHex.substring(2) : signatureHex);
    if (sig.length != 65) {
      throw new IllegalArgumentException("Expected 65-byte r‖s‖v signature");
    }
    BigInteger r = new BigInteger(1, Arrays.copyOfRange(sig, 0, 32));
    BigInteger s = new BigInteger(1, Arrays.copyOfRange(sig, 32, 64));
    int v = sig[64] & 0xff;
    int recId = (v >= 27 ? v - 27 : v) & 1;
    byte[] address = recoverAddress(digest, r, s, recId);
    return address == null ? null : "0x" + toHex(address);
  }

  private static byte[] recoverAddress(byte[] digest, BigInteger r, BigInteger s, int recId) {
    try {
      BigInteger x = r;
      byte[] encoded = new byte[33];
      encoded[0] = (byte) (0x02 | (recId & 1));
      System.arraycopy(BigIntegers.asUnsignedByteArray(32, x), 0, encoded, 1, 32);
      ECPoint pointR = SECP256K1.getCurve().decodePoint(encoded);
      BigInteger e = new BigInteger(1, digest);
      BigInteger rInv = r.modInverse(SECP256K1.getN());
      ECPoint pointQ =
          pointR
              .multiply(s)
              .subtract(SECP256K1.getG().multiply(e))
              .multiply(rInv)
              .normalize();
      byte[] publicKey = pointQ.getEncoded(false);
      byte[] hash = keccak256(Arrays.copyOfRange(publicKey, 1, 65));
      return Arrays.copyOfRange(hash, 12, 32);
    } catch (RuntimeException e) {
      return null;
    }
  }

  private static byte[] deriveAddress(BigInteger d) {
    ECPoint q = SECP256K1.getG().multiply(d).normalize();
    byte[] publicKey = q.getEncoded(false);
    byte[] hash = keccak256(Arrays.copyOfRange(publicKey, 1, 65));
    return Arrays.copyOfRange(hash, 12, 32);
  }

  private static byte[] eip712Digest(byte[] domainSeparator, byte[] structHash) {
    return keccak256(concat(new byte[] {0x19, 0x01}, domainSeparator, structHash));
  }

  private static byte[] domainSeparator(
      byte[] typehash, byte[] nameHash, byte[] versionHash, byte[] verifyingContractWord) {
    byte[] chainWord = word(BigInteger.valueOf(137));
    return verifyingContractWord == null
        ? keccak256(concat(typehash, nameHash, versionHash, chainWord))
        : keccak256(concat(typehash, nameHash, versionHash, chainWord, verifyingContractWord));
  }

  static byte[] keccak256(byte[] input) {
    KeccakDigest digest = new KeccakDigest(256);
    digest.update(input, 0, input.length);
    byte[] out = new byte[32];
    digest.doFinal(out, 0);
    return out;
  }

  private static byte[] word(BigInteger value) {
    return BigIntegers.asUnsignedByteArray(32, value);
  }

  private static byte[] addressWord(String addressHex) {
    String hex = addressHex.startsWith("0x") ? addressHex.substring(2) : addressHex;
    byte[] address = fromHex(hex);
    if (address.length != 20) {
      throw new IllegalArgumentException("Expected 20-byte address, got " + hex.length() / 2);
    }
    byte[] word = new byte[32];
    System.arraycopy(address, 0, word, 12, 20);
    return word;
  }

  private static byte[] bytes32(String hex) {
    if (hex == null) {
      return new byte[32];
    }
    String clean = hex.startsWith("0x") ? hex.substring(2) : hex;
    byte[] value = fromHex(clean);
    if (value.length != 32) {
      throw new IllegalArgumentException("Expected 32-byte value, got " + value.length);
    }
    return value;
  }

  private static byte[] concat(byte[]... parts) {
    int length = 0;
    for (byte[] part : parts) {
      length += part.length;
    }
    byte[] out = new byte[length];
    int offset = 0;
    for (byte[] part : parts) {
      System.arraycopy(part, 0, out, offset, part.length);
      offset += part.length;
    }
    return out;
  }

  private static byte[] fromHex(String hex) {
    int length = hex.length();
    byte[] out = new byte[length / 2];
    for (int i = 0; i < length; i += 2) {
      out[i / 2] = (byte) Integer.parseInt(hex.substring(i, i + 2), 16);
    }
    return out;
  }

  private static String toHex(byte[] bytes) {
    StringBuilder sb = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) {
      sb.append(Character.forDigit((b >> 4) & 0xf, 16));
      sb.append(Character.forDigit(b & 0xf, 16));
    }
    return sb.toString();
  }
}
