package org.knowm.xchange.kucoin.uta.service;

import java.util.regex.Pattern;

/**
 * Redaction of KuCoin UTA secret material from logs and exception text.
 *
 * <p>Guarantees from the CF-449 contract: secrets, signatures, WebSocket tokens, private payloads,
 * and withdrawal/transfer details never appear in logs or exception text. Every sink in the UTA
 * package routes human-readable output through {@link #sanitize(String)}.
 */
public final class UtaRedaction {

  private static final Pattern SIGN_HEADER =
      Pattern.compile("(?i)(KC-API-SIGN)(\\s*[:=]\\s*)[^\\s,;&]+");
  private static final Pattern PASSPHRASE_HEADER =
      Pattern.compile("(?i)(KC-API-PASSPHRASE)(\\s*[:=]\\s*)[^\\s,;&]+");
  private static final Pattern KEY_HEADER = Pattern.compile("(?i)(KC-API-KEY)(\\s*[:=]\\s*)[^\\s,;&]+");
  private static final Pattern TOKEN_QUERY =
      Pattern.compile("(?i)(token=)[^&\\s\"']+");
  private static final Pattern JSON_SECRET_FIELD =
      Pattern.compile("(?i)(\"(?:passphrase|secretKey|apiSecret|token|signature)\"\\s*:\\s*)\"[^\"]*\"");
  private static final Pattern JSON_SENSITIVE_FIELD =
      Pattern.compile(
          "(?i)(\"(?:withdrawal|transfer|amount|address|memo|remark)\"\\s*:\\s*)\"[^\"]*\"");
  private static final String HEADER_MASK = "$1$2***";
  private static final String MASK = "$1***";

  private UtaRedaction() {}

  /**
   * Masks known secret and sensitive material in a string.
   *
   * @param input raw text that may contain credentials
   * @return redacted text, never {@code null}
   */
  public static String sanitize(String input) {
    if (input == null || input.isEmpty()) {
      return input;
    }
    String redacted = input;
    redacted = SIGN_HEADER.matcher(redacted).replaceAll(HEADER_MASK);
    redacted = PASSPHRASE_HEADER.matcher(redacted).replaceAll(HEADER_MASK);
    redacted = KEY_HEADER.matcher(redacted).replaceAll(HEADER_MASK);
    redacted = TOKEN_QUERY.matcher(redacted).replaceAll(MASK);
    redacted = JSON_SECRET_FIELD.matcher(redacted).replaceAll(MASK);
    redacted = JSON_SENSITIVE_FIELD.matcher(redacted).replaceAll(MASK);
    return redacted;
  }
}
