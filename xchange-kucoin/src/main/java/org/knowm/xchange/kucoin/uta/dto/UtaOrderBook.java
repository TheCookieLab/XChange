package org.knowm.xchange.kucoin.uta.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

/**
 * UTA aggregated order book.
 *
 * <p>{@code sequence} is the authoritative snapshot sequence; WebSocket {@code obu} deltas with
 * sequence numbers not exceeding it are stale and must be rejected.
 */
@Data
public class UtaOrderBook {

  @JsonProperty("tradeType")
  private String tradeType;

  @JsonProperty("symbol")
  private String symbol;

  @JsonProperty("sequence")
  private Long sequence;

  /** Bids ordered high to low; each entry is {@code [price, size]}. */
  @JsonProperty("bids")
  private List<List<BigDecimal>> bids;

  /** Asks ordered low to high; each entry is {@code [price, size]}. */
  @JsonProperty("asks")
  private List<List<BigDecimal>> asks;
}
