package org.knowm.xchange.uniswap.protocol;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.knowm.xchange.uniswap.util.Addresses;
import org.web3j.abi.datatypes.Type;

/**
 * Minimal deterministic ABI (Solidity 0.8) encoder for the fixed layouts the Uniswap module needs.
 *
 * <p>Kept deliberately small: every primitive here is exercised by golden fixtures in tests so the
 * exact bytes a swap transaction carries are pinned and reviewable.
 */
public final class Abi {

  /** 32-byte word size. */
  public static final int WORD = 32;

  private Abi() {}

  /** A single 32-byte word holding a big-endian unsigned value. */
  public static byte[] word(BigInteger value) {
    if (value.signum() < 0) {
      throw new IllegalArgumentException("negative ABI value: " + value);
    }
    byte[] bytes = value.toByteArray();
    if (bytes.length > WORD) {
      throw new IllegalArgumentException("ABI value does not fit in 32 bytes: " + value);
    }
    byte[] out = new byte[WORD];
    // toByteArray may include a sign byte; copy the least significant bytes
    System.arraycopy(bytes, 0, out, WORD - bytes.length, bytes.length);
    return out;
  }

  /** A single 32-byte word holding a long value. */
  public static byte[] word(long value) {
    return word(BigInteger.valueOf(value));
  }

  /** A single 32-byte word holding a boolean. */
  public static byte[] bool(boolean value) {
    return word(value ? 1 : 0);
  }

  /** A single 32-byte word holding an address. */
  public static byte[] address(String normalizedAddress) {
    byte[] out = new byte[WORD];
    byte[] addressBytes = hexToBytes(Addresses.requireValidAddress(normalizedAddress));
    System.arraycopy(addressBytes, 0, out, WORD - addressBytes.length, addressBytes.length);
    return out;
  }

  /** ABI-encodes a dynamic {@code bytes} value: an offset word is supplied by the caller. */
  public static byte[] dynamicBytes(byte[] data) {
    byte[] padded = new byte[WORD + ((data.length + WORD - 1) / WORD) * WORD];
    System.arraycopy(word(data.length), 0, padded, 0, WORD);
    System.arraycopy(data, 0, padded, WORD, data.length);
    return padded;
  }

  /**
   * ABI-encodes a {@code bytes[]} value: an offset word is supplied by the caller. Each element is
   * itself a dynamic {@code bytes} value. Offsets are relative to the start of the array encoding
   * (its length word), as the ABI specification requires.
   */
  public static byte[] dynamicBytesArray(List<byte[]> elements) {
    int[] lengths = new int[elements.size()];
    int total = 0;
    for (int i = 0; i < elements.size(); i++) {
      lengths[i] = WORD + ((elements.get(i).length + WORD - 1) / WORD) * WORD;
      total += lengths[i];
    }
    byte[] out = new byte[WORD + elements.size() * WORD + total];
    System.arraycopy(word(elements.size()), 0, out, 0, WORD);
    int offset = WORD + elements.size() * WORD;
    for (int i = 0; i < elements.size(); i++) {
      System.arraycopy(word(offset), 0, out, WORD + i * WORD, WORD);
      offset += lengths[i];
    }
    int cursor = WORD + elements.size() * WORD;
    for (int i = 0; i < elements.size(); i++) {
      byte[] element = dynamicBytes(elements.get(i));
      System.arraycopy(element, 0, out, cursor, element.length);
      cursor += lengths[i];
    }
    return out;
  }

  /** Concatenates chunks into one byte array. */
  public static byte[] concat(byte[]... chunks) {
    int total = 0;
    for (byte[] chunk : chunks) {
      total += chunk.length;
    }
    byte[] out = new byte[total];
    int cursor = 0;
    for (byte[] chunk : chunks) {
      System.arraycopy(chunk, 0, out, cursor, chunk.length);
      cursor += chunk.length;
    }
    return out;
  }

  /** Selector (first four bytes of the Keccak-256 hash) of a function signature. */
  public static byte[] selector(String signature) {
    byte[] digest = org.web3j.crypto.Hash.sha3(signature.getBytes(StandardCharsets.US_ASCII));
    byte[] out = new byte[4];
    System.arraycopy(digest, 0, out, 0, 4);
    return out;
  }

  /** Parses {@code 0x…} or bare hex into bytes. */
  public static byte[] hexToBytes(String hex) {
    String cleaned = hex;
    if (cleaned.startsWith("0x")) {
      cleaned = cleaned.substring(2);
    }
    if ((cleaned.length() & 1) != 0) {
      throw new IllegalArgumentException("odd-length hex string");
    }
    byte[] out = new byte[cleaned.length() / 2];
    for (int i = 0; i < out.length; i++) {
      out[i] = (byte) Integer.parseInt(cleaned.substring(i * 2, i * 2 + 2), 16);
    }
    return out;
  }

  /** Hex-encodes bytes with a {@code 0x} prefix. */
  public static String toHex(byte[] bytes) {
    return "0x" + org.web3j.utils.Numeric.toHexStringNoPrefix(bytes);
  }

  /**
   * Builds the {@code List<TypeReference<Type>>} that {@code FunctionReturnDecoder} expects from
   * typed references; the cast is unchecked because the reference classes carry their generic type
   * at runtime regardless of the declared static type.
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  public static List<org.web3j.abi.TypeReference<Type>> typeReferences(org.web3j.abi.TypeReference<?>... refs) {
    return java.util.Arrays.asList((org.web3j.abi.TypeReference[]) refs);
  }
}
