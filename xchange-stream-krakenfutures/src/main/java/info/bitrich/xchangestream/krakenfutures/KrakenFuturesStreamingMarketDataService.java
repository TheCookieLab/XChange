package info.bitrich.xchangestream.krakenfutures;

import com.fasterxml.jackson.databind.ObjectMapper;
import info.bitrich.xchangestream.core.StreamingMarketDataService;
import info.bitrich.xchangestream.krakenfutures.dto.KrakenFuturesStreamingOrderBookDeltaResponse;
import info.bitrich.xchangestream.krakenfutures.dto.KrakenFuturesStreamingOrderBookSnapshotResponse;
import info.bitrich.xchangestream.krakenfutures.dto.KrakenFuturesStreamingTickerResponse;
import info.bitrich.xchangestream.krakenfutures.dto.KrakenFuturesStreamingTradeResponse;
import info.bitrich.xchangestream.service.netty.StreamingObjectMapperHelper;
import io.reactivex.rxjava3.core.Observable;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.marketdata.*;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.krakenfutures.KrakenFuturesAdapters;

public class KrakenFuturesStreamingMarketDataService implements StreamingMarketDataService {

  static final String ORDERBOOK_CHANNEL = "book";
  static final String TICKER_CHANNEL = "ticker";
  static final String TRADES_CHANNEL = "trade";

  private final ObjectMapper objectMapper = StreamingObjectMapperHelper.getObjectMapper();
  private final KrakenFuturesStreamingService service;
  private final Map<Instrument, OrderBook> orderBookMap = new HashMap<>();
  private final Map<Instrument, Long> lastSeqMap = new HashMap<>();

  public KrakenFuturesStreamingMarketDataService(KrakenFuturesStreamingService service) {
    this.service = service;
  }

  @Override
  public Observable<OrderBook> getOrderBook(Instrument instrument, Object... args) {
    String channelName =
        ORDERBOOK_CHANNEL + KrakenFuturesAdapters.adaptKrakenFuturesSymbol(instrument);
    return service
        .subscribeChannel(channelName)
        .filter(message -> message.has("feed"))
        .concatMap(
            message -> {
              try {
                if (message.get("feed").asText().contains("book_snapshot")) {
                  KrakenFuturesStreamingOrderBookSnapshotResponse snapshot =
                      objectMapper.treeToValue(
                          message, KrakenFuturesStreamingOrderBookSnapshotResponse.class);
                  orderBookMap.put(
                      instrument,
                      KrakenFuturesStreamingAdapters.adaptKrakenFuturesSnapshot(snapshot));
                  lastSeqMap.put(instrument, snapshot.getSeq());
                } else if (message.get("feed").asText().equals(ORDERBOOK_CHANNEL)) {
                  KrakenFuturesStreamingOrderBookDeltaResponse delta =
                      objectMapper.treeToValue(
                          message, KrakenFuturesStreamingOrderBookDeltaResponse.class);
                  Long lastSeq = lastSeqMap.get(instrument);
                  if (lastSeq == null || delta.getSeq() == null || delta.getSeq() > lastSeq + 1) {
                    // gap: state unknown or sequence skipped — rebuild from a fresh snapshot
                    orderBookMap.remove(instrument);
                    service.resubscribeChannel(channelName);
                    return Observable.empty();
                  }
                  if (delta.getSeq() <= lastSeq) {
                    // duplicate redelivery: drop silently, the book state is already current
                    return Observable.empty();
                  }
                  orderBookMap
                      .get(instrument)
                      .update(
                          new LimitOrder.Builder(
                                  (delta
                                          .getSide()
                                          .equals(
                                              KrakenFuturesStreamingOrderBookDeltaResponse
                                                  .KrakenFuturesStreamingSide.sell))
                                      ? Order.OrderType.ASK
                                      : Order.OrderType.BID,
                                  instrument)
                              .limitPrice(delta.getPrice())
                              .originalAmount(delta.getQty())
                              .timestamp(delta.getTimestamp())
                              .build());
                  lastSeqMap.put(instrument, delta.getSeq());
                }
                if (orderBookMap
                        .get(instrument)
                        .getBids()
                        .get(0)
                        .getLimitPrice()
                        .compareTo(orderBookMap.get(instrument).getAsks().get(0).getLimitPrice())
                    > 0) {
                  throw new IOException("OrderBook crossed!!!");
                }
                return Observable.just(copyBook(instrument));
              } catch (Exception e) {
                throw new IOException(e);
              }
            });
  }

  /** Emits a defensive copy so later deltas cannot mutate already-published books. */
  private OrderBook copyBook(Instrument instrument) {
    OrderBook current = orderBookMap.get(instrument);
    return new OrderBook(
        current.getTimeStamp(),
        new java.util.ArrayList<>(current.getAsks()),
        new java.util.ArrayList<>(current.getBids()));
  }

  @Override
  public Observable<Ticker> getTicker(Instrument instrument, Object... args) {
    String channelName =
        TICKER_CHANNEL + KrakenFuturesAdapters.adaptKrakenFuturesSymbol(instrument);

    return service
        .subscribeChannel(channelName)
        .filter(message -> message.has("feed") && message.has("product_id"))
        .filter(
            message ->
                message
                    .get("product_id")
                    .asText()
                    .equals(KrakenFuturesAdapters.adaptKrakenFuturesSymbol(instrument)))
        .map(
            message ->
                KrakenFuturesStreamingAdapters.adaptTicker(
                    objectMapper.treeToValue(message, KrakenFuturesStreamingTickerResponse.class)));
  }

  @Override
  public Observable<Trade> getTrades(Instrument instrument, Object... args) {
    String channelName =
        TRADES_CHANNEL + KrakenFuturesAdapters.adaptKrakenFuturesSymbol(instrument);

    return service
        .subscribeChannel(channelName)
        .filter(message -> message.has("feed") && message.has("product_id"))
        .filter(message -> message.get("feed").asText().equals("trade"))
        .map(
            message ->
                KrakenFuturesStreamingAdapters.adaptTrade(
                    objectMapper.treeToValue(message, KrakenFuturesStreamingTradeResponse.class)));
  }

  @Override
  public Observable<FundingRate> getFundingRate(Instrument instrument, Object... args) {
    String channelName =
        TICKER_CHANNEL + KrakenFuturesAdapters.adaptKrakenFuturesSymbol(instrument);

    return service
        .subscribeChannel(channelName)
        .filter(message -> message.has("feed") && message.has("product_id"))
        .filter(
            message ->
                message
                    .get("product_id")
                    .asText()
                    .equals(KrakenFuturesAdapters.adaptKrakenFuturesSymbol(instrument)))
        .map(
            message ->
                KrakenFuturesStreamingAdapters.adaptFundingRate(
                    objectMapper.treeToValue(message, KrakenFuturesStreamingTickerResponse.class)));
  }
}
