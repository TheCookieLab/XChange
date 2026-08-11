package org.knowm.xchange.kucoin.uta.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import lombok.Data;

/**
 * UTA order record as returned by order history, order detail, and the private order WebSocket.
 *
 * <p>Status codes: 0 notTriggered, 1 triggered, 2 live, 3 filled, 4 partial filled, 5 canceled,
 * 6 partial canceled. Timestamps are nanoseconds on the wire.
 */
@Data
public class UtaOrder {

  @JsonProperty("orderId")
  private String orderId;

  @JsonProperty("clientOid")
  private String clientOid;

  @JsonProperty("status")
  private Integer status;

  @JsonProperty("filledSize")
  private BigDecimal filledSize;

  @JsonProperty("avgPrice")
  private BigDecimal avgPrice;

  @JsonProperty("fee")
  private BigDecimal fee;

  @JsonProperty("feeCurrency")
  private String feeCurrency;

  @JsonProperty("tax")
  private BigDecimal tax;

  @JsonProperty("tradeId")
  private String tradeId;

  @JsonProperty("symbol")
  private String symbol;

  @JsonProperty("orderType")
  private String orderType;

  @JsonProperty("side")
  private String side;

  @JsonProperty("positionSide")
  private String positionSide;

  @JsonProperty("size")
  private BigDecimal size;

  @JsonProperty("sizeUnit")
  private String sizeUnit;

  @JsonProperty("price")
  private BigDecimal price;

  @JsonProperty("reduceOnly")
  private Boolean reduceOnly;

  @JsonProperty("marginMode")
  private String marginMode;

  @JsonProperty("stp")
  private String stp;

  @JsonProperty("timeInForce")
  private String timeInForce;

  /** String for UTA accounts; the provider returns the cancel reason text directly. */
  @JsonProperty("cancelReason")
  private String cancelReason;

  @JsonProperty("cancelAfter")
  private Long cancelAfter;

  @JsonProperty("triggerDirection")
  private String triggerDirection;

  @JsonProperty("triggerPrice")
  private BigDecimal triggerPrice;

  @JsonProperty("triggerPriceType")
  private String triggerPriceType;

  @JsonProperty("tpTriggerPrice")
  private BigDecimal tpTriggerPrice;

  @JsonProperty("tpTriggerPriceType")
  private String tpTriggerPriceType;

  @JsonProperty("slTriggerPrice")
  private BigDecimal slTriggerPrice;

  @JsonProperty("slTriggerPriceType")
  private String slTriggerPriceType;

  @JsonProperty("postOnly")
  private Boolean postOnly;

  @JsonProperty("tags")
  private String tags;

  @JsonProperty("triggerOrderId")
  private String triggerOrderId;

  @JsonProperty("orderTime")
  private Long orderTime;

  @JsonProperty("updatedTime")
  private Long updatedTime;
}
