package org.knowm.xchange.bitget.uta.v3.trade;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

/**
 * Modify-order request body for {@code POST /api/v3/trade/modify-order}.
 *
 * <p>{@code orderId} or {@code clientOid} is required; at least one of {@code qty}/{@code price}
 * must be supplied. Only unfilled orders are modifiable; no concurrent modify requests for the same
 * order. {@code autoCancel} default {@code no} — when {@code yes}, the original order is cancelled
 * if the modify fails. TP/SL convention: pass {@code "0"} to cancel a TP/SL; leave empty to keep
 * the existing value.
 */
@Data
@Builder
@Jacksonized
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BitgetUtaV3ModifyOrderRequest {

  @JsonProperty("orderId")
  private String orderId;

  @JsonProperty("clientOid")
  private String clientOid;

  @JsonProperty("symbol")
  private String symbol;

  @JsonProperty("category")
  private String category;

  @JsonProperty("qty")
  private BigDecimal qty;

  @JsonProperty("price")
  private BigDecimal price;

  @JsonProperty("autoCancel")
  private String autoCancel;

  @JsonProperty("takeProfit")
  private String takeProfit;

  @JsonProperty("stopLoss")
  private String stopLoss;

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
}
