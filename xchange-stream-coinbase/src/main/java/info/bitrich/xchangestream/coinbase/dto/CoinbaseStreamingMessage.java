package info.bitrich.xchangestream.coinbase.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CoinbaseStreamingMessage {

  private final String channel;
  private final List<CoinbaseStreamingEvent> events;

  @JsonCreator
  public CoinbaseStreamingMessage(
      @JsonProperty("channel") String channel,
      @JsonProperty("events") List<CoinbaseStreamingEvent> events) {
    this.channel = channel;
    this.events = events == null ? Collections.emptyList() : Collections.unmodifiableList(events);
  }

  public String getChannel() {
    return channel;
  }

  public List<CoinbaseStreamingEvent> getEvents() {
    return events;
  }
}
