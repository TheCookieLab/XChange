package org.knowm.xchange.mexc.v3.dto.marketdata;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One symbol rule from {@code GET /api/v3/exchangeInfo}.
 *
 * <p>Field names are the verbatim provider keys. Only {@code PERCENT_PRICE_BY_SIDE} is documented
 * in the {@code filters} array; other filter types and any future fields are preserved raw through
 * {@link #getExtra()} instead of being guessed or dropped.
 */
public class MexcV3Symbol {

  private final String symbol;
  private final String status;
  private final String baseAsset;
  private final int baseAssetPrecision;
  private final String quoteAsset;
  private final int quotePrecision;
  private final int quoteAssetPrecision;
  private final int baseCommissionPrecision;
  private final int quoteCommissionPrecision;
  private final java.util.List<String> orderTypes;
  private final boolean isSpotTradingAllowed;
  private final boolean isMarginTradingAllowed;
  private final String quoteAmountPrecision;
  private final String baseSizePrecision;
  private final java.util.List<String> permissions;
  private final java.util.List<MexcV3SymbolFilter> filters;
  private final String maxQuoteAmount;
  private final String makerCommission;
  private final String takerCommission;
  private final String quoteAmountPrecisionMarket;
  private final String maxQuoteAmountMarket;
  private final String fullName;
  private final String tradeSideType;
  private final String contractAddress;
  private final long[] conceptPlateIds;
  private final long firstOpenTime;
  private final String st;
  private final Map<String, Object> extra = new LinkedHashMap<>();

  public MexcV3Symbol(
      @JsonProperty("symbol") String symbol,
      @JsonProperty("status") String status,
      @JsonProperty("baseAsset") String baseAsset,
      @JsonProperty("baseAssetPrecision") int baseAssetPrecision,
      @JsonProperty("quoteAsset") String quoteAsset,
      @JsonProperty("quotePrecision") int quotePrecision,
      @JsonProperty("quoteAssetPrecision") int quoteAssetPrecision,
      @JsonProperty("baseCommissionPrecision") int baseCommissionPrecision,
      @JsonProperty("quoteCommissionPrecision") int quoteCommissionPrecision,
      @JsonProperty("orderTypes") java.util.List<String> orderTypes,
      @JsonProperty("isSpotTradingAllowed") boolean isSpotTradingAllowed,
      @JsonProperty("isMarginTradingAllowed") boolean isMarginTradingAllowed,
      @JsonProperty("quoteAmountPrecision") String quoteAmountPrecision,
      @JsonProperty("baseSizePrecision") String baseSizePrecision,
      @JsonProperty("permissions") java.util.List<String> permissions,
      @JsonProperty("filters") java.util.List<MexcV3SymbolFilter> filters,
      @JsonProperty("maxQuoteAmount") String maxQuoteAmount,
      @JsonProperty("makerCommission") String makerCommission,
      @JsonProperty("takerCommission") String takerCommission,
      @JsonProperty("quoteAmountPrecisionMarket") String quoteAmountPrecisionMarket,
      @JsonProperty("maxQuoteAmountMarket") String maxQuoteAmountMarket,
      @JsonProperty("fullName") String fullName,
      @JsonProperty("tradeSideType") String tradeSideType,
      @JsonProperty("contractAddress") String contractAddress,
      @JsonProperty("conceptPlateIds") long[] conceptPlateIds,
      @JsonProperty("firstOpenTime") long firstOpenTime,
      @JsonProperty("st") String st) {
    this.symbol = symbol;
    this.status = status;
    this.baseAsset = baseAsset;
    this.baseAssetPrecision = baseAssetPrecision;
    this.quoteAsset = quoteAsset;
    this.quotePrecision = quotePrecision;
    this.quoteAssetPrecision = quoteAssetPrecision;
    this.baseCommissionPrecision = baseCommissionPrecision;
    this.quoteCommissionPrecision = quoteCommissionPrecision;
    this.orderTypes = orderTypes;
    this.isSpotTradingAllowed = isSpotTradingAllowed;
    this.isMarginTradingAllowed = isMarginTradingAllowed;
    this.quoteAmountPrecision = quoteAmountPrecision;
    this.baseSizePrecision = baseSizePrecision;
    this.permissions = permissions;
    this.filters = filters;
    this.maxQuoteAmount = maxQuoteAmount;
    this.makerCommission = makerCommission;
    this.takerCommission = takerCommission;
    this.quoteAmountPrecisionMarket = quoteAmountPrecisionMarket;
    this.maxQuoteAmountMarket = maxQuoteAmountMarket;
    this.fullName = fullName;
    this.tradeSideType = tradeSideType;
    this.contractAddress = contractAddress;
    this.conceptPlateIds = conceptPlateIds;
    this.firstOpenTime = firstOpenTime;
    this.st = st;
  }

  /** Symbol, uppercase, e.g. {@code BTCUSDT}. */
  public String getSymbol() {
    return symbol;
  }

  /** Trading status as transmitted: {@code 1}=online, {@code 2}=pause, {@code 3}=offline. */
  public String getStatus() {
    return status;
  }

  public String getBaseAsset() {
    return baseAsset;
  }

  public int getBaseAssetPrecision() {
    return baseAssetPrecision;
  }

  public String getQuoteAsset() {
    return quoteAsset;
  }

  public int getQuotePrecision() {
    return quotePrecision;
  }

  public int getQuoteAssetPrecision() {
    return quoteAssetPrecision;
  }

  public int getBaseCommissionPrecision() {
    return baseCommissionPrecision;
  }

  public int getQuoteCommissionPrecision() {
    return quoteCommissionPrecision;
  }

  public java.util.List<String> getOrderTypes() {
    return orderTypes;
  }

  public boolean isSpotTradingAllowed() {
    return isSpotTradingAllowed;
  }

  public boolean isMarginTradingAllowed() {
    return isMarginTradingAllowed;
  }

  /** Minimum order amount in the quote asset, as transmitted. */
  public String getQuoteAmountPrecision() {
    return quoteAmountPrecision;
  }

  /** Minimum order quantity in the base asset, as transmitted. */
  public String getBaseSizePrecision() {
    return baseSizePrecision;
  }

  public java.util.List<String> getPermissions() {
    return permissions;
  }

  public java.util.List<MexcV3SymbolFilter> getFilters() {
    return filters;
  }

  public String getMaxQuoteAmount() {
    return maxQuoteAmount;
  }

  public String getMakerCommission() {
    return makerCommission;
  }

  public String getTakerCommission() {
    return takerCommission;
  }

  /** Minimum order amount in the quote asset for market orders, as transmitted. */
  public String getQuoteAmountPrecisionMarket() {
    return quoteAmountPrecisionMarket;
  }

  public String getMaxQuoteAmountMarket() {
    return maxQuoteAmountMarket;
  }

  public String getFullName() {
    return fullName;
  }

  public String getTradeSideType() {
    return tradeSideType;
  }

  public String getContractAddress() {
    return contractAddress;
  }

  public long[] getConceptPlateIds() {
    return conceptPlateIds;
  }

  public long getFirstOpenTime() {
    return firstOpenTime;
  }

  public String getSt() {
    return st;
  }

  /** Fields not explicitly modeled, preserved verbatim from the payload. */
  public Map<String, Object> getExtra() {
    return extra;
  }

  @JsonAnySetter
  public void setExtra(String name, Object value) {
    extra.put(name, value);
  }
}
