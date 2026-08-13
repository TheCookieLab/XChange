package org.knowm.xchange.gateio.dto.account;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A spot order's take-profit/stop-loss trigger configuration (nested {@code stop_profit} /
 * {@code stop_loss} object of an order).
 *
 * <p>Wire contract pinned in {@code protocol/gate-api-v4-2026-08-13.json}; trigger prices are
 * exact provider decimals.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GateioOrderTrigger {

  @JsonProperty("trigger_price")
  String triggerPrice;

  @JsonProperty("order_price")
  String orderPrice;
}
