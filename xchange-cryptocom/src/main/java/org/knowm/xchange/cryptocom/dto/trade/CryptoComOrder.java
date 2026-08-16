package org.knowm.xchange.cryptocom.dto.trade;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CryptoComOrder {

  @JsonProperty("account_id")
  private String accountId;

  @JsonProperty("order_id")
  private String orderId;

  @JsonProperty("client_oid")
  private String clientOid;

  @JsonProperty("order_type")
  private String orderType;

  @JsonProperty("time_in_force")
  private String timeInForce;

  @JsonProperty("side")
  private String side;

  @JsonProperty("quantity")
  private String quantity;

  @JsonProperty("limit_price")
  private String limitPrice;

  @JsonProperty("order_value")
  private String orderValue;

  /** Notional value of the order (derivatives and advanced orders). */
  @JsonProperty("notional")
  private String notional;

  /** Side of the position the order opens or closes: LONG or SHORT (derivatives). */
  @JsonProperty("position_side")
  private String positionSide;

  /** Close-position (reduce-only) orders close the open derivative position on fill. */
  @JsonProperty("close_position")
  private Boolean closePosition;

  /** Execution instructions, e.g. POST_ONLY or REDUCE_ONLY. */
  @JsonProperty("exec_inst")
  private String execInst;

  /** Trigger price expressed as a decimal string (advanced/trigger orders). */
  @JsonProperty("trigger_price")
  private String triggerPrice;

  @JsonProperty("avg_price")
  private String avgPrice;

  @JsonProperty("cumulative_quantity")
  private String cumulativeQuantity;

  @JsonProperty("cumulative_value")
  private String cumulativeValue;

  @JsonProperty("cumulative_fee")
  private String cumulativeFee;

  @JsonProperty("status")
  private String status;

  @JsonProperty("order_date")
  private String orderDate;

  @JsonProperty("instrument_name")
  private String instrumentName;

  @JsonProperty("fee_instrument_name")
  private String feeInstrumentName;

  @JsonProperty("create_time")
  private Long createTime;

  @JsonProperty("update_time")
  private Long updateTime;

  // String, not Integer: the user.order WebSocket channel (which reuses this DTO) can send a
  // non-numeric reject reason; the field is unused by any adapter, so String is safe either way.
  @JsonProperty("reason")
  private String reason;
}
