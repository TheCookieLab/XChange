package org.knowm.xchange.cryptocom.dto.marketdata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Typed mirror of the official {@code public/get-instruments} result.data[] row (Crypto.com
 * Exchange v1). Every field follows the authoritative OpenAPI {@code InstrumentItem} schema; all
 * numeric fields keep the provider's exact decimal-string representation so downstream code never
 * rounds or reformats values.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CryptoComInstrument {

  /** Instrument name as used by the exchange, e.g. {@code BTC_USD}, {@code BTCUSD-PERP}, {@code BTCUSD-250627}, {@code BTCUSD-250627-60000-C}. */
  @JsonProperty("symbol")
  private String symbol;

  /** Instrument type, e.g. {@code CCY_PAIR}, {@code PERPETUAL_SWAP}, {@code FUTURE}, {@code OPTION}. Treated as opaque; compare via the constants below. */
  @JsonProperty("inst_type")
  private String instType;

  @JsonProperty("display_name")
  private String displayName;

  @JsonProperty("base_ccy")
  private String baseCurrency;

  @JsonProperty("quote_ccy")
  private String quoteCurrency;

  @JsonProperty("quote_decimals")
  private Integer quoteDecimals;

  @JsonProperty("quantity_decimals")
  private Integer quantityDecimals;

  @JsonProperty("price_tick_size")
  private String priceTickSize;

  @JsonProperty("qty_tick_size")
  private String qtyTickSize;

  @JsonProperty("max_leverage")
  private String maxLeverage;

  @JsonProperty("tradable")
  private Boolean tradable;

  /** Settlement/expiry for dated products; 0 for perpetuals and spot pairs. */
  @JsonProperty("expiry_timestamp_ms")
  private Long expiryTimestampMs;

  @JsonProperty("beta_product")
  private Boolean betaProduct;

  /** Reference index/underlying instrument, e.g. {@code BTCUSD-INDEX} for derivatives. */
  @JsonProperty("underlying_symbol")
  private String underlyingSymbol;

  @JsonProperty("product_type")
  private String productType;

  /** Contract size (multiplier applied to position quantity), e.g. {@code "1"}. */
  @JsonProperty("contract_size")
  private String contractSize;

  @JsonProperty("margin_buy_enabled")
  private Boolean marginBuyEnabled;

  @JsonProperty("margin_sell_enabled")
  private Boolean marginSellEnabled;

  @JsonProperty("last_updated_time")
  private Long lastUpdatedTime;

  // ---------------------------------------------------------------------------------------------
  // Type constants (official inst_type values observed across v1 product lines).
  // ---------------------------------------------------------------------------------------------

  public static final String TYPE_CCY_PAIR = "CCY_PAIR";
  public static final String TYPE_PERPETUAL = "PERPETUAL_SWAP";
  public static final String TYPE_PERPETUAL_LEGACY = "PERPETUAL";
  public static final String TYPE_FUTURE = "FUTURE";
  public static final String TYPE_OPTION = "OPTION";

  // ---------------------------------------------------------------------------------------------
  // Derived identity helpers — lossless product classification on top of official fields.
  // ---------------------------------------------------------------------------------------------

  /** @return true when the instrument is a spot currency pair (not derivative, not prediction). */
  public boolean isCcPair() {
    return TYPE_CCY_PAIR.equals(instType);
  }

  /** @return true for perpetual swaps (both current {@code PERPETUAL_SWAP} and legacy {@code PERPETUAL} identifiers). */
  public boolean isPerpetual() {
    return TYPE_PERPETUAL.equals(instType) || TYPE_PERPETUAL_LEGACY.equals(instType);
  }

  /** @return true for dated futures. */
  public boolean isFuture() {
    return TYPE_FUTURE.equals(instType);
  }

  /** @return true for vanilla options. */
  public boolean isOption() {
    return TYPE_OPTION.equals(instType);
  }

  /** @return true for any derivative product (perpetual, future or option). */
  public boolean isDerivative() {
    return isPerpetual() || isFuture() || isOption();
  }

  /**
   * Settlement currency for the product. The current official {@code InstrumentItem} schema does
   * not expose a distinct {@code settlement_ccy}; for spot pairs settlement equals the quote
   * currency, and for cash-settled derivatives it equals the quote currency of the underlying
   * (e.g. USD for {@code BTCUSD-PERP}). Callers needing a provider-verified distinct settlement
   * currency must source it from {@link #getUnderlyingSymbol()} reference data instead.
   *
   * @return settlement currency code, or {@code null} when the row lacks base/quote currencies.
   */
  public String getSettlementCurrency() {
    return quoteCurrency;
  }

  /** @return margin-trading eligibility state: [buy, sell] or {@code null} when unknown. */
  public boolean[] getMarginTradingEligibility() {
    if (marginBuyEnabled == null || marginSellEnabled == null) {
      return null;
    }
    return new boolean[] {marginBuyEnabled, marginSellEnabled};
  }

  /** @return parsed derivative identity for non-spot instruments, or {@code null} for spot pairs / unparseable names. */
  public CryptoComInstrumentIdentity getIdentity() {
    if (isCcPair() || symbol == null) {
      return null;
    }
    return CryptoComInstrumentIdentity.parse(symbol);
  }

  @Override
  public String toString() {
    return "CryptoComInstrument{"
        + "symbol='"
        + symbol
        + '\''
        + ", instType='"
        + instType
        + '\''
        + ", baseCurrency='"
        + baseCurrency
        + '\''
        + ", quoteCurrency='"
        + quoteCurrency
        + '\''
        + ", tradable="
        + tradable
        + '}';
  }
}