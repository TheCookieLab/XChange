package org.knowm.xchange.coinbase.v3.dto.perpetuals;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import lombok.Getter;

/**
 * Represents a perpetuals position.
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class CoinbasePerpetualsPosition {

  private final String productId;
  private final String productUuid;
  private final String portfolioUuid;
  private final String symbol;
  private final String vwap;
  private final String side;
  private final BigDecimal netSize;
  private final String entryVwap;
  private final BigDecimal positionNotional;
  private final BigDecimal leverage;
  private final BigDecimal unrealizedPnl;
  private final String expiryTime;

  @JsonCreator
  public CoinbasePerpetualsPosition(
      @JsonProperty("product_id") String productId,
      @JsonProperty("product_uuid") String productUuid,
      @JsonProperty("portfolio_uuid") String portfolioUuid,
      @JsonProperty("symbol") String symbol,
      @JsonProperty("vwap") String vwap,
      @JsonProperty("side") String side,
      @JsonProperty("net_size") BigDecimal netSize,
      @JsonProperty("entry_vwap") String entryVwap,
      @JsonProperty("position_notional") BigDecimal positionNotional,
      @JsonProperty("leverage") BigDecimal leverage,
      @JsonProperty("unrealized_pnl") BigDecimal unrealizedPnl,
      @JsonProperty("expiry_time") String expiryTime) {
    this.productId = productId;
    this.productUuid = productUuid;
    this.portfolioUuid = portfolioUuid;
    this.symbol = symbol;
    this.vwap = vwap;
    this.side = side;
    this.netSize = netSize;
    this.entryVwap = entryVwap;
    this.positionNotional = positionNotional;
    this.leverage = leverage;
    this.unrealizedPnl = unrealizedPnl;
    this.expiryTime = expiryTime;
  }

  @Override
  public String toString() {
    return "CoinbasePerpetualsPosition [symbol=" + symbol + ", side=" + side + ", netSize=" + netSize + "]";
  }
}

