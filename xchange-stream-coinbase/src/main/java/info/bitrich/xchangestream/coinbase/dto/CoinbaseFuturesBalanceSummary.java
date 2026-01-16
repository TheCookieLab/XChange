package info.bitrich.xchangestream.coinbase.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public final class CoinbaseFuturesBalanceSummary {
  private final BigDecimal futuresBuyingPower;
  private final BigDecimal totalUsdBalance;
  private final BigDecimal unrealizedPnl;
  private final BigDecimal dailyRealizedPnl;
  private final BigDecimal initialMargin;
  private final BigDecimal availableMargin;

  @JsonCreator
  public CoinbaseFuturesBalanceSummary(
      @JsonProperty("futures_buying_power") BigDecimal futuresBuyingPower,
      @JsonProperty("total_usd_balance") BigDecimal totalUsdBalance,
      @JsonProperty("unrealized_pnl") BigDecimal unrealizedPnl,
      @JsonProperty("daily_realized_pnl") BigDecimal dailyRealizedPnl,
      @JsonProperty("initial_margin") BigDecimal initialMargin,
      @JsonProperty("available_margin") BigDecimal availableMargin) {
    this.futuresBuyingPower = futuresBuyingPower;
    this.totalUsdBalance = totalUsdBalance;
    this.unrealizedPnl = unrealizedPnl;
    this.dailyRealizedPnl = dailyRealizedPnl;
    this.initialMargin = initialMargin;
    this.availableMargin = availableMargin;
  }

  public BigDecimal getFuturesBuyingPower() {
    return futuresBuyingPower;
  }

  public BigDecimal getTotalUsdBalance() {
    return totalUsdBalance;
  }

  public BigDecimal getUnrealizedPnl() {
    return unrealizedPnl;
  }

  public BigDecimal getDailyRealizedPnl() {
    return dailyRealizedPnl;
  }

  public BigDecimal getInitialMargin() {
    return initialMargin;
  }

  public BigDecimal getAvailableMargin() {
    return availableMargin;
  }

  @Override
  public String toString() {
    return "CoinbaseFuturesBalanceSummary{"
        + "futuresBuyingPower="
        + futuresBuyingPower
        + ", totalUsdBalance="
        + totalUsdBalance
        + ", unrealizedPnl="
        + unrealizedPnl
        + ", dailyRealizedPnl="
        + dailyRealizedPnl
        + ", initialMargin="
        + initialMargin
        + ", availableMargin="
        + availableMargin
        + '}';
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof CoinbaseFuturesBalanceSummary)) return false;
    CoinbaseFuturesBalanceSummary other = (CoinbaseFuturesBalanceSummary) obj;
    return Objects.equals(futuresBuyingPower, other.futuresBuyingPower)
        && Objects.equals(totalUsdBalance, other.totalUsdBalance)
        && Objects.equals(unrealizedPnl, other.unrealizedPnl)
        && Objects.equals(dailyRealizedPnl, other.dailyRealizedPnl)
        && Objects.equals(initialMargin, other.initialMargin)
        && Objects.equals(availableMargin, other.availableMargin);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        futuresBuyingPower,
        totalUsdBalance,
        unrealizedPnl,
        dailyRealizedPnl,
        initialMargin,
        availableMargin);
  }
}
