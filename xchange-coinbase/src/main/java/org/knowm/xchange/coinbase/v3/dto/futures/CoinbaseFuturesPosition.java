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
  private final String contractSize;
  private final BigDecimal amount;
  private final String expiryTime;
  private final BigDecimal realizedPnl;
  private final BigDecimal entryPrice;

  /**
   * Creates a position from current and retained CFM wire fields.
   *
   * @param productId canonical Coinbase product identifier
   * @param expirationTime current contract expiration timestamp
   * @param side exchange position side
   * @param numberOfContracts decimal contract quantity
   * @param currentPrice current mark price
   * @param avgEntryPrice average entry price
   * @param unrealizedPnl unrealized profit or loss
   * @param dailyRealizedPnl current-day realized profit or loss
   * @param contractSize retained raw contract size
   * @param amount retained position amount
   * @param expiryTime retained contract expiration timestamp alias
   * @param realizedPnl retained realized profit or loss
   * @param entryPrice retained entry price
   */
  @JsonCreator
  public CoinbaseFuturesPosition(
      @JsonProperty("product_id") String productId,
      @JsonProperty("expiration_time") String expirationTime,
      @JsonProperty("side") String side,
      @JsonProperty("number_of_contracts") BigDecimal numberOfContracts,
      @JsonProperty("current_price") BigDecimal currentPrice,
      @JsonProperty("avg_entry_price") BigDecimal avgEntryPrice,
      @JsonProperty("unrealized_pnl") BigDecimal unrealizedPnl,
      @JsonProperty("daily_realized_pnl") BigDecimal dailyRealizedPnl,
      @JsonProperty("contract_size") String contractSize,
      @JsonProperty("amount") BigDecimal amount,
      @JsonProperty("expiry_time") String expiryTime,
      @JsonProperty("realized_pnl") BigDecimal realizedPnl,
      @JsonProperty("entry_price") BigDecimal entryPrice) {
    this.productId = productId;
    this.expirationTime = expirationTime != null ? expirationTime : expiryTime;
    this.side = side;
    this.numberOfContracts = numberOfContracts;
    this.currentPrice = currentPrice;
    this.avgEntryPrice = avgEntryPrice;
    this.unrealizedPnl = unrealizedPnl;
    this.dailyRealizedPnl = dailyRealizedPnl;
    this.contractSize = contractSize;
    this.amount = amount;
    this.expiryTime = expiryTime != null ? expiryTime : expirationTime;
    this.realizedPnl = realizedPnl;
    this.entryPrice = entryPrice;
  }

  /** Creates a position from the current eight-field CFM response shape. */
  public CoinbaseFuturesPosition(
      String productId,
      String expirationTime,
      String side,
      BigDecimal numberOfContracts,
      BigDecimal currentPrice,
      BigDecimal avgEntryPrice,
      BigDecimal unrealizedPnl,
      BigDecimal dailyRealizedPnl) {
    this(
        productId,
        expirationTime,
        side,
        numberOfContracts,
        currentPrice,
        avgEntryPrice,
        unrealizedPnl,
        dailyRealizedPnl,
        null,
        null,
        null,
        null,
        null);
  }

  /**
   * Preserves the pre-1.0.2 position construction contract.
   *
   * @deprecated use the current CFM position fields supplied by the eight-argument constructor
   */
  @Deprecated
  public CoinbaseFuturesPosition(
      String productId,
      String contractSize,
      String side,
      BigDecimal amount,
      BigDecimal avgEntryPrice,
      BigDecimal currentPrice,
      BigDecimal unrealizedPnl,
      String expiryTime,
      BigDecimal numberOfContracts,
      BigDecimal realizedPnl,
      BigDecimal entryPrice) {
    this.productId = productId;
    this.expirationTime = expiryTime;
    this.side = side;
    this.numberOfContracts = numberOfContracts;
    this.currentPrice = currentPrice;
    this.avgEntryPrice = avgEntryPrice;
    this.unrealizedPnl = unrealizedPnl;
    this.dailyRealizedPnl = realizedPnl;
    this.contractSize = contractSize;
    this.amount = amount;
    this.expiryTime = expiryTime;
    this.realizedPnl = realizedPnl;
    this.entryPrice = entryPrice;
  }

  @Override
  public String toString() {
    return "CoinbaseFuturesPosition [productId=" + productId + ", side=" + side
        + ", numberOfContracts=" + numberOfContracts + "]";
  }
}
