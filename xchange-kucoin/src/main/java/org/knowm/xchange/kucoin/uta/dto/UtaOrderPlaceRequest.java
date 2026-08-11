package org.knowm.xchange.kucoin.uta.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;

/**
 * UTA unified order placement request.
 *
 * <p>Per the official schema: {@code tradeType}, {@code symbol}, {@code side}, {@code orderType},
 * {@code size} and {@code sizeUnit} are required. {@code clientOid} is mandatory for futures and
 * margin orders, max 40 characters (letters, digits, {@code _} and {@code -}).
 */
@Data
@Builder
@JsonInclude(Include.NON_NULL)
public class UtaOrderPlaceRequest {

  @JsonProperty("tradeType")
  private String tradeType;

  @JsonProperty("clientOid")
  private String clientOid;

  @JsonProperty("symbol")
  private String symbol;

  @JsonProperty("side")
  private String side;

  @JsonProperty("positionSide")
  private String positionSide;

  @JsonProperty("orderType")
  private String orderType;

  @JsonProperty("size")
  private String size;

  @JsonProperty("sizeUnit")
  private String sizeUnit;

  @JsonProperty("price")
  private String price;

  @JsonProperty("marginMode")
  private String marginMode;

  @JsonProperty("leverage")
  private String leverage;

  @JsonProperty("reduceOnly")
  private Boolean reduceOnly;

  @JsonProperty("stp")
  private String stp;

  @JsonProperty("timeInForce")
  private String timeInForce;

  @JsonProperty("cancelAfter")
  private Integer cancelAfter;

  @JsonProperty("postOnly")
  private Boolean postOnly;

  @JsonProperty("tags")
  private String tags;

  @JsonProperty("triggerDirection")
  private String triggerDirection;

  @JsonProperty("triggerPrice")
  private String triggerPrice;

  @JsonProperty("triggerPriceType")
  private String triggerPriceType;

  @JsonProperty("tpTriggerPrice")
  private String tpTriggerPrice;

  @JsonProperty("tpTriggerPriceType")
  private String tpTriggerPriceType;

  @JsonProperty("slTriggerPrice")
  private String slTriggerPrice;

  @JsonProperty("slTriggerPriceType")
  private String slTriggerPriceType;

  @JsonProperty("closeOrder")
  private Boolean closeOrder;

  /** Convenience numeric setter that renders the exact decimal string. */
  public static String toWire(BigDecimal value) {
    return value == null ? null : value.toPlainString();
  }
}
