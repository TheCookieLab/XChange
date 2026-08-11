package org.knowm.xchange.kucoin.uta.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

/**
 * Cursor-paginated position history; data retained for 3 months, each query bounded to 7x24 hours.
 *
 * @see <a href="https://www.kucoin.com/docs-new/rest/ua/get-position-history-uta">Get Positions
 *     History (UTA)</a>
 */
@Data
public class UtaPositionHistory {

  @JsonProperty("items")
  private List<Item> items;

  /** Cursor of the last record; {@code null} signals the end. */
  @JsonProperty("lastId")
  private Long lastId;

  @Data
  public static class Item {
    @JsonProperty("closeId")
    private String closeId;

    @JsonProperty("symbol")
    private String symbol;

    @JsonProperty("marginMode")
    private String marginMode;

    @JsonProperty("side")
    private String side;

    @JsonProperty("entryPrice")
    private BigDecimal entryPrice;

    @JsonProperty("closePrice")
    private BigDecimal closePrice;

    @JsonProperty("avgClosePrice")
    private BigDecimal avgClosePrice;

    @JsonProperty("maxSize")
    private BigDecimal maxSize;

    @JsonProperty("leverage")
    private BigDecimal leverage;

    @JsonProperty("realizedPnL")
    private BigDecimal realizedPnL;

    @JsonProperty("fee")
    private BigDecimal fee;

    @JsonProperty("tax")
    private BigDecimal tax;

    @JsonProperty("fundingFee")
    private BigDecimal fundingFee;

    /** Nanoseconds. */
    @JsonProperty("creationTime")
    private Long creationTime;

    /** Nanoseconds. */
    @JsonProperty("closingTime")
    private Long closingTime;
  }
}
