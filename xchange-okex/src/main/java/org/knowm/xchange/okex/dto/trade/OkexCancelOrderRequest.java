package org.knowm.xchange.okex.dto.trade;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import org.knowm.xchange.okx.dto.trade.OkxCancelOrderRequest;

/**
 * @deprecated use {@link org.knowm.xchange.okx.dto.trade.OkxCancelOrderRequest} instead.
 */
@Deprecated
@Builder
@Getter
public class OkexCancelOrderRequest {

  @JsonProperty("instId")
  private String instrumentId;

  @JsonProperty("instIdCode")
  private String instIdCode;

  @JsonProperty("ordId")
  private String orderId;

  @JsonProperty("clOrdId")
  private String clientOrderId;

  public OkxCancelOrderRequest to() {
    return OkxCancelOrderRequest.builder()
        .instrumentId(instrumentId)
        .instIdCode(instIdCode)
        .orderId(orderId)
        .clientOrderId(clientOrderId)
        .build();
  }
}
