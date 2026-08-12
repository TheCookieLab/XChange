package info.bitrich.xchangestream.bitget.uta.v3.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;

/**
 * Bitget UTA v3 WebSocket acknowledgement frame ({@code {event, arg, code, msg, connId}}).
 *
 * <p>The server answers {@code subscribe}/{@code unsubscribe}/{@code login} requests with an {@code
 * event} frame carrying a {@code code} ({@code "0"} success) and a connection id; failures arrive
 * as {@code event: "error"}. Distinguished from push envelopes by the {@code event} property.
 *
 * @since 5.1.0
 */
@Data
@SuperBuilder(toBuilder = true)
@Jacksonized
public class BitgetUtaV3EventNotification extends BitgetUtaV3WsNotification {

  @JsonProperty("event")
  private Event event;

  /** Provider result code; {@code "0"} means success. */
  @JsonProperty("code")
  private String code;

  @JsonProperty("msg")
  private String message;

  /** Connection identifier echoed by the server. */
  @JsonProperty("connId")
  private String connectionId;

  public enum Event {
    @JsonProperty("subscribe")
    SUBSCRIBE,
    @JsonProperty("unsubscribe")
    UNSUBSCRIBE,
    @JsonProperty("login")
    LOGIN,
    @JsonProperty("error")
    ERROR
  }
}
