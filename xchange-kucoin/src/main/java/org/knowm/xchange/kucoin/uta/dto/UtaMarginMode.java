package org.knowm.xchange.kucoin.uta.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

/**
 * Futures margin-mode query result from {@code GET /api/ua/v1/unified/position/margin-mode}.
 *
 * @see <a href="https://www.kucoin.com/docs-new/rest/ua/get-margin-mode">Get Margin Mode</a>
 */
@Data
public class UtaMarginMode {

  @JsonProperty("ts")
  private Long ts;

  @JsonProperty("items")
  private List<Item> items;

  @Data
  public static class Item {
    @JsonProperty("symbol")
    private String symbol;

    /** CROSS or ISOLATED. */
    @JsonProperty("marginMode")
    private String marginMode;
  }
}
