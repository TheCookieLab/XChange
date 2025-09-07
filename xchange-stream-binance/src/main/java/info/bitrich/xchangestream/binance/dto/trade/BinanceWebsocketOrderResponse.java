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
public class BinanceWebsocketOrderResponse<T> {
  private String id;
  private int status;
  private T result;
  private BinanceError error;

  @Getter
  @AllArgsConstructor
  @NoArgsConstructor
  @Builder
  public static class BinanceError {
    private int code;
    private String msg;
  }
}
