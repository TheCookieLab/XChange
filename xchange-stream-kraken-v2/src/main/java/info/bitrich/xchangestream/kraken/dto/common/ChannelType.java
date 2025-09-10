package info.bitrich.xchangestream.kraken.dto.common;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ChannelType {
  TICKER("ticker");

  @JsonValue
  private final String value;

  public String toString() {
    return value;
  }
}
