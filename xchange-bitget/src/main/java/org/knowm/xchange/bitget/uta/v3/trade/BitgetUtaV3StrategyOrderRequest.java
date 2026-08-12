package org.knowm.xchange.bitget.uta.v3.trade;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

/**
 * Strategy (trigger / TP-SL) order request for {@code POST /api/v3/trade/place-strategy-order}.
 *
 * <p>{@code type} {@code tpsl} (default) or {@code trigger}; {@code tpslMode} {@code full}
 * (default) or {@code partial}; {@code qty} required for partial-tpsl and trigger orders. {@code
 * tpTriggerBy}/{@code slTriggerBy}/{@code triggerBy} {@code market} (default) or {@code mark};
 * {@code tpOrderType}/{@code slOrderType}/{@code triggerOrderType} {@code market} or {@code limit}.
 * {@code clientOid} provides 6-hour idempotency for {@code tpsl} orders only (not supported for
 * trigger). Optional header {@code X-CHANNEL-API-CODE} carries the broker rebate code.
 */
@Data
@Builder(toBuilder = true)
@Jacksonized
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BitgetUtaV3StrategyOrderRequest {

  @JsonProperty("category")
  private String category;

  @JsonProperty("symbol")
  private String symbol;

  @JsonProperty("clientOid")
  private String clientOid;

  @JsonProperty("type")
  private String type;

  @JsonProperty("tpslMode")
  private String tpslMode;

  @JsonProperty("qty")
  private BigDecimal qty;

  @JsonProperty("side")
  private String side;

  @JsonProperty("posSide")
  private String posSide;

  @JsonProperty("reduceOnly")
  private String reduceOnly;

  @JsonProperty("tpTriggerBy")
  private String tpTriggerBy;

  @JsonProperty("slTriggerBy")
  private String slTriggerBy;

  @JsonProperty("takeProfit")
  private BigDecimal takeProfit;

  @JsonProperty("stopLoss")
  private BigDecimal stopLoss;

  @JsonProperty("tpOrderType")
  private String tpOrderType;

  @JsonProperty("slOrderType")
  private String slOrderType;

  @JsonProperty("tpLimitPrice")
  private BigDecimal tpLimitPrice;

  @JsonProperty("slLimitPrice")
  private BigDecimal slLimitPrice;

  @JsonProperty("triggerBy")
  private String triggerBy;

  @JsonProperty("triggerPrice")
  private BigDecimal triggerPrice;

  @JsonProperty("triggerOrderType")
  private String triggerOrderType;

  @JsonProperty("triggerOrderPrice")
  private BigDecimal triggerOrderPrice;
}
