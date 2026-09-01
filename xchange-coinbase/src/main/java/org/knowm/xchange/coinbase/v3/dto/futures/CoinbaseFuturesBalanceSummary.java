package org.knowm.xchange.coinbase.v3.dto.futures;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import org.knowm.xchange.coinbase.v3.dto.accounts.CoinbaseAmount;

/**
 * Wrapped {@code balance_summary} returned by the Coinbase CFM balance endpoint.
 *
 * <p>Coinbase returns every monetary value as an amount object containing both value and currency.
 * The currency is intentionally retained; callers must not infer USD or substitute a spot wallet.
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class CoinbaseFuturesBalanceSummary {

  private final CoinbaseAmount futuresBuyingPower;
  private final CoinbaseAmount totalUsdBalance;
  private final CoinbaseAmount cbiUsdBalance;
  private final CoinbaseAmount cfmUsdBalance;
  private final CoinbaseAmount totalOpenOrdersHoldAmount;
  private final CoinbaseAmount unrealizedPnl;
  private final CoinbaseAmount dailyRealizedPnl;
  private final CoinbaseAmount initialMargin;
  private final CoinbaseAmount availableMargin;
  private final CoinbaseAmount liquidationThreshold;
  private final CoinbaseAmount liquidationBufferAmount;
  private final String liquidationBufferPercentage;
  private final CoinbaseMarginWindowMeasure intradayMarginWindowMeasure;
  private final CoinbaseMarginWindowMeasure overnightMarginWindowMeasure;
  private final CoinbaseAmount totalPendingTransfersAmount;
  private final CoinbaseAmount fundingPnl;

  /**
   * Creates the current wrapped CFM balance summary.
   *
   * @param futuresBuyingPower available futures buying power
   * @param totalUsdBalance total USD balance
   * @param cbiUsdBalance Coinbase Inc. USD balance
   * @param cfmUsdBalance Coinbase Financial Markets USD balance
   * @param totalOpenOrdersHoldAmount aggregate open-order hold
   * @param unrealizedPnl unrealized profit or loss
   * @param dailyRealizedPnl current-day realized profit or loss
   * @param initialMargin initial margin requirement
   * @param availableMargin available margin
   * @param liquidationThreshold liquidation threshold
   * @param liquidationBufferAmount liquidation buffer amount
   * @param liquidationBufferPercentage liquidation buffer percentage
   * @param intradayMarginWindowMeasure intraday margin-window measures
   * @param overnightMarginWindowMeasure overnight margin-window measures
   * @param totalPendingTransfersAmount pending transfer amount
   * @param fundingPnl funding profit or loss
   */
  @JsonCreator
  public CoinbaseFuturesBalanceSummary(
      @JsonProperty("futures_buying_power") CoinbaseAmount futuresBuyingPower,
      @JsonProperty("total_usd_balance") CoinbaseAmount totalUsdBalance,
      @JsonProperty("cbi_usd_balance") CoinbaseAmount cbiUsdBalance,
      @JsonProperty("cfm_usd_balance") CoinbaseAmount cfmUsdBalance,
      @JsonProperty("total_open_orders_hold_amount") CoinbaseAmount totalOpenOrdersHoldAmount,
      @JsonProperty("unrealized_pnl") CoinbaseAmount unrealizedPnl,
      @JsonProperty("daily_realized_pnl") CoinbaseAmount dailyRealizedPnl,
      @JsonProperty("initial_margin") CoinbaseAmount initialMargin,
      @JsonProperty("available_margin") CoinbaseAmount availableMargin,
      @JsonProperty("liquidation_threshold") CoinbaseAmount liquidationThreshold,
      @JsonProperty("liquidation_buffer_amount") CoinbaseAmount liquidationBufferAmount,
      @JsonProperty("liquidation_buffer_percentage") String liquidationBufferPercentage,
      @JsonProperty("intraday_margin_window_measure") CoinbaseMarginWindowMeasure intradayMarginWindowMeasure,
      @JsonProperty("overnight_margin_window_measure") CoinbaseMarginWindowMeasure overnightMarginWindowMeasure,
      @JsonProperty("total_pending_transfers_amount") CoinbaseAmount totalPendingTransfersAmount,
      @JsonProperty("funding_pnl") CoinbaseAmount fundingPnl) {
    this.futuresBuyingPower = futuresBuyingPower;
    this.totalUsdBalance = totalUsdBalance;
    this.cbiUsdBalance = cbiUsdBalance;
    this.cfmUsdBalance = cfmUsdBalance;
    this.totalOpenOrdersHoldAmount = totalOpenOrdersHoldAmount;
    this.unrealizedPnl = unrealizedPnl;
    this.dailyRealizedPnl = dailyRealizedPnl;
    this.initialMargin = initialMargin;
    this.availableMargin = availableMargin;
    this.liquidationThreshold = liquidationThreshold;
    this.liquidationBufferAmount = liquidationBufferAmount;
    this.liquidationBufferPercentage = liquidationBufferPercentage;
    this.intradayMarginWindowMeasure = intradayMarginWindowMeasure;
    this.overnightMarginWindowMeasure = overnightMarginWindowMeasure;
    this.totalPendingTransfersAmount = totalPendingTransfersAmount;
    this.fundingPnl = fundingPnl;
  }
}
