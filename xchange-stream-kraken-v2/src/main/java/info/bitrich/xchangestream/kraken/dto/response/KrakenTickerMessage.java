package info.bitrich.xchangestream.kraken.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import info.bitrich.xchangestream.kraken.config.converters.StringToCurrencyPairConverter;
import info.bitrich.xchangestream.kraken.dto.common.ChannelMessageType;
import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;
import org.knowm.xchange.currency.CurrencyPair;

@Data
@SuperBuilder(toBuilder = true)
@Jacksonized
public class KrakenTickerMessage extends KrakenMessage {

  @JsonProperty("type")
  private ChannelMessageType channelMessageType;

  @JsonProperty("data")
  private List<Payload> data;

  @Override
  public String getChannelId() {
    return super.getChannelId() + "_" + data.get(0).getCurrencyPair();
  }

  @Data
  @Builder
  @Jacksonized
  public static class Payload {

    @JsonProperty("symbol")
    @JsonDeserialize(converter = StringToCurrencyPairConverter.class)
    private CurrencyPair currencyPair;

    @JsonProperty("ask")
    private BigDecimal bestAskPrice;

    @JsonProperty("ask_qty")
    private BigDecimal bestAskSize;

    @JsonProperty("bid")
    private BigDecimal bestBidPrice;

    @JsonProperty("bid_qty")
    private BigDecimal bestBidSize;

    @JsonProperty("last")
    private BigDecimal lastPrice;

    @JsonProperty("volume")
    private BigDecimal assetVolume24h;

    @JsonProperty("vwap")
    private BigDecimal vwap;

    @JsonProperty("low")
    private BigDecimal low24h;

    @JsonProperty("high")
    private BigDecimal high24h;

    @JsonProperty("change")
    private BigDecimal change24h;

    @JsonProperty("change_pct")
    private BigDecimal changePercentage24h;

  }

}
