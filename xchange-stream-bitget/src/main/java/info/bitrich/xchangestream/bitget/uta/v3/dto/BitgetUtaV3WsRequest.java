package info.bitrich.xchangestream.bitget.uta.v3.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import info.bitrich.xchangestream.bitget.dto.common.Operation;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;
import lombok.extern.jackson.Jacksonized;

/**
 * Bitget UTA v3 WebSocket request frame ({@code {"op": <operation>, "args": [channels]}}).
 *
 * <p>The {@link Operation} values ({@code subscribe}, {@code unsubscribe}, {@code login}) are
 * byte-identical between the classic v2 and UTA v3 protocols and are reused from the classic {@code
 * dto.common} package.
 *
 * @since 5.1.0
 */
@Data
@Builder
@Jacksonized
public class BitgetUtaV3WsRequest {

  @JsonProperty("op")
  private Operation operation;

  @Singular
  @JsonProperty("args")
  private List<BitgetUtaV3Channel> channels;
}
