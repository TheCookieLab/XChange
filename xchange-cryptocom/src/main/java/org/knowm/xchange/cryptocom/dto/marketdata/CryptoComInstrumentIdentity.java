package org.knowm.xchange.cryptocom.dto.marketdata;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic identity of a native Crypto.com Exchange v1 instrument name.
 *
 * <p>Official naming conventions (see exchange docs, "Instrument Name"):
 *
 * <ul>
 *   <li>Spot: {@code BTC_USD} (base_ccy + "_" + quote_ccy)
 *   <li>Perpetual swap: {@code BTCUSD-PERP}
 *   <li>Future: {@code BTCUSD-250627} (expiry = yyyyMMdd)
 *   <li>Option: {@code BTCUSD-250627-60000-C} (expiry = yyyyMMdd, strike, C/P)
 * </ul>
 *
 * <p>This parser exists so derivative products get lossless, test-verified identity (product type,
 * expiry, strike, option side) without regex sprinkling. Spot pairs are identified via the
 * underscore form only; anything else that does not match a derivative shape is rejected rather
 * than guessed.
 */
public final class CryptoComInstrumentIdentity {

  public enum ProductType {
    SPOT,
    PERPETUAL_SWAP,
    FUTURE,
    OPTION
  }

  private static final Pattern SPOT = Pattern.compile("^([A-Z0-9]+)_([A-Z0-9]+)$");
  private static final Pattern PERPETUAL = Pattern.compile("^([A-Z0-9]+)USD-PERP$");
  private static final Pattern DATED = Pattern.compile("^([A-Z0-9]+)USD-(\\d{6})$");
  private static final Pattern OPTION =
      Pattern.compile("^([A-Z0-9]+)USD-(\\d{6})-(\\d+(?:\\.\\d+)?)-([CP])$");

  private final String nativeName;
  private final ProductType productType;
  private final String baseCurrency;
  private final String quoteCurrency;
  private final String expiry; // yyyyMMdd, null for spot/perpetual
  private final String strikePrice; // null unless option
  private final char optionSide; // 'C' or 'P', '\0' unless option

  private CryptoComInstrumentIdentity(
      String nativeName,
      ProductType productType,
      String baseCurrency,
      String quoteCurrency,
      String expiry,
      String strikePrice,
      char optionSide) {
    this.nativeName = nativeName;
    this.productType = productType;
    this.baseCurrency = baseCurrency;
    this.quoteCurrency = quoteCurrency;
    this.expiry = expiry;
    this.strikePrice = strikePrice;
    this.optionSide = optionSide;
  }

  /**
   * Parses an official native instrument name.
   *
   * @param name instrument name as used by the exchange
   * @return identity, or {@code null} when the name does not match any official shape
   */
  public static CryptoComInstrumentIdentity parse(String name) {
    if (name == null || name.isEmpty()) {
      return null;
    }
    Matcher spot = SPOT.matcher(name);
    if (spot.matches()) {
      return new CryptoComInstrumentIdentity(
          name, ProductType.SPOT, spot.group(1), spot.group(2), null, null, '\0');
    }
    Matcher perp = PERPETUAL.matcher(name);
    if (perp.matches()) {
      return new CryptoComInstrumentIdentity(
          name, ProductType.PERPETUAL_SWAP, perp.group(1), "USD", null, null, '\0');
    }
    Matcher option = OPTION.matcher(name);
    if (option.matches()) {
      return new CryptoComInstrumentIdentity(
          name,
          ProductType.OPTION,
          option.group(1),
          "USD",
          option.group(2),
          option.group(3),
          option.group(4).charAt(0));
    }
    Matcher dated = DATED.matcher(name);
    if (dated.matches()) {
      return new CryptoComInstrumentIdentity(
          name, ProductType.FUTURE, dated.group(1), "USD", dated.group(2), null, '\0');
    }
    return null;
  }

  public String getNativeName() {
    return nativeName;
  }

  public ProductType getProductType() {
    return productType;
  }

  public String getBaseCurrency() {
    return baseCurrency;
  }

  public String getQuoteCurrency() {
    return quoteCurrency;
  }

  /** @return expiry as {@code yyyyMMdd}, or {@code null} when not applicable (spot/perpetual). */
  public String getExpiry() {
    return expiry;
  }

  /** @return strike price string for options, otherwise {@code null}. */
  public String getStrikePrice() {
    return strikePrice;
  }

  /** @return 'C'/'P' for options, otherwise '\0'. */
  public char getOptionSide() {
    return optionSide;
  }

  public boolean isOption() {
    return productType == ProductType.OPTION;
  }

  public boolean isDerivative() {
    return productType == ProductType.PERPETUAL_SWAP
        || productType == ProductType.FUTURE
        || productType == ProductType.OPTION;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof CryptoComInstrumentIdentity)) {
      return false;
    }
    CryptoComInstrumentIdentity that = (CryptoComInstrumentIdentity) o;
    return optionSide == that.optionSide
        && productType == that.productType
        && Objects.equals(nativeName, that.nativeName)
        && Objects.equals(baseCurrency, that.baseCurrency)
        && Objects.equals(quoteCurrency, that.quoteCurrency)
        && Objects.equals(expiry, that.expiry)
        && Objects.equals(strikePrice, that.strikePrice);
  }

  @Override
  public int hashCode() {
    return Objects.hash(nativeName, productType, baseCurrency, quoteCurrency, expiry, strikePrice, optionSide);
  }

  @Override
  public String toString() {
    return nativeName + " [" + productType + "]";
  }
}