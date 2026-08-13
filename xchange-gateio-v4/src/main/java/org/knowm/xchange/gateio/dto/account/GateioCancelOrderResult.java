package org.knowm.xchange.gateio.dto.account;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.gateio.config.converter.StringToCurrencyPairConverter;

/**
 * One element of a cancellation response (POST /spot/cancel_batch_orders or DELETE /spot/orders).
 *
 * <p>Like placements, cancellations succeed partially; {@link #getSucceeded()} marks per-order
 * outcome and {@link #getLabel()}/{@link #getMessage()} carry failure classification. The element
 * is flat: order fields (id, text, currency_pair, account) sit next to the outcome fields.
 */
@Data
@Builder
@Jacksonized
public class GateioCancelOrderResult {

  @JsonProperty("currency_pair")
  @JsonDeserialize(converter = StringToCurrencyPairConverter.class)
  CurrencyPair currencyPair;

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

  @JsonProperty("account")
  String account;
}
