package org.knowm.xchange.coinbase.v3.dto.futures;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import lombok.Getter;

/**
 * A position returned by Coinbase's CFM positions endpoint.
 *
 * <p>Contract quantity and all prices/PnL values remain decimal values. The side is the API's
 * explicit {@code LONG}, {@code SHORT}, or {@code UNKNOWN} value and must not be inferred from a
 * signed quantity when the response supplies it.
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class CoinbaseFuturesPosition {

  private final String productId;
  private final String expirationTime;
  private final String side;
  private final BigDecimal numberOfContracts;
  private final BigDecimal currentPrice;
  private final BigDecimal avgEntryPrice;
  private final BigDecimal unrealizedPnl;
  private final BigDecimal dailyRealizedPnl;

  @JsonCreator
  public CoinbaseFuturesPosition(
      @JsonProperty("product_id") String productId,
      @JsonProperty("expiration_time") String expirationTime,
      @JsonProperty("side") String side,
      @JsonProperty("number_of_contracts") BigDecimal numberOfContracts,
      @JsonProperty("current_price") BigDecimal currentPrice,
      @JsonProperty("avg_entry_price") BigDecimal avgEntryPrice,
      @JsonProperty("unrealized_pnl") BigDecimal unrealizedPnl,
      @JsonProperty("daily_realized_pnl") BigDecimal dailyRealizedPnl) {
    this.productId = productId;
    this.expirationTime = expirationTime;
    this.side = side;
    this.numberOfContracts = numberOfContracts;
    this.currentPrice = currentPrice;
    this.avgEntryPrice = avgEntryPrice;
    this.unrealizedPnl = unrealizedPnl;
    this.dailyRealizedPnl = dailyRealizedPnl;
  }

  @Override
  public String toString() {
    return "CoinbaseFuturesPosition [productId=" + productId + ", side=" + side
        + ", numberOfContracts=" + numberOfContracts + "]";
  }
}
