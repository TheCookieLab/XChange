package org.knowm.xchange.okx.dto.trade;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

/** Author: Max Gao (gaamox@tutanota.com) Created: 10-06-2021 */
@Builder
public class OkxCancelOrderRequest {
  @JsonProperty("instId")
  private String instrumentId;

  @JsonProperty("instIdCode")
  private String instIdCode;

  @JsonProperty("ordId")
  private String orderId;

  @JsonProperty("clOrdId")
  private String clientOrderId;
}
