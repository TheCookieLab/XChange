package org.knowm.xchange.coinbasederivatives.dto.marketdata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

/** Provider instrument metadata. All numeric fields remain decimal values. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CoinbaseDerivativesInstrument(
    @JsonProperty("instrument_name") String instrumentName,
    String kind,
    @JsonProperty("base_currency") String baseCurrency,
    @JsonProperty("counter_currency") String counterCurrency,
    @JsonProperty("settlement_currency") String settlementCurrency,
    @JsonProperty("is_active") boolean active,
    @JsonProperty("tick_size") BigDecimal tickSize,
    @JsonProperty("min_trade_amount") BigDecimal minimumTradeAmount,
    @JsonProperty("contract_size") BigDecimal contractSize,
    @JsonProperty("maker_commission") BigDecimal makerCommission,
    @JsonProperty("taker_commission") BigDecimal takerCommission) {}
