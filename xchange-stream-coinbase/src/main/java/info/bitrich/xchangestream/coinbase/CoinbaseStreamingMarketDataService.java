package info.bitrich.xchangestream.coinbase;

import static info.bitrich.xchangestream.coinbase.adapters.CoinbaseStreamingAdapters.adaptTickers;
import static info.bitrich.xchangestream.coinbase.adapters.CoinbaseStreamingAdapters.adaptTrades;
import static info.bitrich.xchangestream.coinbase.adapters.CoinbaseStreamingAdapters.parseOrderSide;

import com.fasterxml.jackson.databind.JsonNode;
import info.bitrich.xchangestream.coinbase.adapters.CoinbaseStreamingAdapters;
import info.bitrich.xchangestream.core.StreamingMarketDataService;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trade;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.instrument.Instrument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class CoinbaseStreamingMarketDataService implements StreamingMarketDataService {

  private static final Logger LOG =
      LoggerFactory.getLogger(CoinbaseStreamingMarketDataService.class);

  @FunctionalInterface
  interface OrderBookSnapshotProvider {
    OrderBook fetchSnapshot(CurrencyPair currencyPair) throws IOException;
  }

  private final CoinbaseStreamingService streamingService;
  private final OrderBookSnapshotProvider snapshotProvider;
  private final Map<CurrencyPair, OrderBookState> orderBooks = new ConcurrentHashMap<>();

  private final List<Disposable> internalSubscriptions = new CopyOnWriteArrayList<>();

  CoinbaseStreamingMarketDataService(
      CoinbaseStreamingService streamingService,
      OrderBookSnapshotProvider snapshotProvider,
      ExchangeSpecification spec) {
    this.streamingService = streamingService;
    this.snapshotProvider =
        snapshotProvider != null ? snapshotProvider : pair -> null;
  }

  void ensureHeartbeatsSubscription() {
    CoinbaseSubscriptionRequest request =
        new CoinbaseSubscriptionRequest(CoinbaseChannel.HEARTBEATS, Collections.emptyList(), Collections.emptyMap());
    Disposable disposable =
        streamingService
            .observeChannel(request)
            .subscribe(
                msg -> {},
                error ->
                    LOG.debug("Heartbeat subscription emitted error: {}", error.getMessage()));
    internalSubscriptions.add(disposable);
  }

  void resubscribe() {
    orderBooks.values().forEach(OrderBookState::reset);
  }

  @Override
  public Observable<Ticker> getTicker(CurrencyPair currencyPair, Object... args) {
    CoinbaseSubscriptionRequest request =
        new CoinbaseSubscriptionRequest(
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
    CoinbaseSubscriptionRequest request =
        new CoinbaseSubscriptionRequest(
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

  @Override
  public Observable<OrderBook> getOrderBook(CurrencyPair currencyPair, Object... args) {
    CoinbaseSubscriptionRequest request =
        new CoinbaseSubscriptionRequest(
            CoinbaseChannel.LEVEL2,
            Collections.singletonList(CoinbaseProductIds.productId(currencyPair)),
            Collections.emptyMap());

    OrderBookState state =
        orderBooks.computeIfAbsent(
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

  private static boolean instrumentMatches(Object instrument, CurrencyPair expected) {
    if (instrument instanceof CurrencyPair) {
      return instrument.equals(expected);
    }
    return false;
  }

  static final class OrderBookState {
    private final CurrencyPair currencyPair;
    private final OrderBookSnapshotProvider snapshotProvider;
    private final Map<BigDecimal, LimitOrder> bids =
        new ConcurrentHashMap<>();
    private final Map<BigDecimal, LimitOrder> asks =
        new ConcurrentHashMap<>();
    private Long lastSequence;
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
            lastSequence = sequence;
          }
          hasSnapshot = true;
          changed = true;
          continue;
        }
        if (!ensureInitialized(sequence)) {
          continue;
        }
        if (sequence > 0 && lastSequence != null) {
          long expected = lastSequence + 1;
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
          } else if (sequence <= lastSequence) {
            LOG.debug(
                "Skipping stale Coinbase level2 update for {} with sequence {} (last seen {})",
                currencyPair,
                sequence,
                lastSequence);
            continue;
          }
        }

        boolean applied = applyUpdates(event);
        if (applied) {
          changed = true;
        }
        if (sequence > 0) {
          lastSequence = sequence;
        }
      }
      if (!changed) {
        return Maybe.empty();
      }
      OrderBook orderBook =
          new OrderBook(
              null,
              sortedOrders(asks, Order.OrderType.ASK),
              sortedOrders(bids, Order.OrderType.BID));
      return Maybe.just(orderBook);
    }

    private void applySnapshotEvent(JsonNode event) {
      bids.clear();
      asks.clear();
      applyUpdatesToSide(
          bids, CoinbaseStreamingAdapters.adaptLevel2Updates(event, Order.OrderType.BID));
      applyUpdatesToSide(
          asks, CoinbaseStreamingAdapters.adaptLevel2Updates(event, Order.OrderType.ASK));
    }

    private boolean applyUpdates(JsonNode event) {
      boolean changed = false;
      List<LimitOrder> bidUpdates =
          CoinbaseStreamingAdapters.adaptLevel2Updates(event, Order.OrderType.BID);
      if (!bidUpdates.isEmpty()) {
        if (applyUpdatesToSide(bids, bidUpdates)) {
          changed = true;
        }
      }

      List<LimitOrder> askUpdates =
          CoinbaseStreamingAdapters.adaptLevel2Updates(event, Order.OrderType.ASK);
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
          lastSequence = nextSequence - 1;
        } else {
          lastSequence = null;
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
      lastSequence = null;
      hasSnapshot = false;
    }
  }
}


