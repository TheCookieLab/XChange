package info.bitrich.xchangestream.bitget.uta.v3.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import lombok.Data;
import lombok.Singular;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;

/**
 * Bitget UTA v3 WebSocket push envelope ({@code {action, arg, data, ts}}).
 *
 * <p>{@code data} is kept as raw {@link JsonNode}s; each consuming service converts the items to
 * its topic-specific DTO. Acknowledgements ({@code event} frames) parse as {@link
 * BitgetUtaV3EventNotification} instead and are routed by {@code
 * BitgetUtaV3StreamingService#messageHandler} before channel dispatch.
 *
 * @since 5.1.0
 */
@Data
@SuperBuilder(toBuilder = true)
@Jacksonized
public class BitgetUtaV3WsNotification {

  @JsonProperty("action")
  private BitgetUtaV3Action action;

  @JsonProperty("arg")
  private BitgetUtaV3Channel channel;

  @Singular
  @JsonProperty("data")
  private List<JsonNode> payloadItems;

  /** Push timestamp, epoch milliseconds. */
  @JsonProperty("ts")
  private Long timestamp;
}
