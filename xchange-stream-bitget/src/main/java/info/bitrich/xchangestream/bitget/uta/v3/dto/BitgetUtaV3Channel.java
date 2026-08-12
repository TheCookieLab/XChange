package info.bitrich.xchangestream.bitget.uta.v3.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

/**
 * Bitget UTA v3 WebSocket channel argument ({@code instType}, {@code topic}, {@code symbol}, plus
 * the topic-specific {@code interval} for klines).
 *
 * <p>The order-book depth is part of the topic itself ({@code books}, {@code books1}, {@code
 * books5}, {@code books50}) and is not a separate argument. Used both for subscription requests
 * ({@code {"op":"subscribe","args":[...]}}) and echoed inside push envelopes/acknowledgements as
 * {@code arg}.
 *
 * @since 5.1.0
 */
@Data
@Builder
@Jacksonized
public class BitgetUtaV3Channel {

  @JsonProperty("instType")
  private BitgetUtaV3InstType instType;

  @JsonProperty("topic")
  private String topic;

  @JsonProperty("symbol")
  private String symbol;

  /** Optional kline interval for the {@code kline} topic (e.g. {@code 1m}, {@code 1H}). */
  @JsonProperty("interval")
  private String interval;

  /**
   * Subscription identity: {@code instType_topic_symbol}.
   *
   * <p>Deliberately excludes {@code interval}: the provider may or may not echo it in push {@code
   * arg}s, so channel routing keys on the stable fields only. A second subscriber for the same
   * symbol shares the first subscription.
   */
  public String toSubscriptionId() {
    StringBuilder id = new StringBuilder(instType.getWireName()).append('_').append(topic);
    if (symbol != null && !symbol.isEmpty()) {
      id.append('_').append(symbol);
    }
    return id.toString();
  }
}
