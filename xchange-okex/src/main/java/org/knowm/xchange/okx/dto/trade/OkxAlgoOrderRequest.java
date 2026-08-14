package org.knowm.xchange.okx.dto.trade;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Request body for placing an algorithmic (algo) order via {@code /api/v5/trade/order-algo}.
 *
 * <p>Fields follow the OKX v5 wire keys. Only the fields relevant to the stable algo order types
 * (conditional, OCO, trigger, move-order-stop, iceberg, TWAP, ADL) are modeled.
 */
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class OkxAlgoOrderRequest {

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
}
