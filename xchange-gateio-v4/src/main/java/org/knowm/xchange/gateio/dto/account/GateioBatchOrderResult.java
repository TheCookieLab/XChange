package org.knowm.xchange.gateio.dto.account;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.gateio.config.converter.StringToCurrencyPairConverter;

/**
 * One element of a batch order placement (POST /spot/batch_orders) or modification (POST
 * /spot/amend_batch_orders).
 *
 * <p>Batch operations succeed partially: each element carries its own outcome. Gate returns each
 * element as a flat object with the order fields at the response root next to {@link
 * #getSucceeded()}, {@link #getLabel()} and {@link #getMessage()}; there is no nested order
 * wrapper. {@link #getId()} holds the order id for placements, {@link #getOrderId()} the referenced
 * order id for amendments. Consumers must not treat a failed element as placed (no blind retry).
 */
@Data
@Builder
@Jacksonized
public class GateioBatchOrderResult {

  @JsonProperty("id")
  String id;

  @JsonProperty("order_id")
  String orderId;

  @JsonProperty("text")
  String text;

  @JsonProperty("amend_text")
  String amendText;

  @JsonProperty("succeeded")
  Boolean succeeded;

  @JsonProperty("label")
  String label;

  @JsonProperty("message")
  String message;

  @JsonProperty("currency_pair")
  @JsonDeserialize(converter = StringToCurrencyPairConverter.class)
  CurrencyPair currencyPair;

  @JsonProperty("status")
  String status;
}
