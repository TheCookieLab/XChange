package org.knowm.xchange.okex.dto.trade;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.knowm.xchange.okx.dto.trade.OkxAlgoOrderRequest;

/**
 * @deprecated use {@link org.knowm.xchange.okx.dto.trade.OkxAlgoOrderRequest} instead.
 */
@Deprecated
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class OkexAlgoOrderRequest {

  @JsonProperty("instId")
  private String instrumentId;

  @JsonProperty("tdMode")
  private String tradeMode;

  @JsonProperty("ccy")
  private String marginCurrency;

  @JsonProperty("side")
  private String side;

  @JsonProperty("posSide")
  private String posSide;

  @JsonProperty("ordType")
  private String orderType;

  @JsonProperty("sz")
  private String amount;

  @JsonProperty("px")
  private String price;

  @JsonProperty("reduceOnly")
  private boolean reducePosition;

  @JsonProperty("tpTriggerPx")
  private String takeProfitTriggerPrice;

  @JsonProperty("tpOrdPx")
  private String takeProfitOrderPrice;

  @JsonProperty("slTriggerPx")
  private String stopLossTriggerPrice;

  @JsonProperty("slOrdPx")
  private String stopLossOrderPrice;

  @JsonProperty("tpTriggerPxType")
  private String takeProfitTriggerPriceType;

  @JsonProperty("slTriggerPxType")
  private String stopLossTriggerPriceType;

  @JsonProperty("tpOrdKind")
  private String takeProfitOrderKind;

  @JsonProperty("algoClOrdId")
  private String algoClientOrderId;

  @JsonProperty("tag")
  private String tag;

  @JsonProperty("callbackRatio")
  private String callbackRatio;

  @JsonProperty("callbackSpread")
  private String callbackSpread;

  @JsonProperty("activePx")
  private String activePrice;

  @JsonProperty("cxlOnClosePos")
  private boolean cancelOnClosePosition;

  public OkxAlgoOrderRequest to() {
    return OkxAlgoOrderRequest.builder()
        .instrumentId(instrumentId)
        .tradeMode(tradeMode)
        .marginCurrency(marginCurrency)
        .side(side)
        .posSide(posSide)
        .orderType(orderType)
        .amount(amount)
        .price(price)
        .reducePosition(reducePosition)
        .takeProfitTriggerPrice(takeProfitTriggerPrice)
        .takeProfitOrderPrice(takeProfitOrderPrice)
        .stopLossTriggerPrice(stopLossTriggerPrice)
        .stopLossOrderPrice(stopLossOrderPrice)
        .takeProfitTriggerPriceType(takeProfitTriggerPriceType)
        .stopLossTriggerPriceType(stopLossTriggerPriceType)
        .takeProfitOrderKind(takeProfitOrderKind)
        .algoClientOrderId(algoClientOrderId)
        .tag(tag)
        .callbackRatio(callbackRatio)
        .callbackSpread(callbackSpread)
        .activePrice(activePrice)
        .cancelOnClosePosition(cancelOnClosePosition)
        .build();
  }
}
