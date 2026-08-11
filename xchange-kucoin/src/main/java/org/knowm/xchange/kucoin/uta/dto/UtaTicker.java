package org.knowm.xchange.kucoin.uta.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import lombok.Data;

/** UTA 24h ticker entry. */
@Data
public class UtaTicker {

  @JsonProperty("symbol")
  private String symbol;

  @JsonProperty("name")
  private String name;

  @JsonProperty("bestBidPrice")
  private BigDecimal bestBidPrice;

  @JsonProperty("bestBidSize")
  private BigDecimal bestBidSize;

  @JsonProperty("bestAskPrice")
  private BigDecimal bestAskPrice;

  @JsonProperty("bestAskSize")
  private BigDecimal bestAskSize;

  @JsonProperty("high")
  private BigDecimal high;

  @JsonProperty("low")
  private BigDecimal low;

  @JsonProperty("baseVolume")
  private BigDecimal baseVolume;

  @JsonProperty("quoteVolume")
  private BigDecimal quoteVolume;

  @JsonProperty("lastPrice")
  private BigDecimal lastPrice;

  @JsonProperty("open")
  private BigDecimal open;

  @JsonProperty("size")
  private BigDecimal size;

  @JsonProperty("priceChange")
  private BigDecimal priceChange;

  @JsonProperty("priceChangePercent")
  private BigDecimal priceChangePercent;

  @JsonProperty("indexPrice")
  private BigDecimal indexPrice;

  @JsonProperty("markPrice")
  private BigDecimal markPrice;
}
