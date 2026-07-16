package org.knowm.xchange.coinbasederivatives.dto.marketdata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

/** Public trade from the Coinbase derivatives gateway. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CoinbaseDerivativesTrade(
    @JsonProperty("trade_id") String tradeId,
    @JsonProperty("instrument_name") String instrumentName,
    String direction,
    BigDecimal amount,
    BigDecimal price,
    long timestamp) {}
