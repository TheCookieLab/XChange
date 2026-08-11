package info.bitrich.xchangestream.kucoin;

import com.fasterxml.jackson.databind.JsonNode;
import info.bitrich.xchangestream.core.StreamingMarketDataService;
import info.bitrich.xchangestream.kucoin.dto.uta.UtaWsFrame;
import info.bitrich.xchangestream.service.netty.StreamingObjectMapperHelper;
import io.reactivex.rxjava3.core.Observable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trade;
import org.knowm.xchange.dto.marketdata.Trades;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.kucoin.KucoinExchange;
import org.knowm.xchange.kucoin.uta.UtaAdapters;
import org.knowm.xchange.kucoin.uta.UtaTradeTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * UTA streaming market data: ticker, sequence-safe order book ({@code obu} with
 * {@code increment@10ms}), and public trades.
 *
 * <p>Order-book continuity follows the documented calibration procedure: snapshots are pushed
 * first, deltas are applied under the {@code O/C} continuity rule, stale/duplicate deltas are
 * dropped, and a sequence gap resets the assembler and triggers a channel resubscription so the
 * rebuild always starts from a fresh authoritative snapshot. Frames arriving from a stale
 * connection generation are discarded.
 */
public class UtaStreamingMarketDataService implements StreamingMarketDataService {

  private static final Logger LOG = LoggerFactory.getLogger(UtaStreamingMarketDataService.class);

  private static final String DEPTH_INCREMENT_10MS = "increment@10ms";

  private final com.fasterxml.jackson.databind.ObjectMapper mapper =
      StreamingObjectMapperHelper.getObjectMapper();
  private final UtaStreamingService service;
  private final KucoinExchange exchange;
  private final Map<String, OrderBookSubscription> orderBookSubscriptions =
      new ConcurrentHashMap<>();

  public UtaStreamingMarketDataService(UtaStreamingService service, KucoinExchange exchange) {
    this.service = service;
    this.exchange = exchange;
  }

  @Override
  public Observable<Ticker> getTicker(CurrencyPair currencyPair, Object... args) {
    return getUtaTicker(currencyPair);
  }

  public Observable<Ticker> getUtaTicker(Instrument instrument) {
    String tradeType = UtaTradeTypes.of(instrument);
    String symbol = exchange.getUtaProviderSymbol(instrument);
    String channelName = "ticker";
    return service
        .subscribeChannel(channelName, tradeType, symbol)
        .map(node -> mapper.treeToValue(node, UtaWsFrame.class))
        .map(
            frame -> {
              UtaWsFrame.TickerData d = mapper.treeToValue(frame.getD(), UtaWsFrame.TickerData.class);
              return new Ticker.Builder()
                  .instrument(instrument)
                  .bid(d.getB())
                  .bidSize(d.getB())
                  .ask(d.getA())
                  .askSize(d.getA())
                  .last(d.getL())
                  .timestamp(new Date(nanosToMillis(frame.getP())))
                  .build();
            });
  }

  @Override
  public Observable<OrderBook> getOrderBook(CurrencyPair currencyPair, Object... args) {
    return getUtaOrderBook(currencyPair);
  }

  public Observable<OrderBook> getUtaOrderBook(Instrument instrument) {
    String tradeType = UtaTradeTypes.of(instrument);
    String symbol = exchange.getUtaProviderSymbol(instrument);
    String channelName = "obu";
    String uniqueId = service.getSubscriptionUniqueId(channelName, tradeType, symbol, DEPTH_INCREMENT_10MS);
    return orderBookSubscriptions
        .computeIfAbsent(uniqueId, id -> new OrderBookSubscription(instrument, tradeType, symbol))
        .stream;
  }

  @Override
  public Observable<Trade> getTrades(CurrencyPair currencyPair, Object... args) {
    return getUtaTrades(currencyPair).flatMapIterable(Trades::getTrades);
  }

  public Observable<Trades> getUtaTrades(Instrument instrument) {
    String tradeType = UtaTradeTypes.of(instrument);
    String symbol = exchange.getUtaProviderSymbol(instrument);
    return service
        .subscribeChannel("trade", tradeType, symbol)
        .map(node -> mapper.treeToValue(node, UtaWsFrame.class))
        .map(
            frame -> {
              JsonNode d = frame.getD();
              BigDecimal price = d.path("price").isNumber() || d.path("price").isTextual()
                  ? d.path("price").decimalValue() : null;
              BigDecimal size = d.path("size").isNumber() || d.path("size").isTextual()
                  ? d.path("size").decimalValue() : null;
              String side = d.path("side").asText();
              Trade trade =
                  Trade.builder()
                      .instrument(instrument)
                      .price(price)
                      .originalAmount(size)
                      .id(d.path("tradeId").asText())
                      .timestamp(new Date(nanosToMillis(d.path("ts").asLong())))
                      .type("BUY".equalsIgnoreCase(side)
                          ? org.knowm.xchange.dto.Order.OrderType.BID
                          : org.knowm.xchange.dto.Order.OrderType.ASK)
                      .build();
              return new Trades(List.of(trade),
                  org.knowm.xchange.dto.marketdata.Trades.TradeSortType.SortByTimestamp);
            });
  }

  private static long nanosToMillis(Long nanos) {
    return nanos == null ? System.currentTimeMillis() : nanos / 1_000_000L;
  }

  private final class OrderBookSubscription {
    final Instrument instrument;
    final String tradeType;
    final String symbol;
    final String uniqueId;
    final UtaOrderBookAssembler assembler = new UtaOrderBookAssembler();
    final AtomicReference<Long> subscriptionGeneration = new AtomicReference<>(null);
    final Observable<OrderBook> stream;

    OrderBookSubscription(Instrument instrument, String tradeType, String symbol) {
      this.instrument = instrument;
      this.tradeType = tradeType;
      this.symbol = symbol;
      this.uniqueId = service.getSubscriptionUniqueId("obu", tradeType, symbol, DEPTH_INCREMENT_10MS);
      this.stream =
          service
              .subscribeChannel("obu", tradeType, symbol, DEPTH_INCREMENT_10MS)
              .doOnNext(node -> onFrame())
              .map(
                  node -> {
                    UtaWsFrame frame = mapper.treeToValue(node, UtaWsFrame.class);
                    UtaWsFrame.OrderBookData d =
                        mapper.treeToValue(frame.getD(), UtaWsFrame.OrderBookData.class);
                    UtaOrderBookAssembler.Result result =
                        assembler.onUpdate(
                            frame.isSnapshot(),
                            d.getO() == null ? -1L : d.getO(),
                            d.getC() == null ? -1L : d.getC(),
                            d.getB(),
                            d.getA());
                    switch (result) {
                      case GAP:
                        LOG.warn(
                            "UTA order book sequence gap for {} (last={}); resubscribing for a fresh snapshot",
                            symbol,
                            assembler.getLastSequence());
                        service.resubscribeChannel("obu", tradeType, symbol, DEPTH_INCREMENT_10MS);
                        return null;
                      case APPLIED:
                        return assembler.toOrderBook(
                            instrument, new Date(nanosToMillis(frame.getP())));
                      case STALE_DROPPED:
                      case AWAITING_SNAPSHOT:
                      default:
                        return null;
                    }
                  })
              .filter(java.util.Objects::nonNull)
              .share();
    }

    private void onFrame() {
      long currentGeneration = service.getGeneration();
      Long observed = subscriptionGeneration.get();
      if (observed == null) {
        subscriptionGeneration.set(currentGeneration);
      } else if (observed != currentGeneration) {
        // Frames from a new connection: discard pre-reconnect state, await the fresh snapshot.
        LOG.info(
            "UTA order book connection generation changed for {} ({} -> {}); re-syncing",
            symbol,
            observed,
            currentGeneration);
        assembler.reset();
        subscriptionGeneration.set(currentGeneration);
      }
    }
  }
}
