package org.knowm.xchange.coinsph.dto.account;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.ToString;

/**
 * Represents a fiat channel from Coins.ph API Based on GET /openapi/fiat/v1/support-channel
 * endpoint
 */
@Getter
@ToString
public class CoinsphFiatChannel {

  private final String channelName;
  private final String channelSubject;
  private final int status; // 1: available, 0: unavailable
  private final String currency;

  public CoinsphFiatChannel(
      @JsonProperty("channelName") String channelName,
      @JsonProperty("channelSubject") String channelSubject,
      @JsonProperty("status") int status,
      @JsonProperty("currency") String currency) {
    this.channelName = channelName;
    this.channelSubject = channelSubject;
    this.status = status;
    this.currency = currency;
  }
}
