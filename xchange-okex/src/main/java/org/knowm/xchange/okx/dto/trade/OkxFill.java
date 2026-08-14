package org.knowm.xchange.okx.dto.trade;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.ToString;

/**
 * A single OKX v5 fill (transaction detail) as returned by {@code /api/v5/trade/fills} and {@code
 * /api/v5/trade/fills-history}.
 */
@Getter
@ToString
public class OkxFill {

  @JsonProperty("instType")
  private String instrumentType;

  @JsonProperty("instId")
  private String instrumentId;

  @JsonProperty("tradeId")
  private String tradeId;

  @JsonProperty("ordId")
  private String orderId;

  @JsonProperty("clOrdId")
  private String clientOrderId;

  @JsonProperty("billId")
  private String billId;

  @JsonProperty("tag")
  private String tag;

  @JsonProperty("px")
  private String price;

  @JsonProperty("sz")
  private String amount;

  @JsonProperty("side")
  private String side;

  @JsonProperty("posSide")
  private String posSide;

  @JsonProperty("fee")
  private String fee;

  @JsonProperty("feeCcy")
  private String feeCurrency;

  @JsonProperty("rebate")
  private String rebate;

  @JsonProperty("rebateCcy")
  private String rebateCurrency;

  @JsonProperty("fillPx")
  private String fillPrice;

  @JsonProperty("fillSz")
  private String fillSize;

  @JsonProperty("fillTime")
  private String fillTime;

  @JsonProperty("execType")
  private String executionType;

  @JsonProperty("ts")
  private String timestamp;
}
