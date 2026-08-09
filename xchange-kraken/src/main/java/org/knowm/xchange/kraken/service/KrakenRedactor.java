package org.knowm.xchange.kraken.service;

import java.util.regex.Pattern;

/**
 * Removes credentials, tokens, and sensitive identifiers from diagnostic text.
 *
 * <p>Kraken provider error arrays can echo request parameters such as API keys, signatures, nonces,
 * one-time passwords, WebSocket tokens, and withdrawal addresses. All error messages and structured
 * failure details in this module pass through {@link #sanitize(String)} before they reach callers.
 */
public final class KrakenRedactor {

  private static final Pattern BEARER =
      Pattern.compile("(?i)(authorization\\s*[:=]\\s*bearer\\s+)[^\\s,}\"]+");
  private static final Pattern JWT =
      Pattern.compile("\\beyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\b");
  private static final Pattern SENSITIVE_FIELD =
      Pattern.compile(
          "(?i)(\"?(?:api[-_]?key|api[-_]?secret|secret|api[-_]?sign|nonce|otp|websocket[-_]?token"
              + "|access_token|token|signature|client_secret|address)\"?"
              + "\\s*[:=]\\s*\"?)[^\",}\\s]+");

  private KrakenRedactor() {}

  /**
   * Returns the input with bearer tokens, JWTs, and sensitive named fields replaced by {@code
   * <redacted>} markers, or {@code null} when the input is {@code null}.
   *
   * @param value raw diagnostic text
   * @return sanitized text
   */
  public static String sanitize(String value) {
    if (value == null) {
      return null;
    }
    String sanitized = BEARER.matcher(value).replaceAll("$1<redacted>");
    sanitized = JWT.matcher(sanitized).replaceAll("<redacted-jwt>");
    return SENSITIVE_FIELD.matcher(sanitized).replaceAll("$1<redacted>");
  }
}
