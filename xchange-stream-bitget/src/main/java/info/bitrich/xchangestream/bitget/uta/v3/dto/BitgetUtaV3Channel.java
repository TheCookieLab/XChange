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
   * Subscription identity: {@code instType_topic_symbol}, plus {@code _interval} for klines.
   *
   * <p>The kline interval is part of the identity: the provider echoes the full channel argument
   * (interval included) in every push {@code arg} and routing keys on this id, so excluding it
   * would collapse every interval on one symbol onto a single subscription — only the first
   * subscriber would receive pushes and any subscriber's dispose would tear the channel down for
   * the others.
   */
  public String toSubscriptionId() {
    StringBuilder id = new StringBuilder(instType.getWireName()).append('_').append(topic);
    if (symbol != null && !symbol.isEmpty()) {
      id.append('_').append(symbol);
    }
    if (interval != null && !interval.isEmpty()) {
      id.append('_').append(interval);
    }
    return id.toString();
  }
}
