package info.bitrich.xchangestream.bitget.uta.v3.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

/**
 * Bitget UTA v3 ticker push payload (wire keys {@code bid1Price}, {@code lastPrice}, ...).
 *
 * <p>Spot pushes carry the 24h fields only; futures pushes additionally carry the mark/index
 * pricing, funding rate and open-interest fields. There is no data-level timestamp: the push
 * envelope {@code ts} is the timestamp of the ticker.
 *
 * @since 5.1.0
 */
@Data
@Builder
@Jacksonized
public class BitgetUtaV3TickerData {

  @JsonProperty("bid1Price")
  private BigDecimal bidPrice;

  @JsonProperty("lowPrice24h")
  private BigDecimal lowPrice24h;

  @JsonProperty("ask1Size")
  private BigDecimal askSize;

  @JsonProperty("volume24h")
  private BigDecimal volume24h;

  @JsonProperty("price24hPcnt")
  private BigDecimal price24hPcnt;

  @JsonProperty("highPrice24h")
  private BigDecimal highPrice24h;

  @JsonProperty("turnover24h")
  private BigDecimal turnover24h;

  @JsonProperty("bid1Size")
  private BigDecimal bidSize;

  @JsonProperty("ask1Price")
  private BigDecimal askPrice;

  @JsonProperty("openPrice24h")
  private BigDecimal openPrice24h;

  @JsonProperty("lastPrice")
  private BigDecimal lastPrice;

  @JsonProperty("platformTurnover24h")
  private BigDecimal platformTurnover24h;

  // futures-only fields

  @JsonProperty("indexPrice")
  private BigDecimal indexPrice;

  @JsonProperty("markPrice")
  private BigDecimal markPrice;

  @JsonProperty("fundingRate")
  private BigDecimal fundingRate;

  @JsonProperty("nextFundingTime")
  private Long nextFundingTime;

  @JsonProperty("openInterest")
  private BigDecimal openInterest;

  @JsonProperty("deliveryTime")
  private Long deliveryTime;

  @JsonProperty("deliveryStartTime")
  private Long deliveryStartTime;

  @JsonProperty("deliveryStatus")
  private String deliveryStatus;
}
