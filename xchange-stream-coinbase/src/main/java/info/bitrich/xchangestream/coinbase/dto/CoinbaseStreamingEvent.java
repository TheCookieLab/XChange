package info.bitrich.xchangestream.coinbase.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CoinbaseStreamingEvent {

  private final String type;
  private final String productId;
  private final Long sequence;
  private final List<CoinbaseStreamingTicker> tickers;
  private final List<CoinbaseStreamingTrade> trades;
  private final List<CoinbaseStreamingCandle> candles;
  private final List<CoinbaseStreamingLevel2Update> updates;
  private final List<List<String>> bids;
  private final List<List<String>> asks;
  private final List<CoinbaseStreamingUserOrder> orders;
  private final CoinbaseFuturesBalanceSummary fcmBalanceSummary;

  @JsonCreator
  public CoinbaseStreamingEvent(
      @JsonProperty("type") String type,
      @JsonProperty("product_id") String productId,
      @JsonProperty("sequence") Long sequence,
      @JsonProperty("tickers") List<CoinbaseStreamingTicker> tickers,
      @JsonProperty("trades") List<CoinbaseStreamingTrade> trades,
      @JsonProperty("candles") List<CoinbaseStreamingCandle> candles,
      @JsonProperty("updates") List<CoinbaseStreamingLevel2Update> updates,
      @JsonProperty("bids") List<List<String>> bids,
      @JsonProperty("asks") List<List<String>> asks,
      @JsonProperty("orders") List<CoinbaseStreamingUserOrder> orders,
      @JsonProperty("fcm_balance_summary") CoinbaseFuturesBalanceSummary fcmBalanceSummary) {
    this.type = type;
    this.productId = productId;
    this.sequence = sequence;
    this.tickers = tickers == null ? Collections.emptyList() : Collections.unmodifiableList(tickers);
    this.trades = trades == null ? Collections.emptyList() : Collections.unmodifiableList(trades);
    this.candles = candles == null ? Collections.emptyList() : Collections.unmodifiableList(candles);
    this.updates = updates == null ? Collections.emptyList() : Collections.unmodifiableList(updates);
    this.bids = bids == null ? Collections.emptyList() : Collections.unmodifiableList(bids);
    this.asks = asks == null ? Collections.emptyList() : Collections.unmodifiableList(asks);
    this.orders = orders == null ? Collections.emptyList() : Collections.unmodifiableList(orders);
    this.fcmBalanceSummary = fcmBalanceSummary;
  }

  public String getType() {
    return type;
  }

  public String getProductId() {
    return productId;
  }

  public Long getSequence() {
    return sequence;
  }

  public List<CoinbaseStreamingTicker> getTickers() {
    return tickers;
  }

  public List<CoinbaseStreamingTrade> getTrades() {
    return trades;
  }

  public List<CoinbaseStreamingCandle> getCandles() {
    return candles;
  }

  public List<CoinbaseStreamingLevel2Update> getUpdates() {
    return updates;
  }

  public List<List<String>> getBids() {
    return bids;
  }

  public List<List<String>> getAsks() {
    return asks;
  }

  public List<CoinbaseStreamingUserOrder> getOrders() {
    return orders;
  }

  public CoinbaseFuturesBalanceSummary getFcmBalanceSummary() {
    return fcmBalanceSummary;
  }
}
