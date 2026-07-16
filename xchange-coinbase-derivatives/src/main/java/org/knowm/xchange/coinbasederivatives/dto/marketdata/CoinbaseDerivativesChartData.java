package org.knowm.xchange.coinbasederivatives.dto.marketdata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.List;

/** TradingView-compatible candle arrays from the gateway. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CoinbaseDerivativesChartData(
    List<Long> ticks,
    List<BigDecimal> open,
    List<BigDecimal> high,
    List<BigDecimal> low,
    List<BigDecimal> close,
    List<BigDecimal> volume,
    String status) {}
