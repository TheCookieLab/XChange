package org.knowm.xchange.coinbasederivatives.dto.trade;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

/** Private fill with stable exchange order and trade identifiers. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CoinbaseDerivativesUserTrade(
    @JsonProperty("trade_id") String tradeId,
    @JsonProperty("order_id") String orderId,
    @JsonProperty("instrument_name") String instrumentName,
    String direction,
    BigDecimal amount,
    BigDecimal contracts,
    BigDecimal price,
    BigDecimal fee,
    @JsonProperty("fee_currency") String feeCurrency,
    long timestamp,
    String label) {}
