package org.knowm.xchange.bitget.uta.v3.market;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

/**
 * Bitget UTA v3 ticker.
 *
 * <p>{@code GET /api/v3/market/tickers?category=...} returns one object per symbol; keys follow the
 * verified live payload (note the 24h-named fields are {@code openPrice24h}, {@code highPrice24h},
 * {@code lowPrice24h}, {@code price24hPcnt}, {@code volume24h}, {@code turnover24h} and {@code
 * platformTurnover24h}).
 */
@Data
@Builder
@Jacksonized
public class BitgetUtaV3Ticker {

  @JsonProperty("category")
  private String category;

  @JsonProperty("symbol")
  private String symbol;

  @JsonProperty("ts")
  private String ts;

  @JsonProperty("lastPrice")
  private BigDecimal lastPrice;

  @JsonProperty("openPrice24h")
  private BigDecimal openPrice24h;

  @JsonProperty("highPrice24h")
  private BigDecimal highPrice24h;

  @JsonProperty("lowPrice24h")
  private BigDecimal lowPrice24h;

  @JsonProperty("ask1Price")
  private BigDecimal ask1Price;

  @JsonProperty("bid1Price")
  private BigDecimal bid1Price;

  @JsonProperty("bid1Size")
  private BigDecimal bid1Size;

  @JsonProperty("ask1Size")
  private BigDecimal ask1Size;

  /** Signed 24h percentage change, e.g. "0.005" for +0.5%. */
  @JsonProperty("price24hPcnt")
  private BigDecimal price24hPcnt;

  @JsonProperty("volume24h")
  private BigDecimal volume24h;

  @JsonProperty("turnover24h")
  private BigDecimal turnover24h;

  @JsonProperty("platformTurnover24h")
  private BigDecimal platformTurnover24h;
}
