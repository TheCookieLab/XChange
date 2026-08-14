package org.knowm.xchange.mexc.v3.client;

import java.util.regex.Pattern;

/** Removes credentials and private identifiers from diagnostic text. */
public final class MexcV3Redactor {

  private static final Pattern X_MEXC_APIKEY =
      Pattern.compile("(?i)(x-mexc-apikey\\s*[:=]\\s*)[^\\s,}\"]+");
  private static final Pattern API_SECRET =
      Pattern.compile("(?i)(\"?(?:api[_-]?secret|secret[_-]?key|secret)\"?\\s*[:=]\\s*\"?)[^\"',}\\s]+");
  private static final Pattern API_KEY =
      Pattern.compile("(?i)(\"?(?:api[_-]?key|api_key|key)\"?\\s*[:=]\\s*\"?)[^\"',}\\s]+");
  private static final Pattern LISTEN_KEY =
      Pattern.compile("\\b[a-fA-F0-9]{64,}\\b");

  private MexcV3Redactor() {}

  /** Returns {@code value} with credentials and 64+ hex listen keys replaced by placeholders. */
  public static String sanitize(String value) {
    if (value == null) {
      return null;
    }
    String sanitized = X_MEXC_APIKEY.matcher(value).replaceAll("$1<redacted>");
    sanitized = API_SECRET.matcher(sanitized).replaceAll("$1<redacted>");
    sanitized = API_KEY.matcher(sanitized).replaceAll("$1<redacted>");
    return LISTEN_KEY.matcher(sanitized).replaceAll("<redacted>");
  }
}
