package org.knowm.xchange.kucoin;

import java.util.Locale;

/**
 * KuCoin API/account generation.
 *
 * <p>KuCoin's classic Spot-era API and the newer Unified Trading Account (UTA) API are distinct
 * generations: they use different endpoint families, account semantics, and WebSocket behavior.
 * The mode is selected explicitly through {@link KucoinExchange#API_MODE_PARAMETER}; it is never
 * inferred from endpoint responses and never silently switched.
 *
 * <p>{@link #CLASSIC} is the compatibility-period default so existing consumers upgrade without an
 * involuntary account-model change.
 */
public enum KucoinApiMode {

  /** Classic Spot-era API surface; preserved behind the compatibility boundary. */
  CLASSIC,

  /** Unified Trading Account generation: unified Spot/Futures account, positions, margin. */
  UTA;

  /**
   * Resolves an exchange-specific parameter value to a mode.
   *
   * @param raw the raw parameter value; {@code null} or blank resolves to {@link #CLASSIC}
   * @return the resolved mode
   * @throws IllegalArgumentException when the value is not a recognized mode
   */
  public static KucoinApiMode resolve(Object raw) {
    if (raw == null) {
      return CLASSIC;
    }
    if (raw instanceof KucoinApiMode) {
      return (KucoinApiMode) raw;
    }
    String text = String.valueOf(raw).trim();
    if (text.isEmpty()) {
      return CLASSIC;
    }
    try {
      return valueOf(text.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          "Unsupported KuCoin apiMode '" + raw + "'; expected CLASSIC or UTA", e);
    }
  }
}
