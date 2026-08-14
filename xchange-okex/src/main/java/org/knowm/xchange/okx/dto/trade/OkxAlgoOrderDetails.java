package org.knowm.xchange.okx.dto.trade;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.ToString;

/**
 * A single OKX v5 algorithmic order as returned by {@code /api/v5/trade/orders-algo-pending} and
 * {@code /api/v5/trade/orders-algo-history}.
 */
@Getter
@ToString
public class OkxAlgoOrderDetails {

  @JsonProperty("instType")
  private String instrumentType;

  @JsonProperty("instId")
  private String instrumentId;

  @JsonProperty("ordId")
  private String orderId;

  @JsonProperty("algoId")
  private String algoId;

  @JsonProperty("clOrdId")
  private String clientOrderId;

  @JsonProperty("algoClOrdId")
  private String algoClientOrderId;

  @JsonProperty("tag")
  private String tag;

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
  private String reducePosition;

  @JsonProperty("tpTriggerPx")
  private String takeProfitTriggerPrice;

  @JsonProperty("tpOrdPx")
  private String takeProfitOrderPrice;

  @JsonProperty("slTriggerPx")
  private String stopLossTriggerPrice;

  @JsonProperty("slOrdPx")
  private String stopLossOrderPrice;

  @JsonProperty("triggerPx")
  private String triggerPrice;

  @JsonProperty("ordPx")
  private String orderPrice;

  @JsonProperty("actualPx")
  private String actualPrice;

  @JsonProperty("actualSz")
  private String actualSize;

  @JsonProperty("state")
  private String state;

  @JsonProperty("cTime")
  private String creationTime;

  @JsonProperty("uTime")
  private String updateTime;
}
