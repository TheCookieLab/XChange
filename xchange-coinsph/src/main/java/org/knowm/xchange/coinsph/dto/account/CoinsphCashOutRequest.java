package org.knowm.xchange.coinsph.dto.account;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.jackson.Jacksonized;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Represents a cash out request for Coins.ph API Based on POST /openapi/fiat/v1/cash-out endpoint
 */
@Getter
@ToString
@Builder
@Jacksonized
public class CoinsphCashOutRequest {

  @JsonProperty("amount")
  private final BigDecimal amount;

  @JsonProperty("internalOrderId")
  private final String internalOrderId;

  @JsonProperty("currency")
  private final String currency;

  @JsonProperty("channelName")
  private final String channelName;

  @JsonProperty("channelSubject")
  private final String channelSubject;

  @JsonProperty("extendInfo")
  private final Map<String, Object> extendInfo;
}
