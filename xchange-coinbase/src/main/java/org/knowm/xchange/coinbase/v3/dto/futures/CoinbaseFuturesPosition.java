package org.knowm.xchange.coinbase.v3.dto.futures;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import lombok.Getter;

/**
 * Represents a futures position.
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class CoinbaseFuturesPosition {

  private final String productId;
  private final String contractSize;
  private final String side;
  private final BigDecimal amount;
  private final BigDecimal avgEntryPrice;
  private final BigDecimal currentPrice;
  private final BigDecimal unrealizedPnl;
  private final String expiryTime;
  private final BigDecimal numberOfContracts;
  private final BigDecimal realizedPnl;
  private final BigDecimal entryPrice;

  @JsonCreator
  public CoinbaseFuturesPosition(
      @JsonProperty("product_id") String productId,
      @JsonProperty("contract_size") String contractSize,
      @JsonProperty("side") String side,
      @JsonProperty("amount") BigDecimal amount,
      @JsonProperty("avg_entry_price") BigDecimal avgEntryPrice,
      @JsonProperty("current_price") BigDecimal currentPrice,
      @JsonProperty("unrealized_pnl") BigDecimal unrealizedPnl,
      @JsonProperty("expiry_time") String expiryTime,
      @JsonProperty("number_of_contracts") BigDecimal numberOfContracts,
      @JsonProperty("realized_pnl") BigDecimal realizedPnl,
      @JsonProperty("entry_price") BigDecimal entryPrice) {
    this.productId = productId;
    this.contractSize = contractSize;
    this.side = side;
    this.amount = amount;
    this.avgEntryPrice = avgEntryPrice;
    this.currentPrice = currentPrice;
    this.unrealizedPnl = unrealizedPnl;
    this.expiryTime = expiryTime;
    this.numberOfContracts = numberOfContracts;
    this.realizedPnl = realizedPnl;
    this.entryPrice = entryPrice;
  }

  @Override
  public String toString() {
    return "CoinbaseFuturesPosition [productId=" + productId + ", side=" + side + ", numberOfContracts=" + numberOfContracts + "]";
  }
}

