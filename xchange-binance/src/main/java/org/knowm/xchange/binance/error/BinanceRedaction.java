package org.knowm.xchange.binance.error;

/**
 * Redaction helpers so that secrets, signatures, and signed payloads never reach logs or
 * exception messages.
 *
 * <p>Binance API keys are transmitted as the {@code X-MBX-APIKEY} header, signatures appear as
 * the {@code signature} query parameter, and private keys appear in PEM blocks or as Base64
 * material. All of these are redacted by {@link #redact(String)}.
 */
public final class BinanceRedaction {

  private static final String REDACTED = "<redacted>";

  private static final java.util.regex.Pattern SIGNATURE =
      java.util.regex.Pattern.compile("(?i)(signature=)[^&\\s\"]+");
  private static final java.util.regex.Pattern API_KEY_QUERY =
      java.util.regex.Pattern.compile("(?i)(apikey=)[^&\\s\"]+");
  private static final java.util.regex.Pattern API_KEY_HEADER =
      java.util.regex.Pattern.compile("(?i)(x-mbx-apikey[:=]\\s*)[^\\s,\"]+");
  private static final java.util.regex.Pattern PRIVATE_KEY_PEM =
      java.util.regex.Pattern.compile(
          "(?s)(-----BEGIN [A-Z ]*PRIVATE KEY-----).*?(-----END [A-Z ]*PRIVATE KEY-----)");

  private BinanceRedaction() {}

  /**
   * Returns a copy of {@code text} with Binance secret material replaced by a redaction marker.
   */
  public static String redact(String text) {
    if (text == null) {
      return null;
    }
    String redacted =
        PRIVATE_KEY_PEM.matcher(text).replaceAll("$1" + REDACTED + "$2");
    redacted = SIGNATURE.matcher(redacted).replaceAll("$1" + REDACTED);
    redacted = API_KEY_QUERY.matcher(redacted).replaceAll("$1" + REDACTED);
    redacted = API_KEY_HEADER.matcher(redacted).replaceAll("$1" + REDACTED);
    return redacted;
  }
}
