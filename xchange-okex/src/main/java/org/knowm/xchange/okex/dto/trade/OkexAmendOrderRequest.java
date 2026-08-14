package org.knowm.xchange.okex.dto.trade;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import org.knowm.xchange.okx.dto.trade.OkxAmendOrderRequest;

/**
 * @deprecated use {@link org.knowm.xchange.okx.dto.trade.OkxAmendOrderRequest} instead.
 */
@Deprecated
@Builder
@Getter
public class OkexAmendOrderRequest {

  @JsonProperty("instId")
  private String instrumentId;

  @JsonProperty("instIdCode")
  private String instIdCode;

  @JsonProperty("cxlOnFail")
  private boolean cancelOnFail;

  @JsonProperty("ordId")
  private String orderId;

  @JsonProperty("clOrdId")
  private String clientOrderId;

  @JsonProperty("reqId")
  private String requestId;

  @JsonProperty("newSz")
  private String amendedAmount;

  @JsonProperty("newPx")
  private String amendedPrice;

  public OkxAmendOrderRequest to() {
    return OkxAmendOrderRequest.builder()
        .instrumentId(instrumentId)
        .instIdCode(instIdCode)
        .cancelOnFail(cancelOnFail)
        .orderId(orderId)
        .clientOrderId(clientOrderId)
        .requestId(requestId)
        .amendedAmount(amendedAmount)
        .amendedPrice(amendedPrice)
        .build();
  }
}
