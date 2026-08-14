package org.knowm.xchange.okx.dto.marketdata;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;

/* Author: Max Gao (gaamox@tutanota.com) Created: 08-06-2021 */
/**
 * An instrument (tradable product) as reported by <a
 * href="https://www.okx.com/docs-v5/en/#rest-api-public-data-get-instruments">GET
 * /api/v5/public/instruments</a>.
 *
 * <p>Every stable field of the response is carried losslessly: identity fields (instrument id,
 * type, underlying, family, currencies, contract specifications), trading rule fields (lot, minimum
 * and tick size, leverage, order size/amount and position limits, price limit percentages, trading
 * rule type) and lifecycle fields (state, listing/expiry time, schedule switches). Fields that do
 * not apply to a given instrument type are returned by OKX as empty strings and are kept as-is.
 */
@Getter
@NoArgsConstructor
public class OkxInstrument {
  @JsonProperty("instType")
  private String instrumentType;

  @JsonProperty("instId")
  private String instrumentId;

  @JsonProperty("uly")
  private String underlying;

  @JsonProperty("instFamily")
  private String instrumentFamily;

  @JsonProperty("category")
  private String category;

  @JsonProperty("instCategory")
  private String instrumentCategory;

  @JsonProperty("baseCcy")
  private String baseCurrency;

  @JsonProperty("quoteCcy")
  private String quoteCurrency;

  @JsonProperty("settleCcy")
  private String settleCurrency;

  @JsonProperty("ctVal")
  private String contractValue;

  @JsonProperty("ctValCcy")
  private String contractValueCurrency;

  @JsonProperty("ctMult")
  private String contractMultiplier;

  @JsonProperty("optType")
  private String optionType;

  @JsonProperty("stk")
  private String strikePrice;

  @JsonProperty("listTime")
  private String listTime;

  @JsonProperty("expTime")
  private String expiryTime;

  @JsonProperty("lever")
  private String leverage;

  @JsonProperty("tickSz")
  private String tickSize;

  @JsonProperty("lotSz")
  private String lotSize;

  @JsonProperty("minSz")
  private String minSize;

  @JsonProperty("ctType")
  private String contractType;

  @JsonProperty("alias")
  private String alias;

  @JsonProperty("state")
  private String state;

  @JsonProperty("instIdCode")
  private String instIdCode;

  @JsonProperty("ruleType")
  private String ruleType;

  @JsonProperty("maxLmtSz")
  private String maxLimitSize;

  @JsonProperty("maxMktSz")
  private String maxMarketSize;

  @JsonProperty("maxLmtAmt")
  private String maxLimitAmount;

  @JsonProperty("maxMktAmt")
  private String maxMarketAmount;

  @JsonProperty("maxStopSz")
  private String maxStopSize;

  @JsonProperty("maxTriggerSz")
  private String maxTriggerSize;

  @JsonProperty("maxTwapSz")
  private String maxTwapSize;

  @JsonProperty("maxIcebergSz")
  private String maxIcebergSize;

  @JsonProperty("posLmtAmt")
  private String positionLimitAmount;

  @JsonProperty("posLmtPct")
  private String positionLimitPercent;

  @JsonProperty("maxPxLmtPct")
  private String maxPriceLimitPercent;

  @JsonProperty("floatPxLmtPct")
  private String floatPriceLimitPercent;

  @JsonProperty("initPxLmtPct")
  private String initialPriceLimitPercent;

  @JsonProperty("longPosRemainingQuota")
  private String longPositionRemainingQuota;

  @JsonProperty("shortPosRemainingQuota")
  private String shortPositionRemainingQuota;

  @JsonProperty("maxPlatOICoinLmt")
  private String maxPlatformOICoinLimit;

  @JsonProperty("maxPlatOILmt")
  private String maxPlatformOILimit;

  @JsonProperty("auctionEndTime")
  private String auctionEndTime;

  @JsonProperty("contTdSwTime")
  private String contractTradingSwitchTime;

  @JsonProperty("preMktSwTime")
  private String preMarketSwitchTime;

  @JsonProperty("futureSettlement")
  private boolean futureSettlement;

  @JsonProperty("freq")
  private String frequency;

  @JsonProperty("groupId")
  private String groupId;

  @JsonProperty("method")
  private String method;

  @JsonProperty("openType")
  private String openType;

  @JsonProperty("seriesId")
  private String seriesId;

  @JsonProperty("tradeQuoteCcyList")
  private List<String> tradeQuoteCurrencies;

  @JsonProperty("upcChg")
  private List<OkxUpcomingPriceCapChange> upcomingPriceCapChanges;
}
