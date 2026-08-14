package org.knowm.xchange.okx.dto.trade;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * An attached take-profit / stop-loss (TP/SL) order embedded in a place-order request via the
 * single top-level {@code attachAlgoOrds} list.
 *
 * <p>Items attach TP/SL to a new position and then include {@code sz}; items that attach TP/SL to
 * close an existing position omit {@code sz}. OKX accepts both kinds in the same {@code
 * attachAlgoOrds} array.
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
