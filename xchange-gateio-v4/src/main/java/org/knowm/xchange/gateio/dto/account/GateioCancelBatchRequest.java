package org.knowm.xchange.gateio.dto.account;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.gateio.config.converter.CurrencyPairToStringConverter;

/** One element of the POST /spot/cancel_batch_orders body. */
@Data
@Builder
@Jacksonized
public class GateioCancelBatchRequest {

  @JsonProperty("currency_pair")
  @JsonSerialize(converter = CurrencyPairToStringConverter.class)
  CurrencyPair currencyPair;

  @JsonProperty("id")
  String orderId;

  @JsonProperty("text")
  String text;

  @JsonProperty("account")
  String account;
}
