package org.knowm.xchange.bitget.uta.v3.market;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

/**
 * Bitget UTA v3 instrument (product) metadata, one object per symbol within a category.
 *
 * <p>{@code GET /api/v3/market/instruments?category=...} returns one entry per product. Keys below
 * follow the verified live payload. Numeric precision fields are integer exponents (for example
 * {@code pricePrecision=2} means two decimal places); limits are decimal strings. Symbol text is
 * unique only within a category — the same text (for example {@code BTCUSDT}) exists in SPOT,
 * MARGIN and USDT-FUTURES.
 */
@Data
@Builder
@Jacksonized
public class BitgetUtaV3Instrument {

  @JsonProperty("symbol")
  private String symbol;

  /** Native category wire name (spot, margin, usdt-futures, coin-futures, usdc-futures). */
  @JsonProperty("category")
  private String category;

  @JsonProperty("baseCoin")
  private String baseCoin;

  @JsonProperty("quoteCoin")
  private String quoteCoin;

  /** spot | derivatives (futures). */
  @JsonProperty("symbolType")
  private String symbolType;

  @JsonProperty("buyLimitPriceRatio")
  private BigDecimal buyLimitPriceRatio;

  @JsonProperty("sellLimitPriceRatio")
  private BigDecimal sellLimitPriceRatio;

  @JsonProperty("minOrderQty")
  private BigDecimal minOrderQty;

  @JsonProperty("maxOrderQty")
  private BigDecimal maxOrderQty;

  @JsonProperty("pricePrecision")
  private Integer pricePrecision;

  @JsonProperty("quantityPrecision")
  private Integer quantityPrecision;

  @JsonProperty("quotePrecision")
  private Integer quotePrecision;

  @JsonProperty("minOrderAmount")
  private BigDecimal minOrderAmount;

  @JsonProperty("maxSymbolOrderNum")
  private Integer maxSymbolOrderNum;

  @JsonProperty("maxProductOrderNum")
  private Integer maxProductOrderNum;

  /** online | offline | pre_open | suspend. */
  @JsonProperty("status")
  private String status;

  @JsonProperty("maintainTime")
  private Instant maintainTime;

  @JsonProperty("maxPositionNum")
  private Integer maxPositionNum;

  /** Whether this instrument is a Reality (simulated) market pair. */
  @JsonProperty("isReality")
  private String isReality;

  @JsonProperty("launchTime")
  private String launchTime;

  /** Margin coin for futures (present on derivative instruments). */
  @JsonProperty("marginCoin")
  private String marginCoin;

  /** Contract size (derivative instruments). */
  @JsonProperty("ctVal")
  private BigDecimal ctVal;

  /** Contract value currency (derivative instruments). */
  @JsonProperty("ctValCcy")
  private String ctValCcy;

  @JsonProperty("priceEndStep")
  private BigDecimal priceEndStep;

  @JsonProperty("deliveryTime")
  private String deliveryTime;
}
