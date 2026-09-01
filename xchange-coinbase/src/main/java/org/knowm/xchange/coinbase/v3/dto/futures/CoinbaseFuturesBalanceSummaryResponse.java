package org.knowm.xchange.coinbase.v3.dto.futures;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import lombok.Getter;
import org.knowm.xchange.coinbase.v3.dto.accounts.CoinbaseAmount;

/**
 * Response envelope for Coinbase's CFM balance-summary endpoint.
 *
 * <p>The API places all monetary values below {@code balance_summary}; each nested amount retains
 * the response currency. A missing summary is not converted into a synthetic zero balance.
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class CoinbaseFuturesBalanceSummaryResponse {

  private final CoinbaseFuturesBalanceSummary balanceSummary;
  private final List<CoinbaseFuturesPosition> expiringFutures;

  /**
   * Creates the current wrapped balance-summary response.
   *
   * @param balanceSummary current CFM balance and margin measures
   */
  @JsonCreator
  public CoinbaseFuturesBalanceSummaryResponse(
      @JsonProperty("balance_summary") CoinbaseFuturesBalanceSummary balanceSummary) {
    this.balanceSummary = balanceSummary;
    this.expiringFutures = Collections.emptyList();
  }

  /**
   * Preserves the pre-1.0.2 flat response construction contract for source and binary clients.
   *
   * <p>Because the legacy schema omitted currencies, monetary values are represented as USD in the
   * wrapped model. Exchange responses always use the current wrapped constructor and retain their
   * wire currencies.
   *
   * @deprecated construct the current wrapped {@link CoinbaseFuturesBalanceSummary} response
   */
  @Deprecated
  public CoinbaseFuturesBalanceSummaryResponse(
      BigDecimal futuresBuyingPower,
      BigDecimal totalUsdBalance,
      BigDecimal cbiUsdBalance,
      BigDecimal cfmUsdBalance,
      BigDecimal totalOpenOrdersHoldAmount,
      BigDecimal unrealizedPnl,
      BigDecimal dailyRealizedPnl,
      BigDecimal initialMargin,
      BigDecimal availableMargin,
      BigDecimal liquidationThreshold,
      BigDecimal liquidationBufferAmount,
      BigDecimal liquidationBufferPercentage,
      CoinbaseMarginWindowMeasure intradayMarginWindowMeasure,
      CoinbaseMarginWindowMeasure overnightMarginWindowMeasure,
      List<CoinbaseFuturesPosition> expiringFutures) {
    this.balanceSummary =
        new CoinbaseFuturesBalanceSummary(
            usd(futuresBuyingPower),
            usd(totalUsdBalance),
            usd(cbiUsdBalance),
            usd(cfmUsdBalance),
            usd(totalOpenOrdersHoldAmount),
            usd(unrealizedPnl),
            usd(dailyRealizedPnl),
            usd(initialMargin),
            usd(availableMargin),
            usd(liquidationThreshold),
            usd(liquidationBufferAmount),
            liquidationBufferPercentage == null
                ? null
                : liquidationBufferPercentage.toPlainString(),
            intradayMarginWindowMeasure,
            overnightMarginWindowMeasure,
            null,
            null);
    this.expiringFutures =
        expiringFutures == null
            ? Collections.emptyList()
            : Collections.unmodifiableList(expiringFutures);
  }

  /** Returns the current wrapped CFM balance and margin measures. */
  public CoinbaseFuturesBalanceSummary getBalanceSummary() {
    return balanceSummary;
  }

  /** @deprecated use {@link #getBalanceSummary()} and retain its currency */
  @Deprecated
  public BigDecimal getFuturesBuyingPower() {
    return value(balanceSummary == null ? null : balanceSummary.getFuturesBuyingPower());
  }

  /** @deprecated use {@link #getBalanceSummary()} and retain its currency */
  @Deprecated
  public BigDecimal getTotalUsdBalance() {
    return value(balanceSummary == null ? null : balanceSummary.getTotalUsdBalance());
  }

  /** @deprecated use {@link #getBalanceSummary()} and retain its currency */
  @Deprecated
  public BigDecimal getCbiUsdBalance() {
    return value(balanceSummary == null ? null : balanceSummary.getCbiUsdBalance());
  }

  /** @deprecated use {@link #getBalanceSummary()} and retain its currency */
  @Deprecated
  public BigDecimal getCfmUsdBalance() {
    return value(balanceSummary == null ? null : balanceSummary.getCfmUsdBalance());
  }

  /** @deprecated use {@link #getBalanceSummary()} and retain its currency */
  @Deprecated
  public BigDecimal getTotalOpenOrdersHoldAmount() {
    return value(balanceSummary == null ? null : balanceSummary.getTotalOpenOrdersHoldAmount());
  }

  /** @deprecated use {@link #getBalanceSummary()} and retain its currency */
  @Deprecated
  public BigDecimal getUnrealizedPnl() {
    return value(balanceSummary == null ? null : balanceSummary.getUnrealizedPnl());
  }

  /** @deprecated use {@link #getBalanceSummary()} and retain its currency */
  @Deprecated
  public BigDecimal getDailyRealizedPnl() {
    return value(balanceSummary == null ? null : balanceSummary.getDailyRealizedPnl());
  }

  /** @deprecated use {@link #getBalanceSummary()} and retain its currency */
  @Deprecated
  public BigDecimal getInitialMargin() {
    return value(balanceSummary == null ? null : balanceSummary.getInitialMargin());
  }

  /** @deprecated use {@link #getBalanceSummary()} and retain its currency */
  @Deprecated
  public BigDecimal getAvailableMargin() {
    return value(balanceSummary == null ? null : balanceSummary.getAvailableMargin());
  }

  /** @deprecated use {@link #getBalanceSummary()} and retain its currency */
  @Deprecated
  public BigDecimal getLiquidationThreshold() {
    return value(balanceSummary == null ? null : balanceSummary.getLiquidationThreshold());
  }

  /** @deprecated use {@link #getBalanceSummary()} and retain its currency */
  @Deprecated
  public BigDecimal getLiquidationBufferAmount() {
    return value(balanceSummary == null ? null : balanceSummary.getLiquidationBufferAmount());
  }

  /** @deprecated use {@link #getBalanceSummary()} */
  @Deprecated
  public BigDecimal getLiquidationBufferPercentage() {
    String percentage =
        balanceSummary == null ? null : balanceSummary.getLiquidationBufferPercentage();
    return percentage == null ? null : new BigDecimal(percentage);
  }

  /** @deprecated use {@link #getBalanceSummary()} */
  @Deprecated
  public CoinbaseMarginWindowMeasure getIntradayMarginWindowMeasure() {
    return balanceSummary == null ? null : balanceSummary.getIntradayMarginWindowMeasure();
  }

  /** @deprecated use {@link #getBalanceSummary()} */
  @Deprecated
  public CoinbaseMarginWindowMeasure getOvernightMarginWindowMeasure() {
    return balanceSummary == null ? null : balanceSummary.getOvernightMarginWindowMeasure();
  }

  /**
   * Returns legacy expiring-futures data supplied to the flat constructor.
   *
   * @deprecated the current balance-summary endpoint no longer contains positions
   */
  @Deprecated
  public List<CoinbaseFuturesPosition> getExpiringFutures() {
    return expiringFutures;
  }

  private static CoinbaseAmount usd(BigDecimal value) {
    return value == null ? null : new CoinbaseAmount("USD", value);
  }

  private static BigDecimal value(CoinbaseAmount amount) {
    return amount == null ? null : amount.getValue();
  }

  @Override
  public String toString() {
    return "CoinbaseFuturesBalanceSummaryResponse [balanceSummary=" + balanceSummary + "]";
  }
}
