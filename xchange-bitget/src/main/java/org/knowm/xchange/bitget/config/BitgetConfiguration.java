package org.knowm.xchange.bitget.config;

import org.knowm.xchange.ExchangeSpecification;

/**
 * Typed, immutable configuration for the Bitget integration, derived from an {@link
 * ExchangeSpecification}.
 *
 * <p>Replaces magic strings and loosely typed exchange-specific parameters with documented
 * constants and typed accessors. Unknown or invalid values fail during {@link
 * #from(ExchangeSpecification)} with an actionable message, before any network call.
 *
 * <p>The {@link BitgetApiMode} selects the API/account generation. Classic remains the default so
 * existing {@code xchange-bitget}/{@code xchange-bitget-futures} consumers keep their current
 * behavior unless they explicitly opt into {@link BitgetApiMode#UTA_V3}.
 */
public final class BitgetConfiguration {

  /** Typed API/account-mode parameter (a {@link BitgetApiMode} value). */
  public static final String API_MODE = "Bitget_ApiMode";

  private static final BitgetApiMode DEFAULT_API_MODE = BitgetApiMode.CLASSIC_V2;

  private final BitgetApiMode apiMode;

  private BitgetConfiguration(BitgetApiMode apiMode) {
    this.apiMode = apiMode;
  }

  /**
   * Builds a configuration from an exchange specification, validating typed parameters and applying
   * the documented defaults when they are absent.
   *
   * <p>{@code null} mirrors {@link
   * org.knowm.xchange.BaseExchange#applySpecification(org.knowm.xchange.ExchangeSpecification)},
   * which treats a null specification as "use the defaults".
   *
   * @throws IllegalArgumentException when a parameter value is invalid, with an actionable message.
   */
  public static BitgetConfiguration from(ExchangeSpecification specification) {
    return new BitgetConfiguration(readApiMode(specification));
  }

  /** The API/account mode this exchange instance is configured for. */
  public BitgetApiMode getApiMode() {
    return apiMode;
  }

  private static BitgetApiMode readApiMode(ExchangeSpecification specification) {
    if (specification == null) {
      return DEFAULT_API_MODE;
    }
    Object value = specification.getExchangeSpecificParametersItem(API_MODE);
    if (value == null) {
      return DEFAULT_API_MODE;
    }
    if (value instanceof BitgetApiMode) {
      return (BitgetApiMode) value;
    }
    String text = String.valueOf(value);
    try {
      return BitgetApiMode.valueOf(text);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          "Bitget exchange-specific parameter \""
              + API_MODE
              + "\" must be one of "
              + java.util.Arrays.toString(BitgetApiMode.values())
              + " (got \""
              + text
              + "\").",
          e);
    }
  }
}
