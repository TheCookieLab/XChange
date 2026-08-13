package org.knowm.xchange.gateio.dto.marketdata;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.gateio.config.converter.StringToCurrencyPairConverter;

/**
 * A public spot trade (GET /spot/trades).
 *
 * <p>Wire contract pinned in {@code protocol/gate-api-v4-2026-08-13.json}; amounts and prices are
 * exact provider decimals.
 */
@Data
@Builder
@Jacksonized
public class GateioTrade {

  @JsonProperty("id")
  String id;

  @JsonProperty("create_time")
  Long createTime;

  @JsonProperty("create_time_ms")
  BigDecimal createTimeMs;

  @JsonProperty("currency_pair")
  @JsonDeserialize(converter = StringToCurrencyPairConverter.class)
  CurrencyPair currencyPair;

  @JsonProperty("side")
  String side;

  @JsonProperty("role")
  String role;

  @JsonProperty("amount")
  BigDecimal amount;

  @JsonProperty("price")
  BigDecimal price;

  @JsonProperty("order_id")
  String orderId;

  @JsonProperty("fee")
  BigDecimal fee;

  @JsonProperty("fee_currency")
  String feeCurrency;

  @JsonProperty("point_fee")
  BigDecimal pointFee;

  @JsonProperty("gt_fee")
  BigDecimal gtFee;

  @JsonProperty("amend_text")
  String amendText;

  @JsonProperty("sequence_id")
  String sequenceId;

  @JsonProperty("text")
  String text;

  @JsonProperty("deal")
  String deal;
}
