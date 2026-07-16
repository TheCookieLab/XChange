package org.knowm.xchange.coinbasederivatives.dto.trade;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

/** Provider order. Labels are descriptive correlation data, not idempotency keys. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CoinbaseDerivativesOrder(
    @JsonProperty("order_id") String orderId,
    @JsonProperty("primary_order_id") String primaryOrderId,
    @JsonProperty("oto_order_ids") List<String> otoOrderIds,
    @JsonProperty("instrument_name") String instrumentName,
    String direction,
    @JsonProperty("order_type") String orderType,
    BigDecimal amount,
    BigDecimal contracts,
    BigDecimal price,
    @JsonProperty("trigger_price") BigDecimal triggerPrice,
    @JsonProperty("reduce_only") Boolean reduceOnly,
    String label,
    @JsonProperty("order_state") String orderState,
    @JsonProperty("creation_timestamp") Long creationTimestamp,
    @JsonProperty("last_update_timestamp") Long lastUpdateTimestamp,
    @JsonProperty("filled_amount") BigDecimal filledAmount,
    @JsonProperty("average_price") BigDecimal averagePrice,
    BigDecimal commission) {}
