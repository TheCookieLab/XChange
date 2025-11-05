package org.knowm.xchange.coinbase.v3.dto.futures;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import lombok.Getter;

/**
 * Response containing futures balance summary for CFM (Coinbase Financial Markets) futures trading.
 *
 * @see <a href="https://docs.cdp.coinbase.com/advanced-trade/reference/retailbrokerageapi_getfcmbalancesummary">Get Futures Balance Summary</a>
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class CoinbaseFuturesBalanceSummaryResponse {

  private final BigDecimal futuresBuyingPower;
  private final BigDecimal totalUsdBalance;
  private final BigDecimal cbiUsdBalance;
  private final BigDecimal cfmUsdBalance;
  private final BigDecimal totalOpenOrdersHoldAmount;
  private final BigDecimal unrealizedPnl;
  private final BigDecimal dailyRealizedPnl;
  private final BigDecimal initialMargin;
  private final BigDecimal availableMargin;
  private final BigDecimal liquidationThreshold;
  private final BigDecimal liquidationBufferAmount;
  private final BigDecimal liquidationBufferPercentage;
  private final CoinbaseMarginWindowMeasure intradayMarginWindowMeasure;
  private final CoinbaseMarginWindowMeasure overnightMarginWindowMeasure;
  private final List<CoinbaseFuturesPosition> expiringFutures;

  @JsonCreator
  public CoinbaseFuturesBalanceSummaryResponse(
      @JsonProperty("futures_buying_power") BigDecimal futuresBuyingPower,
      @JsonProperty("total_usd_balance") BigDecimal totalUsdBalance,
      @JsonProperty("cbi_usd_balance") BigDecimal cbiUsdBalance,
      @JsonProperty("cfm_usd_balance") BigDecimal cfmUsdBalance,
      @JsonProperty("total_open_orders_hold_amount") BigDecimal totalOpenOrdersHoldAmount,
      @JsonProperty("unrealized_pnl") BigDecimal unrealizedPnl,
      @JsonProperty("daily_realized_pnl") BigDecimal dailyRealizedPnl,
      @JsonProperty("initial_margin") BigDecimal initialMargin,
      @JsonProperty("available_margin") BigDecimal availableMargin,
      @JsonProperty("liquidation_threshold") BigDecimal liquidationThreshold,
      @JsonProperty("liquidation_buffer_amount") BigDecimal liquidationBufferAmount,
      @JsonProperty("liquidation_buffer_percentage") BigDecimal liquidationBufferPercentage,
      @JsonProperty("intraday_margin_window_measure") CoinbaseMarginWindowMeasure intradayMarginWindowMeasure,
      @JsonProperty("overnight_margin_window_measure") CoinbaseMarginWindowMeasure overnightMarginWindowMeasure,
      @JsonProperty("expiring_futures") List<CoinbaseFuturesPosition> expiringFutures) {
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
    this.expiringFutures = expiringFutures == null ? Collections.emptyList() : Collections.unmodifiableList(expiringFutures);
  }

  @Override
  public String toString() {
    return "CoinbaseFuturesBalanceSummaryResponse [totalUsdBalance=" + totalUsdBalance + ", futuresBuyingPower=" + futuresBuyingPower + "]";
  }
}

