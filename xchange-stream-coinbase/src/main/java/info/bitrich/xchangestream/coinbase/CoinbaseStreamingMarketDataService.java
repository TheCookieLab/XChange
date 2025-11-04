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
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
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

  private final CoinbaseStreamingService streamingService;
  private final Map<CurrencyPair, OrderBookState> orderBooks = new ConcurrentHashMap<>();

  private final List<Disposable> internalSubscriptions = new CopyOnWriteArrayList<>();

  CoinbaseStreamingMarketDataService(
      CoinbaseStreamingService streamingService, org.knowm.xchange.ExchangeSpecification spec) {
    this.streamingService = streamingService;
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
    // Observables returned from streamingService.observeChannel remain active across reconnects,
    // so no explicit re-subscription is required here.
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
        orderBooks.computeIfAbsent(currencyPair, key -> new OrderBookState(currencyPair));

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

  private static final class OrderBookState {
    private final CurrencyPair currencyPair;
    private final Map<BigDecimal, LimitOrder> bids =
        new ConcurrentHashMap<>();
    private final Map<BigDecimal, LimitOrder> asks =
        new ConcurrentHashMap<>();

    OrderBookState(CurrencyPair currencyPair) {
      this.currencyPair = currencyPair;
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
        if ("snapshot".equalsIgnoreCase(type)) {
          bids.clear();
          asks.clear();
        }
        List<LimitOrder> bidUpdates =
            CoinbaseStreamingAdapters.adaptLevel2Updates(event, Order.OrderType.BID);
        List<LimitOrder> askUpdates =
            CoinbaseStreamingAdapters.adaptLevel2Updates(event, Order.OrderType.ASK);
        if (!bidUpdates.isEmpty()) {
          applyUpdates(bids, bidUpdates);
          changed = true;
        }
        if (!askUpdates.isEmpty()) {
          applyUpdates(asks, askUpdates);
          changed = true;
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

    private void applyUpdates(Map<BigDecimal, LimitOrder> side, List<LimitOrder> updates) {
      for (LimitOrder update : updates) {
        BigDecimal price = update.getLimitPrice();
        BigDecimal size = update.getOriginalAmount();
        if (price == null || size == null) {
          continue;
        }
        if (size.compareTo(BigDecimal.ZERO) <= 0) {
          side.remove(price);
        } else {
          side.put(price, update);
        }
      }
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
  }
}


