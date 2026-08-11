package org.knowm.xchange.kucoin.uta.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

/**
 * Batch cancellation result with per-item outcomes: each item carries its own provider {@code
 * code}/{@code msg} so partial batch failures are never flattened.
 *
 * @see <a href="https://www.kucoin.com/docs-new/rest/ua/batch-cancel-order-by-id">Batch Cancel
 *     Orders By ID</a>
 */
@Data
public class UtaBatchCancelResult {

  @JsonProperty("tradeType")
  private String tradeType;

  @JsonProperty("items")
  private List<Item> items;

  @Data
  public static class Item {
    @JsonProperty("code")
    private String code;

    @JsonProperty("msg")
    private String msg;

    @JsonProperty("orderId")
    private String orderId;

    @JsonProperty("ts")
    private Long ts;

    @JsonProperty("clientOid")
    private String clientOid;

    public boolean isSuccessful() {
      return "200000".equals(code);
    }
  }
}
