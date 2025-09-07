package info.bitrich.xchangestream.binance.dto.trade;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class BinanceWebsocketLoginPayload {
private String apiKey;
  private long timestamp;

}
