package org.knowm.xchange.okx.dto.trade;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

/**
 * Response DTO returned from placing, cancelling, and amending algorithmic orders ({@code
 * /api/v5/trade/order-algo}, {@code /api/v5/trade/cancel-algos}, {@code
 * /api/v5/trade/amend-algos}).
 *
 * <p>{@code ordId} is present for placement responses; {@code algoId} is present for cancel/amend
 * responses.
 */
@Getter
public class OkxAlgoOrderResponse {

  @JsonProperty("sCode")
  private String code;

  @JsonProperty("sMsg")
  private String message;

  @JsonProperty("clOrdId")
  private String clientOrderId;

  @JsonProperty("ordId")
  private String orderId;

  @JsonProperty("algoId")
  private String algoId;

  @JsonProperty("tag")
  private String orderTag;
}
