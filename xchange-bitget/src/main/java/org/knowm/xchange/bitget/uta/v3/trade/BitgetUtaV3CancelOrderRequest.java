package org.knowm.xchange.bitget.uta.v3.trade;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

/**
 * Cancel-order request body for {@code POST /api/v3/trade/cancel-order}.
 *
 * <p>{@code orderId} or {@code clientOid} must be supplied; {@code orderId} takes priority when
 * both are present and mismatched. {@code category} and {@code symbol} are optional.
 */
@Data
@Builder
@Jacksonized
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BitgetUtaV3CancelOrderRequest {

  @JsonProperty("category")
  private String category;

  @JsonProperty("symbol")
  private String symbol;

  @JsonProperty("orderId")
  private String orderId;

  @JsonProperty("clientOid")
  private String clientOid;
}
