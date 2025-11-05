package info.bitrich.xchangestream.coinbase;

import static info.bitrich.xchangestream.coinbase.adapters.CoinbaseStreamingAdapters.adaptCandles;

import com.fasterxml.jackson.databind.JsonNode;
import info.bitrich.xchangestream.coinbase.adapters.CoinbaseStreamingAdapters;
import info.bitrich.xchangestream.core.StreamingMarketDataService;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
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

  void resubscribe() {
    orderBooks.values().forEach(OrderBookState::reset);
  }

  @Override
  public Observable<Ticker> getTicker(CurrencyPair currencyPair, Object... args) {
    CoinbaseSubscriptionRequest request = new CoinbaseSubscriptionRequest(
        CoinbaseChannel.TICKER,
        Collections.singletonList(CoinbaseProductIds.productId(currencyPair)),
        Collections.emptyMap());

    return streamingService
        .observeChannel(request)
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

  @Override
  public Observable<OrderBook> getOrderBook(CurrencyPair currencyPair, Object... args) {
    CoinbaseSubscriptionRequest request = new CoinbaseSubscriptionRequest(
        CoinbaseChannel.LEVEL2,
        Collections.singletonList(CoinbaseProductIds.productId(currencyPair)),
        Collections.emptyMap());

    OrderBookState state = orderBooks.computeIfAbsent(
        currencyPair, key -> new OrderBookState(currencyPair, snapshotProvider));

    return streamingService
        .observeChannel(request)
        .flatMapMaybe(state::process);
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
    private final Map<BigDecimal, LimitOrder> bids = new ConcurrentHashMap<>();
    private final Map<BigDecimal, LimitOrder> asks = new ConcurrentHashMap<>();
    // Use AtomicLong to ensure thread-safe access to sequence number
    // This prevents race conditions when process() is called concurrently from
    // multiple subscribers or different schedulers
    private final AtomicLong lastSequence = new AtomicLong(-1);
    private volatile boolean hasSnapshot;

    OrderBookState(CurrencyPair currencyPair, OrderBookSnapshotProvider snapshotProvider) {
      this.currencyPair = currencyPair;
      this.snapshotProvider = snapshotProvider;
    }

    Maybe<OrderBook> process(JsonNode message) {
      JsonNode events = message.path("events");
      if (!events.isArray()) {
        return Maybe.empty();
      }
      boolean changed = false;
      for (JsonNode event : events) {
        String productId = event.path("product_id").asText(null);
        if (productId != null && !productId.equals(CoinbaseProductIds.productId(currencyPair))) {
          continue;
        }
        String type = event.path("type").asText("");
        long sequence = event.path("sequence").asLong(-1L);
        if ("snapshot".equalsIgnoreCase(type)) {
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
          sortedOrders(asks, Order.OrderType.ASK),
          sortedOrders(bids, Order.OrderType.BID));
      return Maybe.just(orderBook);
    }

    private void applySnapshotEvent(JsonNode event) {
      bids.clear();
      asks.clear();
      CurrencyPair pair = CoinbaseStreamingAdapters.toCurrencyPair(event.path("product_id").asText(null));
      if (pair == null) {
        return;
      }
      populateSnapshotSide(bids, event.path("bids"), Order.OrderType.BID, pair);
      populateSnapshotSide(asks, event.path("asks"), Order.OrderType.ASK, pair);
    }

    private void populateSnapshotSide(
        Map<BigDecimal, LimitOrder> side,
        JsonNode levels,
        Order.OrderType orderType,
        CurrencyPair pair) {
      if (!levels.isArray()) {
        return;
      }
      for (JsonNode level : levels) {
        if (!level.isArray() || level.size() < 2) {
          continue;
        }
        String priceText = level.get(0).asText(null);
        String sizeText = level.get(1).asText(null);
        if (priceText == null || sizeText == null) {
          continue;
        }
        try {
          BigDecimal price = new BigDecimal(priceText);
          BigDecimal size = new BigDecimal(sizeText);
          if (size.compareTo(BigDecimal.ZERO) > 0) {
            side.put(price, new LimitOrder(orderType, size, pair, null, null, price));
          } else {
            side.remove(price);
          }
        } catch (NumberFormatException ignore) {
          // skip malformed level
        }
      }
    }

    private boolean applyUpdates(JsonNode event) {
      boolean changed = false;
      List<LimitOrder> bidUpdates = CoinbaseStreamingAdapters.adaptLevel2Updates(event, Order.OrderType.BID);
      if (!bidUpdates.isEmpty()) {
        if (applyUpdatesToSide(bids, bidUpdates)) {
          changed = true;
        }
      }

      List<LimitOrder> askUpdates = CoinbaseStreamingAdapters.adaptLevel2Updates(event, Order.OrderType.ASK);
      if (!askUpdates.isEmpty()) {
        if (applyUpdatesToSide(asks, askUpdates)) {
          changed = true;
        }
      }
      return changed;
    }

    private boolean applyUpdatesToSide(Map<BigDecimal, LimitOrder> side, List<LimitOrder> updates) {
      boolean changed = false;
      for (LimitOrder update : updates) {
        BigDecimal price = update.getLimitPrice();
        BigDecimal size = update.getOriginalAmount();
        if (price == null || size == null) {
          continue;
        }
        if (size.compareTo(BigDecimal.ZERO) <= 0) {
          changed |= side.remove(price) != null;
        } else {
          side.put(price, update);
          changed = true;
        }
      }
      return changed;
    }

    private List<LimitOrder> sortedOrders(
        Map<BigDecimal, LimitOrder> side, Order.OrderType orderType) {
      return side.values().stream()
          .sorted(
              orderType == Order.OrderType.BID
                  ? (l, r) -> r.getLimitPrice().compareTo(l.getLimitPrice())
                  : (l, r) -> l.getLimitPrice().compareTo(r.getLimitPrice()))
          .collect(Collectors.toList());
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
            bids.put(order.getLimitPrice(), order);
          }
        });
        snapshot.getAsks().forEach(order -> {
          if (order.getLimitPrice() != null && order.getOriginalAmount() != null) {
            asks.put(order.getLimitPrice(), order);
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
