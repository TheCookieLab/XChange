package org.knowm.xchange.coinbasederivatives.dto.trade;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** Provider placement response containing the accepted order and immediate fills. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CoinbaseDerivativesPlacementResponse(
    CoinbaseDerivativesOrder order, List<CoinbaseDerivativesUserTrade> trades) {}
