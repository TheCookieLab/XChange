package org.knowm.xchange.kucoin.uta.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

/**
 * UTA order cancellation request.
 *
 * <p>At least one of {@code orderId} or {@code clientOid} must be provided; {@code orderId} takes
 * precedence when both are present.
 */
@Data
@Builder
@JsonInclude(Include.NON_NULL)
public class UtaOrderCancelRequest {

  @JsonProperty("tradeType")
  private String tradeType;

  @JsonProperty("symbol")
  private String symbol;

  @JsonProperty("orderId")
  private String orderId;

  @JsonProperty("clientOid")
  private String clientOid;
}
