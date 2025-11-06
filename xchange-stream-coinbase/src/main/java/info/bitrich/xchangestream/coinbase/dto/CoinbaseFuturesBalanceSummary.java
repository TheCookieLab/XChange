package info.bitrich.xchangestream.coinbase.dto;

import java.math.BigDecimal;
import java.util.Objects;

public final class CoinbaseFuturesBalanceSummary {
  private final BigDecimal futuresBuyingPower;
  private final BigDecimal totalUsdBalance;
  private final BigDecimal unrealizedPnl;
  private final BigDecimal dailyRealizedPnl;
  private final BigDecimal initialMargin;
  private final BigDecimal availableMargin;

  public CoinbaseFuturesBalanceSummary(
      BigDecimal futuresBuyingPower,
      BigDecimal totalUsdBalance,
      BigDecimal unrealizedPnl,
      BigDecimal dailyRealizedPnl,
      BigDecimal initialMargin,
      BigDecimal availableMargin) {
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

