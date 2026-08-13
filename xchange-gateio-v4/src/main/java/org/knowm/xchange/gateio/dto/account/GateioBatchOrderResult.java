package org.knowm.xchange.gateio.dto.account;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.gateio.config.converter.StringToCurrencyPairConverter;

/**
 * One element of a batch order placement (POST /spot/batch_orders).
 *
 * <p>Batch placements succeed partially: each element carries its own outcome. {@link #getSucceeded()}
 * distinguishes per-order success, {@link #getLabel()} classifies failure reasons, and {@link
 * #getMessage()} holds the provider message; {@link #getOrder()} is present on success. Consumers
 * must not treat a failed element as placed (no blind retry).
 */
@Data
@Builder
@Jacksonized
public class GateioBatchOrderResult {

  @JsonProperty("id")
  String id;

  @JsonProperty("text")
  String text;

  @JsonProperty("succeeded")
  Boolean succeeded;

  @JsonProperty("label")
  String label;

  @JsonProperty("message")
  String message;

  @JsonProperty("order")
  GateioOrder order;

  @JsonProperty("currency_pair")
  @JsonDeserialize(converter = StringToCurrencyPairConverter.class)
  CurrencyPair currencyPair;
}
