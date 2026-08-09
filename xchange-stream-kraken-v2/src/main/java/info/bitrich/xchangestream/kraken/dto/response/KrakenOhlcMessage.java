package info.bitrich.xchangestream.kraken.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;

/** OHLC candle message on the {@code ohlc} channel. */
@Data
@SuperBuilder(toBuilder = true)
@Jacksonized
public class KrakenOhlcMessage extends KrakenDataMessage<KrakenOhlcMessage.KrakenOhlcLevel> {

  @Override
  public String getChannelId() {
    KrakenOhlcLevel payload = getPayload();
    if (payload == null || payload.getSymbol() == null) {
      return super.getChannelId();
    }
    return super.getChannelId() + "_" + payload.getSymbol();
  }

  /** One candle. */
  @Data
  @Builder
  @Jacksonized
  public static class KrakenOhlcLevel {

    @JsonProperty("symbol")
    private String symbol;

    @JsonProperty("interval")
    private String interval;

    @JsonProperty("open")
    private BigDecimal open;

    @JsonProperty("high")
    private BigDecimal high;

    @JsonProperty("low")
    private BigDecimal low;

    @JsonProperty("close")
    private BigDecimal close;

    @JsonProperty("volume")
    private BigDecimal volume;

    @JsonProperty("timestamp")
    private String timestamp;
  }
}
