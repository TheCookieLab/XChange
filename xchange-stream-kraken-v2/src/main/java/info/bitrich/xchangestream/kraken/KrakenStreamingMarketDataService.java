package info.bitrich.xchangestream.kraken;

import info.bitrich.xchangestream.core.StreamingMarketDataService;
import info.bitrich.xchangestream.kraken.dto.common.ChannelType;
import info.bitrich.xchangestream.kraken.dto.response.KrakenBookMessage;
import info.bitrich.xchangestream.kraken.dto.response.KrakenBookMessage.KrakenBookLevels;
import info.bitrich.xchangestream.kraken.dto.response.KrakenDataMessage;
import info.bitrich.xchangestream.kraken.dto.response.KrakenMessage.KrakenMessageType;
import info.bitrich.xchangestream.kraken.dto.response.KrakenOhlcMessage;
import info.bitrich.xchangestream.kraken.dto.response.KrakenOhlcMessage.KrakenOhlcLevel;
import info.bitrich.xchangestream.kraken.dto.response.KrakenStatusMessage;
import info.bitrich.xchangestream.kraken.dto.response.KrakenTickerMessage;
import info.bitrich.xchangestream.kraken.dto.response.KrakenTradeMessage;
import io.reactivex.rxjava3.core.Observable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trade;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.instrument.Instrument;

@Slf4j
public class KrakenStreamingMarketDataService implements StreamingMarketDataService {

  private final KrakenStreamingService service;

  /** Incremental book state per instrument: bids/asks by price. */
  private final Map<Instrument, BookState> bookStates = new ConcurrentHashMap<>();

  public KrakenStreamingMarketDataService(KrakenStreamingService service) {
    this.service = service;
  }

  @Override
  public Observable<Ticker> getTicker(Instrument instrument, Object... args) {
    return service
        .subscribeChannel(ChannelType.TICKER.getValue(), instrument)
        .map(KrakenTickerMessage.class::cast)
        .map(KrakenDataMessage::getPayload)
        .map(KrakenStreamingAdapters::toTicker);
  }

  @Override
  public Observable<Ticker> getTicker(CurrencyPair currencyPair, Object... args) {
    return getTicker((Instrument) currencyPair, args);
  }

  @Override
  public Observable<Trade> getTrades(Instrument instrument, Object... args) {
    return service
        .subscribeChannel(ChannelType.TRADE.getValue(), instrument)
        .map(KrakenTradeMessage.class::cast)
        .map(KrakenDataMessage::getPayload)
        .map(KrakenStreamingAdapters::toTrade);
  }

  @Override
  public Observable<Trade> getTrades(CurrencyPair currencyPair, Object... args) {
    return getTrades((Instrument) currencyPair, args);
  }

  /**
   * Incremental order book with checksum validation and gap recovery.
   *
   * <p>Snapshots replace the local book; updates apply per-level deltas (a zero quantity removes
   * the level). Every message carrying a checksum is validated against the top 10 levels per side;
   * on mismatch the local book is discarded, the channel is resubscribed to trigger a fresh
   * snapshot, and emissions resume only once the rebuilt book is consistent.
   */
  @Override
  public Observable<OrderBook> getOrderBook(Instrument instrument, Object... args) {
    BookState state = bookStates.computeIfAbsent(instrument, BookState::new);
    return service
        .subscribeChannel(ChannelType.BOOK.getValue(), instrument)
        .map(KrakenBookMessage.class::cast)
        .concatMap(message -> applyBookMessage(instrument, state, message))
        .map(ignored -> state.toOrderBook());
  }

  @Override
  public Observable<OrderBook> getOrderBook(CurrencyPair currencyPair, Object... args) {
    return getOrderBook((Instrument) currencyPair, args);
  }

  /**
   * Raw OHLC candles for an instrument.
   *
   * @return observable of candle payloads (open/high/low/close/volume per interval)
   */
  public Observable<KrakenOhlcLevel> getOHLC(Instrument instrument, Object... args) {
    return service
        .subscribeChannel(ChannelType.OHLC.getValue(), instrument)
        .map(KrakenOhlcMessage.class::cast)
        .map(KrakenDataMessage::getPayload);
  }

  /**
   * System status messages broadcast on the socket (online, cancel_only, maintenance, post_only).
   *
   * @return observable of status payloads
   */
  public Observable<KrakenStatusMessage.Payload> getSystemStatus() {
    return service
        .subscribeChannel(ChannelType.STATUS.getValue())
        .map(KrakenStatusMessage.class::cast)
        .map(KrakenDataMessage::getPayload);
  }

  private Observable<Boolean> applyBookMessage(
      Instrument instrument, BookState state, KrakenBookMessage message) {
    KrakenBookLevels payload = message.getPayload();
    if (payload == null || payload.getBids() == null || payload.getAsks() == null) {
      return Observable.empty();
    }
    if (message.getType() == KrakenMessageType.SNAPSHOT) {
      state.applySnapshot(payload);
      if (!state.validateChecksum(payload.getChecksum(), instrument)) {
        state.reset();
        resubscribeBook(instrument);
        return Observable.empty();
      }
      return Observable.just(true);
    }
    state.applyUpdate(payload);
    if (!state.validateChecksum(payload.getChecksum(), instrument)) {
      // gap: drop the inconsistent book and force a fresh snapshot on the same subscription
      state.reset();
      resubscribeBook(instrument);
      return Observable.empty();
    }
    return Observable.just(true);
  }

  private void resubscribeBook(Instrument instrument) {
    log.warn("Order book checksum mismatch for {}, resubscribing for a fresh snapshot", instrument);
    service.resubscribeChannel(ChannelType.BOOK.getValue(), instrument);
  }

  /** Per-instrument incremental book state. */
  static final class BookState {

    private final Instrument instrument;
    private final Map<BigDecimal, BigDecimal> bids =
        java.util.Collections.synchronizedSortedMap(
            new TreeMap<>(java.util.Collections.reverseOrder()));
    private final Map<BigDecimal, BigDecimal> asks =
        java.util.Collections.synchronizedSortedMap(new TreeMap<>());

    BookState(Instrument instrument) {
      this.instrument = instrument;
    }

    synchronized void applySnapshot(KrakenBookLevels payload) {
      bids.clear();
      asks.clear();
      applyLevels(bids, payload.getBids());
      applyLevels(asks, payload.getAsks());
    }

    synchronized void applyUpdate(KrakenBookLevels payload) {
      applyLevels(bids, payload.getBids());
      applyLevels(asks, payload.getAsks());
    }

    private static BigDecimal toBigDecimal(String value) {
      return new BigDecimal(value);
    }

    private static void applyLevels(
        Map<BigDecimal, BigDecimal> levels, List<List<String>> rawLevels) {
      for (List<String> raw : rawLevels) {
        if (raw.size() < 2) {
          continue;
        }
        BigDecimal price = toBigDecimal(raw.get(0));
        BigDecimal quantity = toBigDecimal(raw.get(1));
        if (quantity.signum() == 0) {
          levels.remove(price);
        } else {
          levels.put(price, quantity);
        }
      }
    }

    synchronized boolean validateChecksum(Long expected, Instrument forInstrument) {
      if (expected == null) {
        return true; // no checksum in this message; nothing to validate
      }
      long actual = KrakenStreamingAdapters.checksum(bids, asks);
      if (actual != expected) {
        log.error(
            "Order book checksum mismatch for {}: expected {} but computed {}",
            forInstrument,
            expected,
            actual);
        return false;
      }
      return true;
    }

    synchronized void reset() {
      bids.clear();
      asks.clear();
    }

    synchronized OrderBook toOrderBook() {
      Date timestamp = new Date();
      // XChange OrderBook constructor expects asks before bids
      return new OrderBook(
          timestamp,
          toLimitOrders(asks, org.knowm.xchange.dto.Order.OrderType.ASK),
          toLimitOrders(bids, org.knowm.xchange.dto.Order.OrderType.BID));
    }

    private List<LimitOrder> toLimitOrders(
        Map<BigDecimal, BigDecimal> levels, org.knowm.xchange.dto.Order.OrderType type) {
      return levels.entrySet().stream()
          .map(
              entry ->
                  new LimitOrder.Builder(type, instrument)
                      .limitPrice(entry.getKey())
                      .originalAmount(entry.getValue())
                      .build())
          .collect(Collectors.toList());
    }
  }
}
