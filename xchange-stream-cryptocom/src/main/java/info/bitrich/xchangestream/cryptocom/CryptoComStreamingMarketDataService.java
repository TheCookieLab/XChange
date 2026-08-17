package info.bitrich.xchangestream.cryptocom;

import info.bitrich.xchangestream.core.StreamingMarketDataService;
import info.bitrich.xchangestream.cryptocom.dto.CryptoComOrderBookContinuityException;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.knowm.xchange.cryptocom.CryptoComAdapters;
import org.knowm.xchange.cryptocom.dto.marketdata.CryptoComOrderBookData;
import org.knowm.xchange.cryptocom.dto.marketdata.CryptoComPublicTrade;
import org.knowm.xchange.cryptocom.dto.marketdata.CryptoComTicker;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trade;
import org.knowm.xchange.instrument.Instrument;

/**
 * Market data feed over the Crypto.com v1 public WebSocket.
 *
 * <p>{@link #getOrderBook(Instrument, Object...)} subscribes with the official snapshot-and-update
 * book contract ({@code book_subscription_type: SNAPSHOT_AND_UPDATE}) and feeds every dataframe
 * into a {@link CryptoComOrderBookAssembler}, so the emitted book is always the consistent result
 * of snapshot + ordered increments: increments are buffered until the opening snapshot, stale or
 * duplicate increment deliveries are rejected, and a broken sequence chain emits a dedicated
 * {@code CryptoComOrderBookContinuityException} on {@link
 * #getOrderBookContinuityFailures()} while the assembler rebuilds from the next full snapshot.
 */
public class CryptoComStreamingMarketDataService implements StreamingMarketDataService {

  private static final int DEFAULT_BOOK_DEPTH = 10;

  private final CryptoComStreamingService service;
  private final ConcurrentMap<String, CryptoComOrderBookAssembler> bookAssemblers =
      new ConcurrentHashMap<>();
  private final PublishSubject<CryptoComOrderBookContinuityException> continuityFailures =
      PublishSubject.create();

  public CryptoComStreamingMarketDataService(CryptoComStreamingService service) {
    this.service = service;
  }

  @Override
  public Observable<Ticker> getTicker(CurrencyPair currencyPair, Object... args) {
    return getTicker((Instrument) currencyPair, args);
  }

  @Override
  public Observable<Ticker> getTicker(Instrument instrument, Object... args) {
    String channel = "ticker." + CryptoComAdapters.toInstrumentName(instrument);
    return service
        .subscribeChannel(channel)
        .flatMapIterable(message -> service.extractData(message, CryptoComTicker.class))
        .map(CryptoComAdapters::adaptTicker);
  }

  @Override
  public Observable<OrderBook> getOrderBook(CurrencyPair currencyPair, Object... args) {
    return getOrderBook((Instrument) currencyPair, args);
  }

  @Override
  public Observable<OrderBook> getOrderBook(Instrument instrument, Object... args) {
    CurrencyPair pair = CryptoComAdapters.requireCurrencyPair(instrument, "getOrderBook");
    int depth =
        args != null && args.length > 0 && args[0] instanceof Integer
            ? (Integer) args[0]
            : DEFAULT_BOOK_DEPTH;
    String channel = "book." + CryptoComAdapters.toInstrumentName(instrument) + "." + depth;
    CryptoComOrderBookAssembler assembler =
        bookAssemblers.computeIfAbsent(
            channel,
            name -> {
              CryptoComOrderBookAssembler created = new CryptoComOrderBookAssembler(name, pair, depth);
              created
                  .continuityFailures()
                  .subscribe(
                      failure -> {
                        continuityFailures.onNext(failure);
                        // A broken sequence chain is healed by a fresh provider snapshot; ask
                        // for one on this exact channel instead of waiting for the next
                        // (unrelated) reconnect to re-subscribe it.
                        service.resubscribeChannel(name);
                      });
              // Each (re)connection starts a fresh sequence chain; the next full snapshot
              // rebuilds the book automatically because the framework re-subscribes the channel.
              service.subscribeDisconnect().subscribe(ignored -> created.markConnectionLost());
              return created;
            });
    return service
        .subscribeChannel(channel)
        .flatMapIterable(message -> service.extractData(message, CryptoComOrderBookData.class))
        .flatMapIterable(assembler::apply);
  }

  /**
   * Dedicated order-book continuity failures. When a book's snapshot/increment sequence chain
   * breaks (gap, incompatible previous-update id, or a snapshot never arrived within the bounded
   * buffer) a {@code CryptoComOrderBookContinuityException} is emitted here; the corresponding
   * book observable continues and rebuilds from the next full snapshot.
   */
  public Observable<CryptoComOrderBookContinuityException> getOrderBookContinuityFailures() {
    return continuityFailures;
  }

  @Override
  public Observable<Trade> getTrades(CurrencyPair currencyPair, Object... args) {
    return getTrades((Instrument) currencyPair, args);
  }

  @Override
  public Observable<Trade> getTrades(Instrument instrument, Object... args) {
    CurrencyPair pair = CryptoComAdapters.requireCurrencyPair(instrument, "getTrades");
    String channel = "trade." + CryptoComAdapters.toInstrumentName(instrument);
    return service
        .subscribeChannel(channel)
        .flatMapIterable(message -> service.extractData(message, CryptoComPublicTrade.class))
        .map(data -> CryptoComAdapters.adaptTrade(data, pair));
  }
}