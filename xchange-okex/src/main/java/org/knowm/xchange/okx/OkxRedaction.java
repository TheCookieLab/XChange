package org.knowm.xchange.okx;

import java.util.regex.Pattern;

/**
 * Masks credential material (API key, secret key, passphrase, signature) in strings destined for
 * logs, exceptions, or any other external output.
 *
 * <p>Two layers are applied:
 *
 * <ul>
 *   <li>Every occurrence of each supplied secret value is replaced with {@link #MASK}.
 *   <li>OKX authentication header values ({@code OK-ACCESS-KEY}, {@code OK-ACCESS-SIGN}, {@code
 *       OK-ACCESS-PASSPHRASE}) are normalized to {@code name: ***} wherever they appear, so even
 *       secrets that are not known up front cannot leak through dumped request headers.
 * </ul>
 */
public final class OkxRedaction {

  /** Replacement used for every masked secret. */
  public static final String MASK = "***";

  /** Shortest secret that will be masked, to avoid mangling short innocent substrings. */
  static final int MIN_SECRET_LENGTH = 4;

  private static final Pattern OKX_ACCESS_HEADER =
      Pattern.compile("(?i)(OK-ACCESS-(?:KEY|SIGN|PASSPHRASE))\\s*[:=]\\s*[^\\s,;]+");

  private OkxRedaction() {}

  /**
   * Returns {@code value} with every occurrence of each non-blank secret (of length at least
   * {@value #MIN_SECRET_LENGTH}) replaced by {@link #MASK}, and with OKX authentication header
   * values normalized to {@code name: ***}.
   *
   * @param value the text to redact; may be {@code null}
   * @param secrets secret values to mask; {@code null} entries and short secrets are ignored
   * @return the redacted text, or {@code null} when {@code value} is {@code null}
   */
  public static String mask(String value, String... secrets) {
    if (value == null || value.isEmpty()) {
      return value;
    }
    String result = value;
    if (secrets != null) {
      for (String secret : secrets) {
        if (secret != null && secret.length() >= MIN_SECRET_LENGTH) {
          result = result.replace(secret, MASK);
        }
      }
    }
    return OKX_ACCESS_HEADER.matcher(result).replaceAll("$1: " + MASK);
  }
}
