package org.knowm.xchange.bitget.uta.v3.trade;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

/**
 * Bitget UTA v3 order (shared shape for pending, history and order-info).
 *
 * <p>Wire enums: orderStatus {@code live|new|partially_filled|filled|cancelled}; execType {@code
 * normal|offset|reduce|liquidation|delivery}; timeInForce {@code ioc|fok|gtc|post_only|rpi};
 * delegateType is a large provider enum (normal, market, plan_limit, ...). {@code tradeSide}
 * ({@code open|close}) is only present on order-info responses. Timestamps are Unix milliseconds as
 * decimal strings.
 */
@Data
@Builder
@Jacksonized
public class BitgetUtaV3Order {

  @JsonProperty("orderId")
  private String orderId;

  @JsonProperty("clientOid")
  private String clientOid;

  @JsonProperty("category")
  private String category;

  @JsonProperty("symbol")
  private String symbol;

  @JsonProperty("orderType")
  private String orderType;

  @JsonProperty("side")
  private String side;

  @JsonProperty("price")
  private BigDecimal price;

  @JsonProperty("qty")
  private BigDecimal qty;

  @JsonProperty("amount")
  private BigDecimal amount;

  @JsonProperty("cumExecQty")
  private BigDecimal cumExecQty;

  @JsonProperty("cumExecValue")
  private BigDecimal cumExecValue;

  @JsonProperty("avgPrice")
  private BigDecimal avgPrice;

  @JsonProperty("timeInForce")
  private String timeInForce;

  @JsonProperty("orderStatus")
  private String orderStatus;

  @JsonProperty("posSide")
  private String posSide;

  @JsonProperty("holdMode")
  private String holdMode;

  @JsonProperty("delegateType")
  private String delegateType;

  @JsonProperty("reduceOnly")
  private String reduceOnly;

  @JsonProperty("marginMode")
  private String marginMode;

  @JsonProperty("stpMode")
  private String stpMode;

  @JsonProperty("takeProfit")
  private BigDecimal takeProfit;

  @JsonProperty("stopLoss")
  private BigDecimal stopLoss;

  @JsonProperty("tpTriggerBy")
  private String tpTriggerBy;

  @JsonProperty("slTriggerBy")
  private String slTriggerBy;

  @JsonProperty("tpOrderType")
  private String tpOrderType;

  @JsonProperty("slOrderType")
  private String slOrderType;

  @JsonProperty("tpLimitPrice")
  private BigDecimal tpLimitPrice;

  @JsonProperty("slLimitPrice")
  private BigDecimal slLimitPrice;

  @JsonProperty("feeDetail")
  private java.util.List<BitgetUtaV3Fee> feeDetail;

  @JsonProperty("cancelReason")
  private String cancelReason;

  @JsonProperty("execType")
  private String execType;

  /** Present on order-info responses only. */
  @JsonProperty("tradeSide")
  private String tradeSide;

  @JsonProperty("createdTime")
  private String createdTime;

  @JsonProperty("updatedTime")
  private String updatedTime;

  /** Fee breakdown entry: fee coin and fee amount. */
  @Data
  @Builder
  @Jacksonized
  public static class BitgetUtaV3Fee {

    @JsonProperty("feeCoin")
    private String feeCoin;

    @JsonProperty("fee")
    private BigDecimal fee;
  }
}
