package org.knowm.xchange.okx.dto.trade;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * An attached take-profit / stop-loss (TP/SL) order embedded in a place-order request via {@code
 * attachAlgoOrs} or {@code attachAlgoCls}.
 *
 * <p>{@code attachAlgoOrs} attaches TP/SL to a new position (and includes {@code sz}); {@code
 * attachAlgoCls} attaches TP/SL to close an existing position (without {@code sz}).
 */
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class OkxAttachAlgoOrder {

  @JsonProperty("tpTriggerPx")
  private String takeProfitTriggerPrice;

  @JsonProperty("tpOrdPx")
  private String takeProfitOrderPrice;

  @JsonProperty("slTriggerPx")
  private String stopLossTriggerPrice;

  @JsonProperty("slOrdPx")
  private String stopLossOrderPrice;

  @JsonProperty("tpTriggerPxType")
  private String takeProfitTriggerPriceType;

  @JsonProperty("slTriggerPxType")
  private String stopLossTriggerPriceType;

  @JsonProperty("sz")
  private String amount;
}
