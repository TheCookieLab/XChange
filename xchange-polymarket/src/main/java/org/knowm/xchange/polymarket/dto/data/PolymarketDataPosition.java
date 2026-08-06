package org.knowm.xchange.polymarket.dto.data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

/** Public Data-API outcome-token position of a wallet. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PolymarketDataPosition(
    @JsonProperty("proxyWallet") String proxyWallet,
    @JsonProperty("asset") String asset,
    @JsonProperty("conditionId") String conditionId,
    @JsonProperty("size") BigDecimal size,
    @JsonProperty("avgPrice") BigDecimal avgPrice,
    @JsonProperty("curPrice") BigDecimal curPrice,
    @JsonProperty("currentValue") BigDecimal currentValue,
    @JsonProperty("outcome") String outcome,
    @JsonProperty("outcomeIndex") Integer outcomeIndex,
    @JsonProperty("oppositeAsset") String oppositeAsset,
    @JsonProperty("eventId") String eventId,
    @JsonProperty("title") String title,
    @JsonProperty("negativeRisk") Boolean negativeRisk,
    @JsonProperty("redeemable") Boolean redeemable) {}
