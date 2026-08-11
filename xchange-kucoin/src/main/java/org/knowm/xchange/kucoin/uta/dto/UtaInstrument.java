package org.knowm.xchange.kucoin.uta.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

/**
 * UTA instrument (trading pair) with lossless native identity.
 *
 * <p>Spot instruments map to {@link org.knowm.xchange.currency.CurrencyPair}; futures instruments
 * keep their distinct derivative identity via {@code contractType}, {@code isInverse}, {@code
 * expiryTime}, {@code lotSize}, {@code unitSize}, {@code maxLeverage} and {@code settlementCurrency}
 * rather than being inferred from symbol text.
 */
@Data
public class UtaInstrument {

  @JsonProperty("symbol")
  private String symbol;

  @JsonProperty("name")
  private String name;

  @JsonProperty("baseCurrency")
  private String baseCurrency;

  @JsonProperty("quoteCurrency")
  private String quoteCurrency;

  @JsonProperty("market")
  private String market;

  @JsonProperty("minBaseOrderSize")
  private BigDecimal minBaseOrderSize;

  @JsonProperty("minQuoteOrderSize")
  private BigDecimal minQuoteOrderSize;

  @JsonProperty("maxBaseOrderSize")
  private BigDecimal maxBaseOrderSize;

  @JsonProperty("maxQuoteOrderSize")
  private BigDecimal maxQuoteOrderSize;

  @JsonProperty("baseOrderStep")
  private BigDecimal baseOrderStep;

  @JsonProperty("quoteOrderStep")
  private BigDecimal quoteOrderStep;

  @JsonProperty("tickSize")
  private BigDecimal tickSize;

  @JsonProperty("feeCurrency")
  private String feeCurrency;

  /** 0 disabled / init, 1 enabled / open, 2 beingSettled, 3 settled, 4 paused, 5 closed, 6 cancelOnly. */
  @JsonProperty("tradingStatus")
  private String tradingStatus;

  @JsonProperty("priceLimitRatio")
  private BigDecimal priceLimitRatio;

  @JsonProperty("feeCategory")
  private Integer feeCategory;

  @JsonProperty("makerFeeCoefficient")
  private BigDecimal makerFeeCoefficient;

  @JsonProperty("takerFeeCoefficient")
  private BigDecimal takerFeeCoefficient;

  @JsonProperty("st")
  private Boolean st;

  @JsonProperty("settlementCurrency")
  private String settlementCurrency;

  /** 0 perpetual swap, 1 deliverable futures. */
  @JsonProperty("contractType")
  private String contractType;

  @JsonProperty("isInverse")
  private Boolean isInverse;

  @JsonProperty("expiryTime")
  private Long expiryTime;

  @JsonProperty("settlementTime")
  private Long settlementTime;

  @JsonProperty("maxPrice")
  private BigDecimal maxPrice;

  @JsonProperty("lotSize")
  private BigDecimal lotSize;

  @JsonProperty("unitSize")
  private BigDecimal unitSize;

  @JsonProperty("makerFeeRate")
  private BigDecimal makerFeeRate;

  @JsonProperty("takerFeeRate")
  private BigDecimal takerFeeRate;

  @JsonProperty("settlementFeeRate")
  private BigDecimal settlementFeeRate;

  @JsonProperty("maxLeverage")
  private Integer maxLeverage;

  @JsonProperty("indexSourceExchanges")
  private List<String> indexSourceExchanges;

  @JsonProperty("displayBaseCurrency")
  private String displayBaseCurrency;

  @JsonProperty("minFunds")
  private BigDecimal minFunds;

  @JsonProperty("callauctionIsEnabled")
  private Boolean callauctionIsEnabled;

  @JsonProperty("buyLimit")
  private BigDecimal buyLimit;

  @JsonProperty("sellLimit")
  private BigDecimal sellLimit;

  @JsonProperty("indexPriceTickSize")
  private BigDecimal indexPriceTickSize;

  @JsonProperty("marketType")
  private String marketType;

  @JsonProperty("marketStage")
  private String marketStage;
}
