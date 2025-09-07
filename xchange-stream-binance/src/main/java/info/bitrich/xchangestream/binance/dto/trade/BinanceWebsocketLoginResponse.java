package info.bitrich.xchangestream.binance.dto.trade;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BinanceWebsocketLoginResponse {

  private String apiKey;
  private long authorizedSince;
  private long connectedSince;
  private boolean returnRateLimits;
  private long serverTime;
}
