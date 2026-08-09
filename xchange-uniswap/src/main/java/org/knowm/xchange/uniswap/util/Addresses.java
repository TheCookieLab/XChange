package org.knowm.xchange.uniswap.util;

import java.math.BigInteger;
import java.util.Locale;

/** Address helpers shared by the Uniswap module. */
public final class Addresses {

  private Addresses() {}

  /** Normalizes a hex address to lowercase with a {@code 0x} prefix. */
  public static String normalize(String address) {
    String cleaned = clean(address);
    if (cleaned.isEmpty()) {
      return "";
    }
    return "0x" + cleaned.toLowerCase(Locale.ROOT);
  }

  /**
   * Validates that {@code address} is a 20-byte hex address and returns its normalized lowercase
   * form.
   */
  public static String requireValidAddress(String address) {
    String normalized = normalize(address);
    if (!isValid(normalized)) {
      throw new IllegalArgumentException("invalid Ethereum address: " + address);
    }
    return normalized;
  }

  /** Returns true when the normalized address is exactly 20 bytes of hex. */
  public static boolean isValid(String normalizedAddress) {
    if (normalizedAddress == null || !normalizedAddress.startsWith("0x")) {
      return false;
    }
    String hex = normalizedAddress.substring(2);
    return hex.length() == 40 && hex.chars().allMatch(Addresses::isHexDigit);
  }

  private static boolean isHexDigit(int c) {    return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
  }

  private static String clean(String address) {
    if (address == null) {
      return "";
    }
    String trimmed = address.trim();
    if (trimmed.length() >= 2 && trimmed.startsWith("0x")) {
      return trimmed.substring(2);
    }
    return trimmed;
  }

  /** Numeric value of a normalized address, used for currency sorting. */
  public static BigInteger value(String normalizedAddress) {
    return new BigInteger(normalizedAddress.substring(2), 16);
  }
}
