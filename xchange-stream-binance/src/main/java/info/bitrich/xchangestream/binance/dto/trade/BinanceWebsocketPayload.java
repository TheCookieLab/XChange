package info.bitrich.xchangestream.binance.dto.trade;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BinanceWebsocketPayload<T> {

  private String id;
  private String method;
  private T params;

}
