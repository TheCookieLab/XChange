package org.knowm.xchange.okex.dto.trade;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.knowm.xchange.okx.dto.trade.OkxAttachAlgoOrder;

/**
 * @deprecated use {@link org.knowm.xchange.okx.dto.trade.OkxAttachAlgoOrder} instead.
 */
@Deprecated
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class OkexAttachAlgoOrder {

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

  public OkxAttachAlgoOrder to() {
    return OkxAttachAlgoOrder.builder()
        .takeProfitTriggerPrice(takeProfitTriggerPrice)
        .takeProfitOrderPrice(takeProfitOrderPrice)
        .stopLossTriggerPrice(stopLossTriggerPrice)
        .stopLossOrderPrice(stopLossOrderPrice)
        .takeProfitTriggerPriceType(takeProfitTriggerPriceType)
        .stopLossTriggerPriceType(stopLossTriggerPriceType)
        .amount(amount)
        .build();
  }
}
