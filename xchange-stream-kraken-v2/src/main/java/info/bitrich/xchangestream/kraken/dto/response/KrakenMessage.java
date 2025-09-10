package info.bitrich.xchangestream.kraken.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.As;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import lombok.Data;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;

@JsonTypeInfo(
    use = Id.NAME,
    include = As.EXISTING_PROPERTY,
    property = "channel",
    visible = true,
    defaultImpl = KrakenControlMessage.class)
@JsonSubTypes({
    @Type(value = KrakenHeartbeatMessage.class, name = "heartbeat"),
    @Type(value = KrakenStatusMessage.class, name = "status"),
    @Type(value = KrakenTickerMessage.class, name = "ticker"),
    @Type(value = KrakenTradeMessage.class, name = "trade"),
})
@Data
@SuperBuilder(toBuilder = true)
@Jacksonized
public class KrakenMessage {

  @JsonProperty("channel")
  String channel;


  public String getChannelId() {
    return channel;
  }

}
