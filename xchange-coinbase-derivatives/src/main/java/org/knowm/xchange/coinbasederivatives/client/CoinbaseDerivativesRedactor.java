package org.knowm.xchange.coinbasederivatives.client;

import java.util.regex.Pattern;

/** Removes credentials and tokens from diagnostic text. */
public final class CoinbaseDerivativesRedactor {
  private static final Pattern BEARER =
      Pattern.compile("(?i)(authorization\\s*[:=]\\s*bearer\\s+)[^\\s,}\"]+");
  private static final Pattern JWT =
      Pattern.compile("\\beyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\b");
  private static final Pattern TOKEN_FIELD =
      Pattern.compile(
          "(?i)(\"?(?:access_token|signed_jwt|jwt|token|client_secret|api_key|key_id|key_name)\"?"
              + "\\s*[:=]\\s*\"?)[^\",}\\s]+");

  private CoinbaseDerivativesRedactor() {}

  public static String sanitize(String value) {
    if (value == null) {
      return null;
    }
    String sanitized = BEARER.matcher(value).replaceAll("$1<redacted>");
    sanitized = JWT.matcher(sanitized).replaceAll("<redacted-jwt>");
    return TOKEN_FIELD.matcher(sanitized).replaceAll("$1<redacted>");
  }
}
