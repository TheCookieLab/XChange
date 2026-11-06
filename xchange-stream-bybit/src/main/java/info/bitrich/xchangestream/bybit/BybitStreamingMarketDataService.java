package info.bitrich.xchangestream.bybit;

import static info.bitrich.xchangestream.bybit.BybitStreamAdapters.adaptFundingRateInterval;
import static org.knowm.xchange.bybit.BybitAdapters.convertToBybitSymbol;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import info.bitrich.xchangestream.bybit.dto.BybitResponse;
import info.bitrich.xchangestream.bybit.dto.marketdata.BybitOrderBookGap;
import info.bitrich.xchangestream.bybit.dto.marketdata.BybitOrderbook;
import info.bitrich.xchangestream.bybit.dto.marketdata.BybitPublicOrder;
import info.bitrich.xchangestream.bybit.dto.trade.BybitTrade;
import info.bitrich.xchangestream.core.StreamingMarketDataService;
import info.bitrich.xchangestream.service.netty.StreamingObjectMapperHelper;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.knowm.xchange.bybit.BybitAdapters;
import org.knowm.xchange.bybit.dto.marketdata.candles.BybitCandleStick;
import org.knowm.xchange.bybit.dto.marketdata.tickers.linear.BybitLinearInverseTicker;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.marketdata.*;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.instrument.Instrument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BybitStreamingMarketDataService implements StreamingMarketDataService {

  private final Logger LOG = LoggerFactory.getLogger(BybitStreamingMarketDataService.class);
  private final BybitStreamingService streamingService;
  private final ObjectMapper mapper = StreamingObjectMapperHelper.getObjectMapper();
  public static final String TRADE = "publicTrade.";
  public static final String ORDERBOOK = "orderbook.";
  public static final String TICKER = "tickers.";
  public static final String CANDLE = "kline.";
  private static final OrderBook NO_BOOK =
      new OrderBook(null, Collections.emptyList(), Collections.emptyList(), false);

  /** Active order-book channels by symbol and depth, shared by duplicate subscribers. */
  private final Map<String, NavigableMap<Integer, OrderBookChannel>> orderBookChannels =
      new HashMap<>();
  private final Map<Instrument, PublishSubject<List<OrderBookUpdate>>>
      orderBookUpdatesSubscriptions;

  /** Dedicated continuity-gap signals for order-book streams (see {@link BybitOrderBookGap}). */
  private final PublishSubject<BybitOrderBookGap> orderBookGapSubject = PublishSubject.create();

  private final Map<String, FundingRate> fundingRateMap = new HashMap<>();
  private final Map<String, BybitLinearInverseTicker> tickerSnapshotMap = new HashMap<>();

  public BybitStreamingMarketDataService(BybitStreamingService streamingService) {
    this.streamingService = streamingService;
    this.orderBookUpdatesSubscriptions = new ConcurrentHashMap<>();
  }

  /**
   * Linear & inverse: Level 1 data, push frequency: 10ms Level 50 data, push frequency: 20ms Level
   * 200 data, push frequency: 100ms Level 500 data, push frequency: 100ms Spot: Level 1 data, push
   * frequency: 10ms Level 50 data, push frequency: 20ms Level 200 data, push frequency: 200ms
   *
   * @param args order-book depth or comma-separated depths; the deepest active depth for the
   *     symbol governs the shared book across subscriptions
   */
  @Override
  public Observable<OrderBook> getOrderBook(Instrument instrument, Object... args) {
    List<Integer> depths = new ArrayList<>();
    if (args.length > 0 && args[0] != null) {
      try {
        for (String value : args[0].toString().split(",", -1)) {
          depths.add(Integer.parseInt(value.trim()));
        }
      } catch (NumberFormatException e) {
        throw new ExchangeException(
            "Order book depth must be an integer or comma-separated integers");
      }
    } else {
      depths.add(50);
    }
    depths.sort(Comparator.reverseOrder());

    String bybitSymbol = convertToBybitSymbol(instrument);
    List<Observable<OrderBook>> streams = new ArrayList<>(depths.size());
    for (int i = 0; i < depths.size(); i++) {
      int depth = depths.get(i);
      streams.add(subscribeOrderBookChannel(instrument, bybitSymbol, depth, args, i == 0));
    }
    return Observable.merge(streams);
  }

  private Observable<OrderBook> subscribeOrderBookChannel(
      Instrument instrument, String bybitSymbol, int depth, Object[] args, boolean emitBook) {
    return Observable.defer(
        () -> {
          OrderBookChannel channel;
          synchronized (orderBookChannels) {
            NavigableMap<Integer, OrderBookChannel> depths =
                orderBookChannels.computeIfAbsent(bybitSymbol, key -> new TreeMap<>());
            channel =
                depths.computeIfAbsent(
                    depth, key -> new OrderBookChannel(instrument, bybitSymbol, depth, args));
            channel.subscribers++;
            if (emitBook) {
              channel.bookSubscribers++;
            }
          }
          Observable<OrderBook> values =
              emitBook ? channel.orderBooks : channel.orderBooks.ignoreElements().toObservable();
          return values.doFinally(
              () -> {
                synchronized (orderBookChannels) {
                  if (emitBook) {
                    channel.bookSubscribers--;
                  }
                  if (--channel.subscribers == 0) {
                    NavigableMap<Integer, OrderBookChannel> depths =
                        orderBookChannels.get(bybitSymbol);
                    depths.remove(depth);
                    if (depths.isEmpty()) {
                      orderBookChannels.remove(bybitSymbol);
                    }
                  }
                }
              });
        });
  }

  private OrderBook governingBook(String bybitSymbol) {
    synchronized (orderBookChannels) {
      NavigableMap<Integer, OrderBookChannel> depths = orderBookChannels.get(bybitSymbol);
      return depths == null || depths.isEmpty() ? null : depths.lastEntry().getValue().book.get();
    }
  }

  private final class OrderBookChannel {
    private final Observable<JsonNode> messages;
    private final Observable<OrderBook> orderBooks;
    private final AtomicReference<OrderBook> book = new AtomicReference<>();
    private int subscribers;
    // Compound requests subscribe to shallower channels without emitting their books.
    private int bookSubscribers;

    private OrderBookChannel(
        Instrument instrument, String bybitSymbol, int depth, Object[] args) {
      String channelUniqueId = ORDERBOOK + depth + "." + bybitSymbol;
      messages = streamingService.subscribeChannel(channelUniqueId).share();
      orderBooks =
          Observable.defer(
                  () -> {
                    AtomicLong previousUpdateId = new AtomicLong();
                    return messages.map(
                        jsonNode -> {
                          boolean governing;
                          boolean emitEvents;
                          synchronized (orderBookChannels) {
                            NavigableMap<Integer, OrderBookChannel> depths =
                                orderBookChannels.get(bybitSymbol);
                            governing =
                                depths != null
                                    && !depths.isEmpty()
                                    && depths.lastEntry().getValue() == this;
                            emitEvents = governing && bookSubscribers > 0;
                          }
                          try {
                            BybitOrderbook update =
                                mapper.treeToValue(jsonNode, BybitOrderbook.class);
                            String type = update.getDataType();
                            if (type.equalsIgnoreCase("snapshot")) {
                              previousUpdateId.set(update.getData().getU());
                              OrderBook snapshot =
                                  BybitStreamAdapters.adaptOrderBook(update, instrument);
                              book.set(snapshot);
                              if (governing) {
                                return snapshot;
                              }
                              OrderBook current = governingBook(bybitSymbol);
                              return current != null ? current : NO_BOOK;
                            } else if (type.equalsIgnoreCase("delta")) {
                              if (!emitEvents) {
                                return NO_BOOK;
                              }
                              return applyOrderBookDeltaSnapshot(
                                  book, instrument, channelUniqueId, update, previousUpdateId);
                            }
                            return NO_BOOK;
                          } catch (IllegalStateException e) {
                            book.set(null);
                            if (emitEvents) {
                              LOG.warn(
                                  "Resubscribing {} channel after order book recovery {}",
                                  channelUniqueId,
                                  e.getMessage());
                              if (streamingService.isSocketOpen()) {
                                streamingService.sendMessage(
                                    streamingService.getUnsubscribeMessage(channelUniqueId, args));
                                streamingService.sendMessage(
                                    streamingService.getSubscribeMessage(channelUniqueId, args));
                              }
                            }
                            return NO_BOOK;
                          }
                        });
                  })
              .replay(1)
              .refCount()
              // A replayed book must still belong to the currently governing depth.
              .filter(
                  orderBook ->
                      orderBook == governingBook(bybitSymbol)
                          && !orderBook.getBids().isEmpty()
                          && !orderBook.getAsks().isEmpty());
    }
  }

  private OrderBook applyOrderBookDeltaSnapshot(
      AtomicReference<OrderBook> book,
      Instrument instrument,
      String channelUniqueId,
      BybitOrderbook bybitOrderBookUpdate,
      AtomicLong orderBookUpdateIdPrev) {
    OrderBook orderBook = book.get();
    if (orderBook == null) {
      LOG.error("Failed to get order book snapshot, channel={}", channelUniqueId);
      orderBookGapSubject.onNext(
          new BybitOrderBookGap(
              channelUniqueId,
              orderBookUpdateIdPrev.get(),
              bybitOrderBookUpdate.getData().getU(),
              "missing-snapshot"));
      throw new IllegalStateException(
          "orderBookUpdate without snapshot, channel " + channelUniqueId);
    }
    if (orderBookUpdateIdPrev.incrementAndGet() == bybitOrderBookUpdate.getData().getU()) {
      LOG.debug(
          "orderBookUpdate id {}, seq {} ",
          bybitOrderBookUpdate.getData().getU(),
          bybitOrderBookUpdate.getData().getSeq());
      List<BybitPublicOrder> asks = bybitOrderBookUpdate.getData().getAsk();
      List<BybitPublicOrder> bids = bybitOrderBookUpdate.getData().getBid();
      Date timestamp = new Date(bybitOrderBookUpdate.getCts());
      asks.forEach(
          bybitPublicOrder ->
              orderBook.update(
                  BybitStreamAdapters.adaptOrderBookOrder(
                      bybitPublicOrder, instrument, Order.OrderType.ASK, timestamp)));
      bids.forEach(
          bybitPublicOrder ->
              orderBook.update(
                  BybitStreamAdapters.adaptOrderBookOrder(
                      bybitPublicOrder, instrument, Order.OrderType.BID, timestamp)));
      if (orderBookUpdatesSubscriptions.get(instrument) != null) {
        orderBookUpdatesSubscriptions(instrument, asks, bids, timestamp);
      }
      return orderBook;
    } else {
      long expected = orderBookUpdateIdPrev.get();
      long actual = bybitOrderBookUpdate.getData().getU();
      // Invalidate the replay before gap observers can attach another subscriber.
      book.set(null);
      LOG.error(
          "orderBookUpdate id sequence failed, expected {}, in fact {}",
          expected,
          actual);
      orderBookGapSubject.onNext(
          new BybitOrderBookGap(channelUniqueId, expected, actual, "sequence"));
      throw new IllegalStateException("orderBookUpdate id sequence failed");
    }
  }

  /**
   * Dedicated order-book continuity-gap signals. A gap is emitted before the affected channel is
   * resubscribed and rebuilt from a fresh snapshot; the stream itself recovers automatically.
   */
  public Observable<BybitOrderBookGap> getOrderBookGapEvents() {
    return orderBookGapSubject;
  }

  @Override
  public Observable<List<OrderBookUpdate>> getOrderBookUpdates(
      Instrument instrument, Object... args) {
    return orderBookUpdatesSubscriptions.computeIfAbsent(instrument, v -> PublishSubject.create());
  }

  private void orderBookUpdatesSubscriptions(
      Instrument instrument, List<BybitPublicOrder> asks, List<BybitPublicOrder> bids, Date date) {
    List<OrderBookUpdate> orderBookUpdates = new ArrayList<>();
    for (BybitPublicOrder ask : asks) {
      OrderBookUpdate o =
          new OrderBookUpdate(
              Order.OrderType.ASK,
              new BigDecimal(ask.getSize()),
              instrument,
              new BigDecimal(ask.getPrice()),
              date,
              new BigDecimal(ask.getSize()));
      orderBookUpdates.add(o);
    }
    for (BybitPublicOrder bid : bids) {
      OrderBookUpdate o =
          new OrderBookUpdate(
              Order.OrderType.BID,
              new BigDecimal(bid.getSize()),
              instrument,
              new BigDecimal(bid.getPrice()),
              date,
              new BigDecimal(bid.getSize()));
      orderBookUpdates.add(o);
    }
    orderBookUpdatesSubscriptions.get(instrument).onNext(orderBookUpdates);
  }

  @Override
  public Observable<Trade> getTrades(Instrument instrument, Object... args) {
    String channelUniqueId = TRADE + convertToBybitSymbol(instrument);

    return streamingService
        .subscribeChannel(channelUniqueId)
        .filter(message -> message.has("data"))
        .flatMap(
            jsonNode -> {
              List<BybitTrade> bybitTradeList =
                  mapper.treeToValue(
                      jsonNode.get("data"),
                      mapper
                          .getTypeFactory()
                          .constructCollectionType(List.class, BybitTrade.class));
              return Observable.fromIterable(
                  BybitStreamAdapters.adaptTrades(bybitTradeList, instrument).getTrades());
            });
  }

  @Override
  public Observable<Ticker> getTicker(Instrument instrument, Object... args) {
    String channelUniqueId = TICKER + convertToBybitSymbol(instrument);
    return streamingService
        .subscribeChannel(channelUniqueId)
        .map(
            jsonNode -> {
              BybitResponse<BybitLinearInverseTicker> bybitTicker =
                  mapper.treeToValue(jsonNode, new TypeReference<>() {});
              String type = bybitTicker.getType();
              if (type.equalsIgnoreCase("snapshot")) {
                tickerSnapshotMap.put(channelUniqueId, bybitTicker.getData());
                return BybitStreamAdapters.adaptTicker(bybitTicker.getData());
              } else if (type.equalsIgnoreCase("delta")) {
                return applyTickerDelta(channelUniqueId, bybitTicker.getData());
              }
              return null;
            })
        .filter(ticker -> ticker != null);
  }

  private Ticker applyTickerDelta(String channelUniqueId, BybitLinearInverseTicker delta) {
    BybitLinearInverseTicker snapshot = tickerSnapshotMap.getOrDefault(channelUniqueId, null);
    if (snapshot == null) {
      LOG.error("Failed to get ticker snapshot, channelUniqueId= {}", channelUniqueId);
      return null;
    }
    // Bybit deltas only contain changed fields; rebuild snapshot merging delta values
    BybitLinearInverseTicker merged =
        BybitLinearInverseTicker.builder()
            .symbol(snapshot.getSymbol())
            .lastPrice(coalesce(delta.getLastPrice(), snapshot.getLastPrice()))
            .bid1Price(coalesce(delta.getBid1Price(), snapshot.getBid1Price()))
            .bid1Size(coalesce(delta.getBid1Size(), snapshot.getBid1Size()))
            .ask1Price(coalesce(delta.getAsk1Price(), snapshot.getAsk1Price()))
            .ask1Size(coalesce(delta.getAsk1Size(), snapshot.getAsk1Size()))
            .highPrice24h(coalesce(delta.getHighPrice24h(), snapshot.getHighPrice24h()))
            .lowPrice24h(coalesce(delta.getLowPrice24h(), snapshot.getLowPrice24h()))
            .volume24h(coalesce(delta.getVolume24h(), snapshot.getVolume24h()))
            .turnover24h(coalesce(delta.getTurnover24h(), snapshot.getTurnover24h()))
            .price24hPcnt(coalesce(delta.getPrice24hPcnt(), snapshot.getPrice24hPcnt()))
            .indexPrice(coalesce(delta.getIndexPrice(), snapshot.getIndexPrice()))
            .markPrice(coalesce(delta.getMarkPrice(), snapshot.getMarkPrice()))
            .prevPrice1h(coalesce(delta.getPrevPrice1h(), snapshot.getPrevPrice1h()))
            .prevPrice24h(coalesce(delta.getPrevPrice24h(), snapshot.getPrevPrice24h()))
            .openInterest(coalesce(delta.getOpenInterest(), snapshot.getOpenInterest()))
            .openInterestValue(
                coalesce(delta.getOpenInterestValue(), snapshot.getOpenInterestValue()))
            .fundingRate(coalesce(delta.getFundingRate(), snapshot.getFundingRate()))
            .nextFundingTime(
                delta.getNextFundingTime() != null
                    ? delta.getNextFundingTime()
                    : snapshot.getNextFundingTime())
            .fundingIntervalHour(
                delta.getFundingIntervalHour() != null
                    ? delta.getFundingIntervalHour()
                    : snapshot.getFundingIntervalHour())
            .predictedDeliveryPrice(
                coalesce(delta.getPredictedDeliveryPrice(), snapshot.getPredictedDeliveryPrice()))
            .basisRate(coalesce(delta.getBasisRate(), snapshot.getBasisRate()))
            .basis(coalesce(delta.getBasis(), snapshot.getBasis()))
            .deliveryFeeRate(coalesce(delta.getDeliveryFeeRate(), snapshot.getDeliveryFeeRate()))
            .deliveryTime(
                delta.getDeliveryTime() != null
                    ? delta.getDeliveryTime()
                    : snapshot.getDeliveryTime())
            .fundingCap(
                delta.getFundingCap() != null ? delta.getFundingCap() : snapshot.getFundingCap())
            .build();
    tickerSnapshotMap.put(channelUniqueId, merged);
    return BybitStreamAdapters.adaptTicker(merged);
  }

  private static <T> T coalesce(T a, T b) {
    return a != null ? a : b;
  }

  @Override
  public Observable<FundingRate> getFundingRate(Instrument instrument, Object... args) {
    String channelUniqueId = TICKER + convertToBybitSymbol(instrument);
    return streamingService
        .subscribeChannel(channelUniqueId)
        .map(
            jsonNode -> {
              BybitResponse<BybitLinearInverseTicker> bybitTicker =
                  mapper.treeToValue(jsonNode, new TypeReference<>() {});
              String type = bybitTicker.getType();
              if (type.equalsIgnoreCase("snapshot")) {
                FundingRate fundingRate =
                    BybitStreamAdapters.adaptFundingRate(bybitTicker.getData());
                fundingRateMap.put(channelUniqueId, fundingRate);
                return fundingRate;
              } else if (type.equalsIgnoreCase("delta")) {
                return applyFundingRateDelta(channelUniqueId, bybitTicker.getData());
              }
              return new FundingRate();
            })
        .filter(pred -> pred.getFundingRate() != null);
  }

  private FundingRate applyFundingRateDelta(
      String channelUniqueId, BybitLinearInverseTicker bybitTicker) {
    FundingRate fundingRate = fundingRateMap.getOrDefault(channelUniqueId, null);
    if (fundingRate != null) {
      boolean returnNew = false;
      if (bybitTicker.getFundingRate() != null) {
        fundingRate.setFundingRate(bybitTicker.getFundingRate());
        returnNew = true;
      }
      if (bybitTicker.getNextFundingTime() != null) {
        fundingRate.setFundingRateDate(bybitTicker.getNextFundingTime());
        returnNew = true;
      }
      if (bybitTicker.getFundingIntervalHour() != null) {
        fundingRate.setFundingRateInterval(
            adaptFundingRateInterval(bybitTicker.getFundingIntervalHour()));
        returnNew = true;
      }
      if (returnNew) {
        fundingRate.setFundingRate1h(
            fundingRate
                .getFundingRate()
                .divide(
                    BigDecimal.valueOf(fundingRate.getFundingRateInterval().getHours()),
                    fundingRate.getFundingRate().scale(),
                    RoundingMode.HALF_EVEN));
        fundingRate.setFundingRateEffectiveInMinutes(
            TimeUnit.MILLISECONDS.toMinutes(
                fundingRate.getFundingRateDate().getTime() - System.currentTimeMillis()));
        return fundingRate;
      } else {
        return new FundingRate();
      }
    } else {
      LOG.error("Failed to get fundingRate, channelUniqueId= {}", channelUniqueId);
      return new FundingRate();
    }
  }

  @Override
  public Observable<CandleStickData> getCandleStick(
      Instrument instrument, CandleStickInterval interval) {
    String channelUniqueId =
        CANDLE
            + BybitAdapters.toBybitCandleStickInterval(interval).getCode()
            + "."
            + convertToBybitSymbol(instrument);
    return streamingService
        .subscribeChannel(channelUniqueId)
        .map(
            jsonNode -> {
              BybitResponse<List<BybitCandleStick>> bybitCandles =
                  mapper.treeToValue(jsonNode, new TypeReference<>() {});
              return BybitStreamAdapters.adaptCandles(bybitCandles.getData().get(0), instrument);
            });
  }
}
