package org.knowm.xchange.coinbase.v3.dto.products;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import lombok.Getter;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CoinbaseProductMarketTradesResponse {

  @Getter
  private final List<CoinbaseMarketTrade> marketTrades;

  @Getter
  private final BigDecimal bestBid;

  @Getter
  private final BigDecimal bestAsk;

  private CoinbaseProductMarketTradesResponse(
      @JsonProperty("trades") List<CoinbaseMarketTrade> marketTrades,
      @JsonProperty("best_bid") BigDecimal bestBid, @JsonProperty("best_ask") BigDecimal bestAsk) {
    this.marketTrades = marketTrades == null ? Collections.emptyList() : Collections.unmodifiableList(marketTrades);
    this.bestAsk = bestAsk;
    this.bestBid = bestBid;
  }

  @Override
  public String toString() {
    return "CoinbaseProductMarketTradesResponse [bestBid=" + bestBid + ", bestAsk=" + bestAsk
        + ", marketTrades:" + marketTrades + "]";
  }

}
