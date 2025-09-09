package dto.trade;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.knowm.xchange.bybit.dto.trade.BybitPlaceOrderPayload;

@AllArgsConstructor
@Getter
public class BybitOrderMessage {
  private final String reqId;
  private final Header header;
  private final String op;
  private final List<BybitPlaceOrderPayload> args;

  @Getter
  @AllArgsConstructor
  public static class Header {
    @JsonProperty("X-BAPI-TIMESTAMP")
    private String X_BAPI_TIMESTAMP;
    @JsonProperty("X-BAPI-RECV-WINDOW")
    private String X_BAPI_RECV_WINDOW;
    @JsonProperty("Referer")
    private String referer;
  }
}
