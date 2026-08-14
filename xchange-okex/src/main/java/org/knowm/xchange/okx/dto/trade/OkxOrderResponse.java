package org.knowm.xchange.okx.dto.trade;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

/** Author: Max Gao (gaamox@tutanota.com) Created: 09-06-2021 */
/** Response DTO received from placing, cancelling, and amending orders * */
@Getter
public class OkxOrderResponse {
  @JsonProperty("ordId")
  private String orderId;

  @JsonProperty("clOrdId")
  private String clientOrderId;

  @JsonProperty("tag")
  private String orderTag;

  @JsonProperty("sCode")
  private String code;

  @JsonProperty("sMsg")
  private String message;

  @JsonProperty("ts")
  private Long ts;

  /**
   * Builds a successful placement-style response for an order that already exists under the given
   * client order id. Used by idempotent placement reconciliation to return an existing order
   * instead of re-submitting it.
   *
   * @param orderId the exchange order id of the existing order
   * @param clientOrderId the client order id that was replayed
   */
  public static OkxOrderResponse replay(String orderId, String clientOrderId) {
    OkxOrderResponse response = new OkxOrderResponse();
    response.orderId = orderId;
    response.clientOrderId = clientOrderId;
    response.code = "0";
    return response;
  }
}
