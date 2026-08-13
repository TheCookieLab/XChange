package org.knowm.xchange.gateio.dto.account;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.gateio.config.converter.CurrencyPairToStringConverter;

/**
 * Amend payload for PATCH /spot/orders/{order_id}.
 *
 * <p>All fields optional; only provided fields are amended. Wire contract pinned in {@code
 * protocol/gate-api-v4-2026-08-13.json}.
 */
@Data
@Builder
@Jacksonized
public class GateioAmendOrderRequest {

  /** Price/amount trigger for a stop-profit order; optional. */
  @Data
  @Builder
  @Jacksonized
  public static class GateioStopTrigger {

    @JsonProperty("trigger_price")
    BigDecimal triggerPrice;

    @JsonProperty("order_price")
    BigDecimal orderPrice;
  }

  @JsonProperty("currency_pair")
  @JsonSerialize(converter = CurrencyPairToStringConverter.class)
  CurrencyPair currencyPair;

  @JsonProperty("account")
  String account;

  @JsonProperty("amount")
  BigDecimal amount;

  @JsonProperty("price")
  BigDecimal price;

  @JsonProperty("amend_text")
  String amendText;

  @JsonProperty("action_mode")
  String actionMode;

  @JsonProperty("stop_profit")
  GateioStopTrigger stopProfit;

  @JsonProperty("stop_loss")
  GateioStopTrigger stopLoss;
}
