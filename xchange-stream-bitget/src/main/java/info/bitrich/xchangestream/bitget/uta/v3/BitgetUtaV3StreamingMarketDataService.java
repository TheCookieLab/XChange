package info.bitrich.xchangestream.bitget.uta.v3;

import com.fasterxml.jackson.databind.JsonNode;
import info.bitrich.xchangestream.bitget.config.Config;
import info.bitrich.xchangestream.bitget.uta.v3.dto.BitgetUtaV3Channel;
import info.bitrich.xchangestream.bitget.uta.v3.dto.BitgetUtaV3KlineData;
import info.bitrich.xchangestream.bitget.uta.v3.dto.BitgetUtaV3OrderBookData;
import info.bitrich.xchangestream.bitget.uta.v3.dto.BitgetUtaV3OrderBookLevel;
import info.bitrich.xchangestream.bitget.uta.v3.dto.BitgetUtaV3PublicTradeData;
import info.bitrich.xchangestream.bitget.uta.v3.dto.BitgetUtaV3TickerData;
import info.bitrich.xchangestream.bitget.uta.v3.dto.BitgetUtaV3WsNotification;
import info.bitrich.xchangestream.core.StreamingMarketDataService;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.Validate;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.marketdata.CandleStickData;
import org.knowm.xchange.dto.marketdata.CandleStickInterval;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trade;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.instrument.Instrument;

/**
 * Bitget UTA v3 public WebSocket market data service.
 *
 * <p>Order-book depth is part of the topic: {@code books} (full depth, incremental), {@code
 * books1}/{@code books5}/{@code books50} (replace-only snapshots of the top 1/5/50 levels). The
 * depth is selected through the first {@code args} element as an {@link Integer} (1, 5 or 50);
 * omitting it subscribes to the full-depth {@code books} channel. Each subscription assembles its
 * own book locally; a continuity failure emits on {@link #subscribeOrderBookContinuityFailures()}
 * and resubscribes the channel for a fresh snapshot without terminating the order-book observable.
 *
 * @since 5.1.0
 */
@Slf4j
public class BitgetUtaV3StreamingMarketDataService implements StreamingMarketDataService {

  private final BitgetUtaV3StreamingService service;
  private final ConcurrentMap<String, BitgetUtaV3OrderBookAssembler> assemblers =
      new ConcurrentHashMap<>();
  private final ConcurrentMap<String, Observable<BitgetUtaV3WsNotification>> sharedChannels =
      new ConcurrentHashMap<>();
  private final PublishSubject<Throwable> orderBookContinuityFailures = PublishSubject.create();

  public BitgetUtaV3StreamingMarketDataService(BitgetUtaV3StreamingService service) {
    this.service = service;
  }

  /** Dedicated failure stream for order-book continuity errors (never the order-book stream). */
  public Observable<Throwable> subscribeOrderBookContinuityFailures() {
    return orderBookContinuityFailures.share();
  }

  /**
   * Shared per-subscription-id channel stream with order-book assembler lifecycle: when the last
   * subscriber disposes, the underlying channel is unsubscribed and the assembler for that
   * subscription id is evicted, so a stream of short-lived book subscriptions cannot grow the
   * {@link #assemblers} map without bound. A stream that terminates (e.g. a not-connected
   * subscription rejected by the service before {@code connect()} completes) is evicted from the
   * cache, so a post-connect retry of the same ticker, book, trade, or kline channel builds a
   * fresh shared observable instead of reusing the dead one; a normally disposed shared stream
   * (final subscriber left) is evicted as well, so the cache cannot grow without bound either.
   */
  private Observable<BitgetUtaV3WsNotification> sharedChannel(BitgetUtaV3Channel channel) {
    String subscriptionId = channel.toSubscriptionId();
    AtomicReference<Observable<BitgetUtaV3WsNotification>> ref = new AtomicReference<>();
    Observable<BitgetUtaV3WsNotification> shared =
        service
            .subscribeChannel(null, channel)
            .doOnError(t -> sharedChannels.remove(subscriptionId, ref.get()))
            .doFinally(
                () -> {
                  assemblers.remove(subscriptionId);
                  // a normally disposed shared stream (final subscriber left) also drops the
                  // cached observable, so short-lived book/ticker/trade/kline subscriptions
                  // cannot grow the sharedChannels map without bound; remove(key, value) never
                  // clobbers a concurrently replaced entry
                  sharedChannels.remove(subscriptionId, ref.get());
                })
            .share();
    ref.set(shared);
    return sharedChannels.computeIfAbsent(subscriptionId, id -> ref.get());
  }

  /** Test seam (same package): the cached shared observables keyed by subscription id. */
  Map<String, Observable<BitgetUtaV3WsNotification>> sharedChannelsForTesting() {
    return sharedChannels;
  }

  @Override
  public Observable<OrderBook> getOrderBook(CurrencyPair currencyPair, Object... args) {
    return getOrderBook((Instrument) currencyPair, args);
  }

  @Override
  public Observable<OrderBook> getOrderBook(Instrument instrument, Object... args) {
    Integer depth = null;
    if (ArrayUtils.isNotEmpty(args) && args[0] instanceof Integer) {
      depth = (Integer) args[0];
    }
    String topic = depth == null ? "books" : "books" + depth;
    if (depth != null) {
      Validate.isTrue(
          depth == 1 || depth == 5 || depth == 50,
          "Bitget UTA v3 order-book depth must be 1, 5 or 50, got %s",
          depth);
    }
    BitgetUtaV3Channel channel =
        BitgetUtaV3Channel.builder()
            .instType(BitgetUtaV3StreamingAdapters.toInstType(instrument))
            .topic(topic)
            .symbol(BitgetUtaV3StreamingAdapters.toString(instrument))
            .build();
    String subscriptionId = channel.toSubscriptionId();
    assemblers.computeIfAbsent(subscriptionId, id -> new BitgetUtaV3OrderBookAssembler());

    return sharedChannel(channel)
        .flatMap(notification -> processOrderBookPush(notification, instrument, channel))
        .filter(book -> book != null);
  }

  private Observable<OrderBook> processOrderBookPush(
      BitgetUtaV3WsNotification notification, Instrument instrument, BitgetUtaV3Channel channel) {
    String subscriptionId = channel.toSubscriptionId();
    // recreate lazily: the shared channel evicts the assembler when the last subscriber leaves, so
    // a later resubscription of the same observable must not hit a null assembler (PRD CF-451)
    BitgetUtaV3OrderBookAssembler assembler =
        assemblers.computeIfAbsent(subscriptionId, id -> new BitgetUtaV3OrderBookAssembler());
    List<OrderBook> books = new ArrayList<>();
    for (JsonNode item : notification.getPayloadItems()) {
      BitgetUtaV3OrderBookData data;
      try {
        data =
            Config.getInstance()
                .getObjectMapper()
                .treeToValue(item, BitgetUtaV3OrderBookData.class);
      } catch (IOException e) {
        log.error("Failed to parse order-book push for {}: {}", subscriptionId, item, e);
        continue;
      }
      try {
        assembler.apply(data, notification.getAction(), subscriptionId);
        if (assembler.hasSnapshot()) {
          books.add(toOrderBook(assembler, instrument, data, notification.getTimestamp()));
        }
      } catch (BitgetUtaV3OrderBookContinuityException e) {
        log.warn(
            "Order-book continuity lost for {}; resubscribing for a fresh snapshot",
            subscriptionId);
        orderBookContinuityFailures.onNext(e);
        service.resubscribeChannel(null, channel);
        books.clear();
        break;
      }
    }
    return Observable.fromIterable(books);
  }

  private OrderBook toOrderBook(
      BitgetUtaV3OrderBookAssembler assembler,
      Instrument instrument,
      BitgetUtaV3OrderBookData data,
      Long envelopeTimestamp) {
    List<LimitOrder> asks = new ArrayList<>();
    List<LimitOrder> bids = new ArrayList<>();
    for (BitgetUtaV3OrderBookLevel level : assembler.getAsks()) {
      asks.add(
          new LimitOrder.Builder(OrderType.ASK, instrument)
              .limitPrice(level.getPrice())
              .originalAmount(level.getSize())
              .build());
    }
    for (BitgetUtaV3OrderBookLevel level : assembler.getBids()) {
      bids.add(
          new LimitOrder.Builder(OrderType.BID, instrument)
              .limitPrice(level.getPrice())
              .originalAmount(level.getSize())
              .build());
    }
    Long ts = data.getTs() != null ? data.getTs() : envelopeTimestamp;
    return new OrderBook(ts == null ? null : new Date(ts), asks, bids);
  }

  @Override
  public Observable<Ticker> getTicker(CurrencyPair currencyPair, Object... args) {
    return getTicker((Instrument) currencyPair, args);
  }

  @Override
  public Observable<Ticker> getTicker(Instrument instrument, Object... args) {
    BitgetUtaV3Channel channel =
        BitgetUtaV3Channel.builder()
            .instType(BitgetUtaV3StreamingAdapters.toInstType(instrument))
            .topic("ticker")
            .symbol(BitgetUtaV3StreamingAdapters.toString(instrument))
            .build();
    return sharedChannel(channel)
        .flatMap(
            notification ->
                Observable.fromIterable(notification.getPayloadItems())
                    .map(item -> new Object[] {notification.getTimestamp(), item}))
        .map(
            pair -> {
              Long envelopeTs = (Long) pair[0];
              BitgetUtaV3TickerData dto =
                  Config.getInstance()
                      .getObjectMapper()
                      .treeToValue((JsonNode) pair[1], BitgetUtaV3TickerData.class);
              return BitgetUtaV3StreamingAdapters.toTicker(dto, instrument, envelopeTs);
            });
  }

  @Override
  public Observable<Trade> getTrades(CurrencyPair currencyPair, Object... args) {
    return getTrades((Instrument) currencyPair, args);
  }

  @Override
  public Observable<Trade> getTrades(Instrument instrument, Object... args) {
    BitgetUtaV3Channel channel =
        BitgetUtaV3Channel.builder()
            .instType(BitgetUtaV3StreamingAdapters.toInstType(instrument))
            .topic("publicTrade")
            .symbol(BitgetUtaV3StreamingAdapters.toString(instrument))
            .build();
    return sharedChannel(channel)
        .flatMap(notification -> Observable.fromIterable(notification.getPayloadItems()))
        .map(
            item ->
                Config.getInstance()
                    .getObjectMapper()
                    .treeToValue(item, BitgetUtaV3PublicTradeData.class))
        .map(dto -> BitgetUtaV3StreamingAdapters.toTrade(dto, instrument));
  }

  @Override
  public Observable<CandleStickData> getCandleStick(
      Instrument instrument, CandleStickInterval interval) {
    String wireInterval = BitgetUtaV3StreamingAdapters.toInterval(interval);
    BitgetUtaV3Channel channel =
        BitgetUtaV3Channel.builder()
            .instType(BitgetUtaV3StreamingAdapters.toInstType(instrument))
            .topic("kline")
            .symbol(BitgetUtaV3StreamingAdapters.toString(instrument))
            .interval(wireInterval)
            .build();
    return sharedChannel(channel)
        .flatMap(notification -> Observable.fromIterable(notification.getPayloadItems()))
        .map(
            item ->
                Config.getInstance()
                    .getObjectMapper()
                    .treeToValue(item, BitgetUtaV3KlineData.class))
        .map(dto -> BitgetUtaV3StreamingAdapters.toCandle(dto, instrument));
  }
}
