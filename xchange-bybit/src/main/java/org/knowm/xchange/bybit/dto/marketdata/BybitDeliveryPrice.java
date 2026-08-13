package org.knowm.xchange.bybit.dto.marketdata;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * One option/linear/inverse delivery-price record from {@code /v5/market/delivery-price}.
 *
 * <p>All numeric fields are preserved as wire strings to avoid precision loss.
 */
@Builder
@Jacksonized
@Value
public class BybitDeliveryPrice {

  @JsonProperty("symbol")
  String symbol;

  @JsonProperty("deliveryPrice")
  String deliveryPrice;

  @JsonProperty("deliveryTime")
  String deliveryTime;
}
