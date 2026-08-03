package info.bitrich.xchangestream.polymarket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import info.bitrich.xchangestream.core.StreamingMarketDataService;
import info.bitrich.xchangestream.polymarket.dto.PolymarketWsBook;
import info.bitrich.xchangestream.polymarket.dto.PolymarketWsLastTradePrice;
import info.bitrich.xchangestream.polymarket.dto.PolymarketWsPriceChange;
import info.bitrich.xchangestream.service.netty.StreamingObjectMapperHelper;
import io.reactivex.rxjava3.core.Observable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trade;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.polymarket.PolymarketAdapters;

/**
 * Streaming market data over the Polymarket CLOB market channel. All methods require a Polymarket
 * {@link org.knowm.xchange.prediction.PredictionMarketContract}; generic currency pairs are
 * rejected before any subscription is attempted.
 *
 * <p>The provider multiplexes every market event for one outcome token onto a single channel, so
 * all public methods share one memoized subscription per token id (the base streaming service
 * would otherwise orphan any second subscriber on the same channel). The order-book stream anchors
 * on full {@code book} snapshots and applies absolute-size {@code price_change} updates; integrity
 * violations terminate the stream with an {@link ExchangeException} instead of silently continuing
 * (see {@link PolymarketStreamingOrderBook}).
 */
public class PolymarketStreamingMarketDataService implements StreamingMarketDataService {

  private final PolymarketStreamingService service;
  private final ObjectMapper mapper = StreamingObjectMapperHelper.getObjectMapper();
  private final Map<String, Observable<JsonNode>> marketChannels =
      new ConcurrentHashMap<>();

  public PolymarketStreamingMarketDataService(PolymarketStreamingService service) {
    this.service = service;
  }

  @Override
  public Observable<OrderBook> getOrderBook(Instrument instrument, Object... args) {
    String tokenId = PolymarketAdapters.tokenId(instrument);
    PolymarketStreamingOrderBook book =
        new PolymarketStreamingOrderBook(PolymarketAdapters.conditionId(instrument), tokenId);
    return marketChannel(tokenId)
        .flatMap(
            node -> {
              switch (node.path("event_type").asText("")) {
                case "book" ->
                    {
                      return Observable.just(
                          book.applySnapshot(mapper.treeToValue(node, PolymarketWsBook.class)));
                    }
                case "price_change" ->
                    {
                      PolymarketWsPriceChange event =
                          mapper.treeToValue(node, PolymarketWsPriceChange.class);
                      OrderBook updated = null;
                      if (event.priceChanges() != null) {
                        for (PolymarketWsPriceChange.Change change : event.priceChanges()) {
                          updated = book.applyPriceChange(change, event.timestamp());
                        }
                      }
                      return updated == null ? Observable.empty() : Observable.just(updated);
                    }
                default ->
                    {
                      // last_trade_price, tick_size_change, ... carry no book state.
                      return Observable.empty();
                    }
              }
            });
  }

  @Override
  public Observable<Trade> getTrades(Instrument instrument, Object... args) {
    String tokenId = PolymarketAdapters.tokenId(instrument);
    return marketChannel(tokenId)
        .filter(node -> "last_trade_price".equals(node.path("event_type").asText("")))
        .map(
            node ->
                PolymarketStreamingAdapters.adaptLastTradePrice(
                    mapper.treeToValue(node, PolymarketWsLastTradePrice.class)));
  }

  /**
   * Top-of-book ticker stream from {@code book} and {@code price_change} events; each price-change
   * entry yields one ticker from its best bid/ask fields.
   */
  @Override
  public Observable<Ticker> getTicker(Instrument instrument, Object... args) {
    String tokenId = PolymarketAdapters.tokenId(instrument);
    return marketChannel(tokenId)
        .flatMap(
            node -> {
              switch (node.path("event_type").asText("")) {
                case "book" ->
                    {
                      PolymarketWsBook book = mapper.treeToValue(node, PolymarketWsBook.class);
                      return Observable.just(PolymarketStreamingAdapters.adaptTicker(book));
                    }
                case "price_change" ->
                    {
                      PolymarketWsPriceChange event =
                          mapper.treeToValue(node, PolymarketWsPriceChange.class);
                      if (event.priceChanges() == null) {
                        return Observable.empty();
                      }
                      return Observable.fromIterable(event.priceChanges())
                          .map(
                              change ->
                                  PolymarketStreamingAdapters.adaptTicker(
                                      change, event.market(), event.timestamp()));
                    }
                default ->
                    {
                      return Observable.empty();
                    }
              }
            });
  }

  private Observable<JsonNode> marketChannel(String tokenId) {
    return marketChannels.computeIfAbsent(
        tokenId, t -> service.subscribeChannel(PolymarketStreamingService.CHANNEL_MARKET, t));
  }
}
