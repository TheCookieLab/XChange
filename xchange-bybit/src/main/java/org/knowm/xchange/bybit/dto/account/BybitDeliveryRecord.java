package org.knowm.xchange.bybit.dto.account;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * One settlement/delivery record from {@code /v5/asset/delivery-record} (option and linear
 * settlement identity).
 *
 * <p>All numeric fields are preserved as wire strings to avoid precision loss.
 */
@Builder
@Jacksonized
@Value
public class BybitDeliveryRecord {

  @JsonProperty("deliveryTime")
  String deliveryTime;

  @JsonProperty("symbol")
  String symbol;

  @JsonProperty("side")
  String side;

  @JsonProperty("position")
  String position;

  @JsonProperty("deliveryPrice")
  String deliveryPrice;

  @JsonProperty("strike")
  String strike;

  @JsonProperty("fee")
  String fee;

  @JsonProperty("deliveryRpl")
  String deliveryRpl;
}
