package org.knowm.xchange.gateio.dto.account;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.gateio.config.converter.CurrencyPairToStringConverter;

/** Countdown-cancel-all task body (POST /spot/countdown_cancel_all). */
@Data
@Builder
@Jacksonized
public class GateioCountdownCancelRequest {

  /** Timeout in seconds; required. */
  @JsonProperty("timeout")
  int timeout;

  /** Optional currency pair scope; when null the countdown applies to all pairs. */
  @JsonProperty("currency_pair")
  @JsonSerialize(converter = CurrencyPairToStringConverter.class)
  CurrencyPair currencyPair;
}
