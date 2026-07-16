package org.knowm.xchange.coinbasederivatives.dto.account;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

/** Open provider position including signed size and risk data. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CoinbaseDerivativesPosition(
    @JsonProperty("instrument_name") String instrumentName,
    String direction,
    BigDecimal size,
    @JsonProperty("size_currency") BigDecimal sizeCurrency,
    @JsonProperty("average_price") BigDecimal averagePrice,
    @JsonProperty("mark_price") BigDecimal markPrice,
    @JsonProperty("estimated_liquidation_price") BigDecimal estimatedLiquidationPrice,
    @JsonProperty("floating_profit_loss") BigDecimal unrealizedPnl,
    @JsonProperty("realized_profit_loss") BigDecimal realizedPnl,
    @JsonProperty("total_profit_loss") BigDecimal totalPnl,
    @JsonProperty("interest_value") BigDecimal funding,
    @JsonProperty("initial_margin") BigDecimal initialMargin,
    @JsonProperty("maintenance_margin") BigDecimal maintenanceMargin,
    @JsonProperty("margin_model") String marginModel,
    @JsonProperty("creation_timestamp") Long creationTimestamp) {}
