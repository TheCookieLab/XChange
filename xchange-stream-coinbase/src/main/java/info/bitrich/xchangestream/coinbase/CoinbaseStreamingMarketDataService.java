package info.bitrich.xchangestream.coinbase;

import static info.bitrich.xchangestream.coinbase.adapters.CoinbaseStreamingAdapters.adaptCandles;

import com.fasterxml.jackson.databind.JsonNode;
import info.bitrich.xchangestream.coinbase.adapters.CoinbaseStreamingAdapters;
import info.bitrich.xchangestream.coinbase.dto.CoinbaseStreamingEvent;
import info.bitrich.xchangestream.coinbase.dto.CoinbaseStreamingMessage;
import info.bitrich.xchangestream.core.StreamingMarketDataService;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.marketdata.CandleStick;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trade;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.instrument.Instrument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CoinbaseStreamingMarketDataService implements StreamingMarketDataService {

  private static final Logger LOG = LoggerFactory.getLogger(CoinbaseStreamingMarketDataService.class);

  @FunctionalInterface
  interface OrderBookSnapshotProvider {
    OrderBook fetchSnapshot(CurrencyPair currencyPair) throws IOException;
  }

  private final CoinbaseStreamingService streamingService;
  private final OrderBookSnapshotProvider snapshotProvider;
  private final ExchangeSpecification exchangeSpecification;
  private final Map<CurrencyPair, OrderBookState> orderBooks = new ConcurrentHashMap<>();
  // Cache observables per currency pair to enable replay for new subscribers
  // Key format: "CURRENCY_PAIR:channel" to differentiate between level2 and level2_batch
  private final Map<String, Observable<OrderBook>> orderBookObservables = new ConcurrentHashMap<>();

  private final List<Disposable> internalSubscriptions = new CopyOnWriteArrayList<>();

  CoinbaseStreamingMarketDataService(
      CoinbaseStreamingService streamingService,
      OrderBookSnapshotProvider snapshotProvider,
      ExchangeSpecification spec) {
    this.streamingService = streamingService;
    this.snapshotProvider = snapshotProvider != null ? snapshotProvider : pair -> null;
    this.exchangeSpecification = spec;
  }

  void ensureHeartbeatsSubscription() {
    CoinbaseSubscriptionRequest request = new CoinbaseSubscriptionRequest(CoinbaseChannel.HEARTBEATS,
        Collections.emptyList(), Collections.emptyMap());
    Disposable disposable = streamingService
        .observeChannel(request)
        .subscribe(
            msg -> {
            },
            error -> LOG.debug("Heartbeat subscription emitted error: {}", error.getMessage()));
    internalSubscriptions.add(disposable);
  }

  /**
   * Get an observable for heartbeat messages. Heartbeats help keep the WebSocket connection alive.
   *
   * @return Observable that emits heartbeat messages
   */
  public Observable<JsonNode> getHeartbeats() {
    CoinbaseSubscriptionRequest request = new CoinbaseSubscriptionRequest(CoinbaseChannel.HEARTBEATS,
        Collections.emptyList(), Collections.emptyMap());
    return streamingService.observeChannel(request);
  }

  void resubscribe() {
    orderBooks.values().forEach(OrderBookState::reset);
    // Clear cached observables on resubscribe to ensure fresh state
    orderBookObservables.clear();
  }

  @Override
  public Observable<Ticker> getTicker(CurrencyPair currencyPair, Object... args) {
    CoinbaseSubscriptionRequest request = new CoinbaseSubscriptionRequest(
        CoinbaseChannel.TICKER,
        Collections.singletonList(CoinbaseProductIds.productId(currencyPair)),
        Collections.emptyMap());

    return streamingService
        .observeChannel(request)
        .map(CoinbaseStreamingAdapters::toStreamingMessage)
        .flatMapIterable(CoinbaseStreamingAdapters::adaptTickers)
        .filter(ticker -> instrumentMatches(ticker.getInstrument(), currencyPair));
  }

  @Override
  public Observable<Ticker> getTicker(Instrument instrument, Object... args) {
    if (instrument instanceof CurrencyPair) {
      return getTicker((CurrencyPair) instrument, args);
    }
    return StreamingMarketDataService.super.getTicker(instrument, args);
  }

  @Override
  public Observable<Trade> getTrades(CurrencyPair currencyPair, Object... args) {
    CoinbaseSubscriptionRequest request = new CoinbaseSubscriptionRequest(
        CoinbaseChannel.MARKET_TRADES,
        Collections.singletonList(CoinbaseProductIds.productId(currencyPair)),
        Collections.emptyMap());

    return streamingService
        .observeChannel(request)
        .map(CoinbaseStreamingAdapters::toStreamingMessage)
        .flatMapIterable(CoinbaseStreamingAdapters::adaptTrades)
        .filter(trade -> instrumentMatches(trade.getInstrument(), currencyPair));
  }

  @Override
  public Observable<Trade> getTrades(Instrument instrument, Object... args) {
    if (instrument instanceof CurrencyPair) {
      return getTrades((CurrencyPair) instrument, args);
    }
    return StreamingMarketDataService.super.getTrades(instrument, args);
  }

  public Observable<CandleStick> getCandles(CurrencyPair currencyPair, Object... args) {
    CoinbaseCandleSubscriptionParams params = resolveCandleParams(args);
    return subscribeCandles(currencyPair, params);
  }

  public Observable<CandleStick> getCandles(
      CurrencyPair currencyPair, CoinbaseCandleSubscriptionParams params) {
    CoinbaseCandleSubscriptionParams effective = params == null ? resolveCandleParams() : resolveCandleParams(params);
    return subscribeCandles(currencyPair, effective);
  }

  public Observable<CandleStick> getCandles(Instrument instrument, Object... args) {
    if (instrument instanceof CurrencyPair) {
      return getCandles((CurrencyPair) instrument, args);
    }
    throw new IllegalArgumentException("Coinbase candle subscriptions support currency pairs only");
  }

  /**
   * Get an order book representing the current offered exchange rates (market depth).
   *
   * <p>The returned Observable uses {@code replay(1).refCount()} to cache and replay the last
   * emitted OrderBook to new subscribers. This ensures that:
   * <ul>
   *   <li>New subscribers receive the latest state immediately upon subscription</li>
   *   <li>Multiple subscribers share the same underlying WebSocket subscription</li>
   *   <li>The observable is automatically cleaned up when all subscribers unsubscribe</li>
   * </ul>
   *
   * <p><strong>Best Practice:</strong> Subscribe to the order book after connecting to ensure you
   * receive the initial snapshot. If you use {@link ProductSubscription} with order books, you
   * still need to manually subscribe via this method to receive the data - the replay mechanism
   * ensures you'll get the latest state even if the snapshot was already processed.
   *
   * @param currencyPair Currency pair of the order book
   * @param args Optional arguments (not currently used)
   * @return Observable that emits OrderBook updates, replaying the last value to new subscribers
   */
  @Override
  public Observable<OrderBook> getOrderBook(CurrencyPair currencyPair, Object... args) {
    // Determine which channel to use from args
    CoinbaseChannel channel = determineChannel(args);
    
    // Use cached observable with replay to ensure new subscribers get the latest state
    // Include channel in cache key to differentiate between level2 and level2_batch
    final String cacheKey = currencyPair.toString() + ":" + channel.channelName();
    final CoinbaseChannel finalChannel = channel; // Make final for lambda
    
    return orderBookObservables.computeIfAbsent(cacheKey, key -> {
      CoinbaseSubscriptionRequest request = new CoinbaseSubscriptionRequest(
          finalChannel,
          Collections.singletonList(CoinbaseProductIds.productId(currencyPair)),
          Collections.emptyMap());

      OrderBookState state = orderBooks.computeIfAbsent(
          currencyPair, pair -> new OrderBookState(pair, snapshotProvider));

      LOG.info("Creating order book observable for {} on channel {}", currencyPair, finalChannel.channelName());
      return streamingService
          .observeChannel(request)
          .doOnNext(msg -> LOG.debug("Raw level2 message received for {}: {}", currencyPair, msg))
          .map(CoinbaseStreamingAdapters::toStreamingMessage)
          .doOnNext(msg -> {
            LOG.debug("Parsed level2 message for {}: {} events", currencyPair, 
                msg != null && msg.getEvents() != null ? msg.getEvents().size() : 0);
            if (msg != null && !msg.getEvents().isEmpty()) {
              for (CoinbaseStreamingEvent event : msg.getEvents()) {
                LOG.debug("Level2 event: type={}, productId={}, sequence={}, updates={}, bids={}, asks={}",
                    event.getType(), event.getProductId(), event.getSequence(),
                    event.getUpdates() != null ? event.getUpdates().size() : 0,
                    event.getBids() != null ? event.getBids().size() : 0,
                    event.getAsks() != null ? event.getAsks().size() : 0);
              }
            }
          })
          .flatMapMaybe(state::process)
          .doOnNext(ob -> LOG.info("OrderBook emitted for {}: {} bids, {} asks", 
              currencyPair, ob.getBids().size(), ob.getAsks().size()))
          .replay(1)
          .refCount();
    });
  }
  
  private CoinbaseChannel determineChannel(Object... args) {
    if (args.length > 0) {
      if (args[0] instanceof Boolean && (Boolean) args[0]) {
        return CoinbaseChannel.LEVEL2_BATCH;
      } else if (args[0] instanceof String) {
        String channelName = (String) args[0];
        try {
          return CoinbaseChannel.fromName(channelName);
        } catch (IllegalArgumentException e) {
          LOG.warn("Unknown channel name '{}', defaulting to level2", channelName);
          return CoinbaseChannel.LEVEL2;
        }
      }
    }
    return CoinbaseChannel.LEVEL2;
  }
  
  /**
   * Get an order book using the level2_batch channel, which batches updates every 0.05 seconds.
   * This is recommended for high-volume products like BTC-USD to avoid exceeding WebSocket
   * max_msg_size limits with large initial snapshots.
   *
   * @param currencyPair Currency pair of the order book
   * @return Observable that emits OrderBook updates from the batched channel
   */
  public Observable<OrderBook> getOrderBookBatch(CurrencyPair currencyPair) {
    return getOrderBook(currencyPair, true);
  }

  @Override
  public Observable<OrderBook> getOrderBook(Instrument instrument, Object... args) {
    if (instrument instanceof CurrencyPair) {
      return getOrderBook((CurrencyPair) instrument, args);
    }
    return StreamingMarketDataService.super.getOrderBook(instrument, args);
  }

  private Observable<CandleStick> subscribeCandles(
      CurrencyPair currencyPair, CoinbaseCandleSubscriptionParams params) {
    CoinbaseSubscriptionRequest request = new CoinbaseSubscriptionRequest(
        CoinbaseChannel.CANDLES,
        Collections.singletonList(CoinbaseProductIds.productId(currencyPair)),
        params.toChannelArgs());

    return streamingService
        .observeChannel(request)
        .map(CoinbaseStreamingAdapters::toStreamingMessage)
        .flatMapIterable(message -> adaptCandles(message, currencyPair));
  }

  private CoinbaseCandleSubscriptionParams resolveCandleParams(Object... args) {
    CoinbaseCandleSubscriptionParams params = null;
    if (args != null && args.length > 0 && args[0] != null) {
      params = convertToCandleParams(args[0]);
      if (params == null) {
        throw new IllegalArgumentException(
            "Unsupported Coinbase candle parameter: " + args[0]);
      }
    }
    if (params == null) {
      CoinbaseCandleGranularity defaultGranularity = defaultCandleGranularity();
      if (defaultGranularity == null) {
        throw new IllegalArgumentException(
            "Coinbase candle subscriptions require a granularity parameter");
      }
      params = new CoinbaseCandleSubscriptionParams(defaultGranularity);
    }
    if (params.getProductType() == null) {
      String productType = null;
      if (args != null && args.length > 1) {
        productType = parseProductType(args[1]);
        if (productType == null && args[1] != null) {
          throw new IllegalArgumentException(
              "Unsupported Coinbase candle product type parameter: " + args[1]);
        }
      }
      if (productType == null) {
        productType = defaultCandleProductType();
      }
      if (productType != null) {
        params = params.withProductType(productType);
      }
    }
    return params;
  }

  private CoinbaseCandleSubscriptionParams convertToCandleParams(Object value) {
    if (value instanceof CoinbaseCandleSubscriptionParams) {
      return (CoinbaseCandleSubscriptionParams) value;
    }
    CoinbaseCandleGranularity granularity = parseGranularity(value);
    if (granularity != null) {
      return new CoinbaseCandleSubscriptionParams(granularity);
    }
    return null;
  }

  private CoinbaseCandleGranularity parseGranularity(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof CoinbaseCandleSubscriptionParams) {
      return ((CoinbaseCandleSubscriptionParams) value).getGranularity();
    }
    if (value instanceof CoinbaseCandleGranularity) {
      return (CoinbaseCandleGranularity) value;
    }
    if (value instanceof Duration) {
      return CoinbaseCandleGranularity.fromDuration((Duration) value);
    }
    if (value instanceof Number) {
      return CoinbaseCandleGranularity.fromSeconds(((Number) value).longValue());
    }
    if (value instanceof String) {
      return parseGranularityFromString((String) value);
    }
    return null;
  }

  private CoinbaseCandleGranularity parseGranularityFromString(String value) {
    if (value == null) {
      return null;
    }
    String normalized = value.trim();
    if (normalized.isEmpty()) {
      return null;
    }
    for (CoinbaseCandleGranularity option : CoinbaseCandleGranularity.values()) {
      if (option.name().equalsIgnoreCase(normalized)
          || option.apiValue().equalsIgnoreCase(normalized)) {
        return option;
      }
    }
    try {
      long seconds = Long.parseLong(normalized);
      return CoinbaseCandleGranularity.fromSeconds(seconds);
    } catch (NumberFormatException ignore) {
      return null;
    }
  }

  private String parseProductType(Object value) {
    if (value == null) {
      return null;
    }
    String text = value.toString().trim();
    return text.isEmpty() ? null : text;
  }

  private CoinbaseCandleGranularity defaultCandleGranularity() {
    Object raw = exchangeSpecification == null
        ? null
        : exchangeSpecification.getExchangeSpecificParametersItem(
            CoinbaseStreamingExchange.PARAM_DEFAULT_CANDLE_GRANULARITY);
    return parseGranularity(raw);
  }

  private String defaultCandleProductType() {
    if (exchangeSpecification == null) {
      return null;
    }
    Object raw = exchangeSpecification.getExchangeSpecificParametersItem(
        CoinbaseStreamingExchange.PARAM_DEFAULT_CANDLE_PRODUCT_TYPE);
    return parseProductType(raw);
  }

  private static boolean instrumentMatches(Object instrument, CurrencyPair expected) {
    if (instrument instanceof CurrencyPair) {
      return instrument.equals(expected);
    }
    return false;
  }

  static final class OrderBookState {
    private final CurrencyPair currencyPair;
    private final OrderBookSnapshotProvider snapshotProvider;
    private final NavigableMap<BigDecimal, LimitOrder> bids =
        new ConcurrentSkipListMap<>(Collections.reverseOrder());
    private final NavigableMap<BigDecimal, LimitOrder> asks = new ConcurrentSkipListMap<>();
    // Use AtomicLong to ensure thread-safe access to sequence number
    // This prevents race conditions when process() is called concurrently from
    // multiple subscribers or different schedulers
    private final AtomicLong lastSequence = new AtomicLong(-1);
    private volatile boolean hasSnapshot;

    OrderBookState(CurrencyPair currencyPair, OrderBookSnapshotProvider snapshotProvider) {
      this.currencyPair = currencyPair;
      this.snapshotProvider = snapshotProvider;
    }

    /**
     * Normalizes a BigDecimal price to ensure consistent map key behavior.
     * This removes trailing zeros and canonicalizes the scale so that
     * identical numeric values with different scales (e.g., 1.0 vs 1.00) are
     * treated as the same key.
     */
    private static BigDecimal normalizePrice(BigDecimal price) {
      if (price == null) {
        return null;
      }
      return price.stripTrailingZeros();
    }

    Maybe<OrderBook> process(CoinbaseStreamingMessage message) {
      if (message == null || message.getEvents().isEmpty()) {
        return Maybe.empty();
      }
      boolean changed = false;
      for (CoinbaseStreamingEvent event : message.getEvents()) {
        String productId = event.getProductId();
        if (productId != null && !productId.equals(CoinbaseProductIds.productId(currencyPair))) {
          continue;
        }
        String type = event.getType() == null ? "" : event.getType();
        long sequence = event.getSequence() == null ? -1L : event.getSequence();
        LOG.debug("Processing level2 event: type={}, productId={}, sequence={}, updates={}, bids={}, asks={}", 
            type, productId, sequence, 
            event.getUpdates() != null ? event.getUpdates().size() : 0,
            event.getBids() != null ? event.getBids().size() : 0,
            event.getAsks() != null ? event.getAsks().size() : 0);
        if ("snapshot".equalsIgnoreCase(type)) {
          LOG.info("Processing level2 snapshot for {}: updates={}, bids={}, asks={}", 
              currencyPair,
              event.getUpdates() != null ? event.getUpdates().size() : 0,
              event.getBids() != null ? event.getBids().size() : 0,
              event.getAsks() != null ? event.getAsks().size() : 0);
          applySnapshotEvent(event);
          if (sequence >= 0) {
            lastSequence.set(sequence);
          }
          hasSnapshot = true;
          changed = true;
          continue;
        }
        if (!ensureInitialized(sequence)) {
          continue;
        }
        long currentLastSequence = lastSequence.get();
        if (sequence > 0 && currentLastSequence >= 0) {
          long expected = currentLastSequence + 1;
          if (sequence > expected) {
            LOG.warn(
                "Detected Coinbase level2 sequence gap for {}: expected {} but received {}",
                currencyPair,
                expected,
                sequence);
            if (recoverFromSnapshot(sequence)) {
              changed = true;
            } else {
              continue;
            }
          } else if (sequence <= currentLastSequence) {
            LOG.debug(
                "Skipping stale Coinbase level2 update for {} with sequence {} (last seen {})",
                currencyPair,
                sequence,
                currentLastSequence);
            continue;
          }
        }

        boolean applied = applyUpdates(event);
        if (applied) {
          changed = true;
        }
        if (sequence > 0) {
          // Atomically update lastSequence only if the new sequence is greater
          // This prevents race conditions where concurrent updates might overwrite
          // a higher sequence with a lower one
          lastSequence.updateAndGet(current -> sequence > current ? sequence : current);
        }
      }
      if (!changed) {
        return Maybe.empty();
      }
      OrderBook orderBook = new OrderBook(
          null,
          new ArrayList<>(asks.values()),
          new ArrayList<>(bids.values()));
      return Maybe.just(orderBook);
    }

    private void applySnapshotEvent(CoinbaseStreamingEvent event) {
      bids.clear();
      asks.clear();
      CurrencyPair pair = CoinbaseStreamingAdapters.toCurrencyPair(event.getProductId());
      if (pair == null) {
        return;
      }
      // Coinbase level2 snapshots can have either:
      // 1. bids/asks arrays (List<List<String>>) - traditional format
      // 2. updates array with price_level/new_quantity - newer format
      // Handle both formats
      if (!event.getUpdates().isEmpty()) {
        // Snapshot uses updates array format
        LOG.debug("Applying snapshot from updates array ({} updates)", event.getUpdates().size());
        List<LimitOrder> bidUpdates = CoinbaseStreamingAdapters.adaptLevel2Updates(event, Order.OrderType.BID);
        List<LimitOrder> askUpdates = CoinbaseStreamingAdapters.adaptLevel2Updates(event, Order.OrderType.ASK);
        for (LimitOrder order : bidUpdates) {
          BigDecimal normalizedPrice = normalizePrice(order.getLimitPrice());
          if (order.getOriginalAmount().compareTo(BigDecimal.ZERO) > 0) {
            bids.put(normalizedPrice, order);
          } else {
            bids.remove(normalizedPrice);
          }
        }
        for (LimitOrder order : askUpdates) {
          BigDecimal normalizedPrice = normalizePrice(order.getLimitPrice());
          if (order.getOriginalAmount().compareTo(BigDecimal.ZERO) > 0) {
            asks.put(normalizedPrice, order);
          } else {
            asks.remove(normalizedPrice);
          }
        }
      } else {
        // Snapshot uses bids/asks arrays format
        LOG.debug("Applying snapshot from bids/asks arrays ({} bids, {} asks)", 
            event.getBids() != null ? event.getBids().size() : 0,
            event.getAsks() != null ? event.getAsks().size() : 0);
        populateSnapshotSide(bids, event.getBids(), Order.OrderType.BID, pair);
        populateSnapshotSide(asks, event.getAsks(), Order.OrderType.ASK, pair);
      }
    }

    private void populateSnapshotSide(
        NavigableMap<BigDecimal, LimitOrder> side,
        List<List<String>> levels,
        Order.OrderType orderType,
        CurrencyPair pair) {
      if (levels == null || levels.isEmpty()) {
        return;
      }
      for (List<String> level : levels) {
        if (level == null || level.size() < 2) {
          continue;
        }
        String priceText = level.get(0);
        String sizeText = level.get(1);
        if (priceText == null || sizeText == null) {
          continue;
        }
        try {
          BigDecimal price = new BigDecimal(priceText);
          BigDecimal size = new BigDecimal(sizeText);
          BigDecimal normalizedPrice = normalizePrice(price);
          if (size.compareTo(BigDecimal.ZERO) > 0) {
            side.put(normalizedPrice, new LimitOrder(orderType, size, pair, null, null, price));
          } else {
            side.remove(normalizedPrice);
          }
        } catch (NumberFormatException ignore) {
          // skip malformed level
        }
      }
    }

    private boolean applyUpdates(CoinbaseStreamingEvent event) {
      boolean changed = false;
      LOG.debug("Applying level2 updates: {} updates total", 
          event.getUpdates() != null ? event.getUpdates().size() : 0);
      List<LimitOrder> bidUpdates =
          CoinbaseStreamingAdapters.adaptLevel2Updates(event, Order.OrderType.BID);
      if (!bidUpdates.isEmpty()) {
        LOG.debug("Applying {} bid updates", bidUpdates.size());
        if (applyUpdatesToSide(bids, bidUpdates)) {
          changed = true;
        }
      }
      List<LimitOrder> askUpdates =
          CoinbaseStreamingAdapters.adaptLevel2Updates(event, Order.OrderType.ASK);
      if (!askUpdates.isEmpty()) {
        LOG.debug("Applying {} ask updates", askUpdates.size());
        if (applyUpdatesToSide(asks, askUpdates)) {
          changed = true;
        }
      }
      if (!changed && !event.getUpdates().isEmpty()) {
        LOG.warn("Received {} updates but none were applied (possibly filtered out)", event.getUpdates().size());
      }
      return changed;
    }

    private boolean applyUpdatesToSide(
        NavigableMap<BigDecimal, LimitOrder> side, List<LimitOrder> updates) {
      boolean changed = false;
      for (LimitOrder update : updates) {
        BigDecimal price = update.getLimitPrice();
        BigDecimal size = update.getOriginalAmount();
        if (price == null || size == null) {
          continue;
        }
        BigDecimal normalizedPrice = normalizePrice(price);
        if (size.compareTo(BigDecimal.ZERO) <= 0) {
          changed |= side.remove(normalizedPrice) != null;
        } else {
          side.put(normalizedPrice, update);
          changed = true;
        }
      }
      return changed;
    }

    private boolean ensureInitialized(long nextSequence) {
      if (hasSnapshot) {
        return true;
      }
      return recoverFromSnapshot(nextSequence);
    }

    private boolean recoverFromSnapshot(long nextSequence) {
      if (snapshotProvider == null) {
        LOG.warn(
            "No snapshot provider configured for {} - unable to recover Coinbase order book",
            currencyPair);
        return false;
      }
      try {
        OrderBook snapshot = snapshotProvider.fetchSnapshot(currencyPair);
        if (snapshot == null) {
          LOG.warn(
              "Snapshot provider returned null while recovering Coinbase order book for {}",
              currencyPair);
          return false;
        }
        bids.clear();
        asks.clear();
        snapshot.getBids().forEach(order -> {
          if (order.getLimitPrice() != null && order.getOriginalAmount() != null) {
            BigDecimal normalizedPrice = normalizePrice(order.getLimitPrice());
            bids.put(normalizedPrice, order);
          }
        });
        snapshot.getAsks().forEach(order -> {
          if (order.getLimitPrice() != null && order.getOriginalAmount() != null) {
            BigDecimal normalizedPrice = normalizePrice(order.getLimitPrice());
            asks.put(normalizedPrice, order);
          }
        });
        hasSnapshot = true;
        if (nextSequence > 0) {
          lastSequence.set(nextSequence - 1);
        } else {
          lastSequence.set(-1);
        }
        LOG.debug("Recovered Coinbase order book snapshot for {}", currencyPair);
        return true;
      } catch (IOException e) {
        LOG.warn("Failed to fetch Coinbase order book snapshot for {}", currencyPair, e);
        return false;
      }
    }

    void reset() {
      bids.clear();
      asks.clear();
      lastSequence.set(-1);
      hasSnapshot = false;
    }
  }
}
