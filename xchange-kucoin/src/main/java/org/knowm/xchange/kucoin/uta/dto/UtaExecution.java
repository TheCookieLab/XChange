package org.knowm.xchange.kucoin.uta.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import lombok.Data;

/** UTA execution/fill record from {@code GET /api/ua/v1/unified/order/execution}. */
@Data
public class UtaExecution {

  @JsonProperty("symbol")
  private String symbol;

  @JsonProperty("orderId")
  private String orderId;

  @JsonProperty("orderType")
  private String orderType;

  @JsonProperty("side")
  private String side;

  @JsonProperty("positionSide")
  private String positionSide;

  @JsonProperty("fillType")
  private String fillType;

  @JsonProperty("tradeId")
  private String tradeId;

  @JsonProperty("size")
  private BigDecimal size;

  @JsonProperty("value")
  private BigDecimal value;

  @JsonProperty("price")
  private BigDecimal price;

  /** Execution time in nanoseconds. */
  @JsonProperty("executionTime")
  private Long executionTime;

  @JsonProperty("fee")
  private BigDecimal fee;

  @JsonProperty("feeCurrency")
  private String feeCurrency;

  @JsonProperty("liquidityRole")
  private String liquidityRole;

  @JsonProperty("marginMode")
  private String marginMode;

  @JsonProperty("tax")
  private BigDecimal tax;
}
