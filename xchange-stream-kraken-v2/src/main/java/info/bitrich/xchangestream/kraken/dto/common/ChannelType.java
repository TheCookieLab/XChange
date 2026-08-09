package info.bitrich.xchangestream.kraken.dto.common;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ChannelType {
  TICKER("ticker"),
  TRADE("trade"),
  BOOK("book"),
  OHLC("ohlc"),
  STATUS("status"),

  BALANCES("balances"),
  USER_TRADES("executions"),
  ORDERS("orders");

  @JsonValue private final String value;

  public String toString() {
    return value;
  }
}
