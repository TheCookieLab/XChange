package org.knowm.xchange.bitget.uta.v3.common;

import java.util.Arrays;
import java.util.Optional;

/**
 * Bitget UTA v3 product category.
 *
 * <p>Categories are the native product families of the v3 API. They appear verbatim as the {@code
 * category} request parameter and in instrument payloads. Symbol text alone is not unique across
 * categories (for example {@code BTCUSDT} exists in SPOT, MARGIN and USDT-FUTURES), so any
 * instrument identity that must not collide needs the category as native context.
 */
public enum BitgetUtaV3Category {

  /** Spot trading. */
  SPOT("spot"),

  /** Margin trading (spot-like instruments with isolated/crossed margin). */
  MARGIN("margin"),

  /** USDT-margined linear futures. */
  USDT_FUTURES("usdt-futures"),

  /** Coin-margined inverse futures. */
  COIN_FUTURES("coin-futures"),

  /** USDC-margined futures. */
  USDC_FUTURES("usdc-futures");

  private final String wireName;

  BitgetUtaV3Category(String wireName) {
    this.wireName = wireName;
  }

  /** The verbatim value used by the Bitget v3 API. */
  public String getWireName() {
    return wireName;
  }

  /**
   * Parses a category from its wire name.
   *
   * @throws IllegalArgumentException when the value is not a known category.
   */
  public static BitgetUtaV3Category fromWireName(String wireName) {
    return Optional.ofNullable(wireName)
        .flatMap(
            name ->
                Arrays.stream(values())
                    .filter(category -> category.wireName.equalsIgnoreCase(name))
                    .findFirst())
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Unknown Bitget v3 category \""
                        + wireName
                        + "\"; expected one of "
                        + Arrays.toString(values())));
  }

  /** Whether this category is a futures (derivative) family. */
  public boolean isDerivative() {
    return this == USDT_FUTURES || this == COIN_FUTURES || this == USDC_FUTURES;
  }
}
