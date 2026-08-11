package org.knowm.xchange.kucoin.uta.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * Batch order cancellation request; up to 20 orders per call, each identified by {@code orderId}
 * or {@code clientOid} (orderId takes precedence).
 *
 * @see <a href="https://www.kucoin.com/docs-new/rest/ua/batch-cancel-order-by-id">Batch Cancel
 *     Orders By ID</a>
 */
@Data
@Builder
@JsonInclude(Include.NON_NULL)
public class UtaBatchCancelRequest {

  @JsonProperty("tradeType")
  private String tradeType;

  @JsonProperty("cancelOrderList")
  private List<Item> cancelOrderList;

  @Data
  @Builder
  @JsonInclude(Include.NON_NULL)
  public static class Item {
    @JsonProperty("symbol")
    private String symbol;

    @JsonProperty("orderId")
    private String orderId;

    @JsonProperty("clientOid")
    private String clientOid;
  }
}
