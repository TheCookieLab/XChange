package org.knowm.xchange.kucoin.uta.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

/**
 * Cursor-paginated UTA order history.
 *
 * <p>Pagination uses {@code lastId} (cursor of the last record) plus {@code pageSize}; there is no
 * total count. A {@code lastId} of {@code null} or equal to the previous request signals the end.
 */
@Data
public class UtaOrderHistory {

  @JsonProperty("lastId")
  private Long lastId;

  @JsonProperty("tradeType")
  private String tradeType;

  @JsonProperty("items")
  private List<UtaOrder> items;
}
