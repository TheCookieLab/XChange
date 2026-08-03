package info.bitrich.xchangestream.kalshi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import info.bitrich.xchangestream.core.StreamingMarketDataService;
import info.bitrich.xchangestream.kalshi.dto.KalshiWsMarketLifecycle;
import info.bitrich.xchangestream.kalshi.dto.KalshiWsOrderBookDelta;
import info.bitrich.xchangestream.kalshi.dto.KalshiWsOrderBookSnapshot;
import info.bitrich.xchangestream.kalshi.dto.KalshiWsTicker;
import info.bitrich.xchangestream.kalshi.dto.KalshiWsTrade;
import info.bitrich.xchangestream.service.netty.StreamingObjectMapperHelper;
import io.reactivex.rxjava3.core.Observable;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trade;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.kalshi.KalshiAdapters;

/**
 * Streaming market data over the Kalshi trade-api v2 WebSocket. All methods require a Kalshi
 * {@link org.knowm.xchange.prediction.PredictionMarketContract}; generic currency pairs are
 * rejected before any subscription is attempted.
 *
 * <p>The order-book stream anchors on the server snapshot and applies sequenced deltas; sequence
 * gaps terminate the stream with an {@link ExchangeException} instead of silently continuing (see
 * {@link KalshiStreamingOrderBook}).
 */
public class KalshiStreamingMarketDataService implements StreamingMarketDataService {

  private final KalshiStreamingService service;
  private final ObjectMapper mapper = StreamingObjectMapperHelper.getObjectMapper();

  public KalshiStreamingMarketDataService(KalshiStreamingService service) {
    this.service = service;
  }

  @Override
  public Observable<OrderBook> getOrderBook(Instrument instrument, Object... args) {
    String ticker = KalshiAdapters.marketTicker(instrument);
    KalshiStreamingOrderBook book = new KalshiStreamingOrderBook(ticker);
    return service
        .subscribeChannel(KalshiStreamingService.CHANNEL_ORDERBOOK, ticker)
        .map(
            node -> {
              long seq = node.path("seq").asLong(-1);
              JsonNode msg = node.path("msg");
              return switch (node.path("type").asText("")) {
                case "orderbook_snapshot" ->
                    book.applySnapshot(
                        seq, mapper.treeToValue(msg, KalshiWsOrderBookSnapshot.class));
                case "orderbook_delta" ->
                    book.applyDelta(seq, mapper.treeToValue(msg, KalshiWsOrderBookDelta.class));
                default ->
                    throw new ExchangeException(
                        "Unexpected Kalshi orderbook message type: " + node.path("type").asText());
              };
            });
  }

  @Override
  public Observable<Trade> getTrades(Instrument instrument, Object... args) {
    String ticker = KalshiAdapters.marketTicker(instrument);
    return service
        .subscribeChannel(KalshiStreamingService.CHANNEL_TRADE, ticker)
        .map(
            node ->
                KalshiStreamingAdapters.adaptTrade(
                    mapper.treeToValue(node.path("msg"), KalshiWsTrade.class)));
  }

  @Override
  public Observable<Ticker> getTicker(Instrument instrument, Object... args) {
    String ticker = KalshiAdapters.marketTicker(instrument);
    return service
        .subscribeChannel(KalshiStreamingService.CHANNEL_TICKER, ticker)
        .map(
            node ->
                KalshiStreamingAdapters.adaptTicker(
                    mapper.treeToValue(node.path("msg"), KalshiWsTicker.class)));
  }

  /**
   * Streams raw market status transitions ({@code market_lifecycle_v2}) for one market. There is
   * no generic XChange DTO for prediction-market settlement events, so the provider payload is
   * exposed unmapped.
   *
   * @param instrument Kalshi prediction-market contract
   * @return stream of raw lifecycle events (created/activated/determined/settled/...)
   */
  public Observable<KalshiWsMarketLifecycle> getMarketLifecycle(
      Instrument instrument, Object... args) {
    String ticker = KalshiAdapters.marketTicker(instrument);
    return service
        .subscribeChannel(KalshiStreamingService.CHANNEL_MARKET_LIFECYCLE, ticker)
        .map(node -> mapper.treeToValue(node.path("msg"), KalshiWsMarketLifecycle.class));
  }
}
