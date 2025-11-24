package info.bitrich.xchangestream.deribit.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "messageType",
    visible = true,
    defaultImpl = DeribitWsNotification.class)
@JsonSubTypes({
    @Type(value = DeribitEventNotification.class, name = "event"),
    @Type(value = DeribitTickerNotification.class, name = "ticker"),
})
@Data
@SuperBuilder(toBuilder = true)
@Jacksonized
public class DeribitWsNotification<T> {

  @JsonProperty("method")
  String method;

  @JsonProperty("params")
  Params<T> params;

  @Data
  @Builder
  @Jacksonized
  public static class Params<T> {

    @JsonProperty("channel")
    String channel;

    @JsonProperty("data")
    T data;
  }
}
