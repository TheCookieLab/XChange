package info.bitrich.xchangestream.coinbasederivatives;

import com.fasterxml.jackson.databind.JsonNode;
import info.bitrich.xchangestream.core.StreamingMarketDataService;
import io.reactivex.rxjava3.core.Observable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.derivative.FuturesContract;
import org.knowm.xchange.dto.marketdata.CandleStickData;
import org.knowm.xchange.dto.marketdata.CandleStickInterval;
import org.knowm.xchange.dto.marketdata.FundingRate;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.OrderBookUpdate;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trade;
import org.knowm.xchange.instrument.Instrument;

/** Public market-data subscriptions for the Coinbase derivatives gateway. */
public final class CoinbaseDerivativesStreamingMarketDataService
    implements StreamingMarketDataService {

  private final CoinbaseDerivativesStreamingService streamingService;
  private final Map<Instrument, Observable<OrderBook>> books = new ConcurrentHashMap<>();

  public CoinbaseDerivativesStreamingMarketDataService(
      CoinbaseDerivativesStreamingService streamingService) {
    this.streamingService = streamingService;
  }

  @Override
  public Observable<Ticker> getTicker(Instrument instrument, Object... args) {
    return channel("ticker", instrument)
        .map(CoinbaseDerivativesStreamingAdapters::data)
        .map(CoinbaseDerivativesStreamingAdapters::toTicker);
  }

  @Override
  public Observable<Ticker> getTicker(CurrencyPair currencyPair, Object... args) {
    return getTicker(new FuturesContract(currencyPair, "PERPETUAL"), args);
  }

  @Override
  public Observable<Trade> getTrades(Instrument instrument, Object... args) {
    return channel("trades", instrument)
        .map(CoinbaseDerivativesStreamingAdapters::data)
        .flatMapIterable(CoinbaseDerivativesStreamingAdapters::toTrades);
  }

  @Override
  public Observable<Trade> getTrades(CurrencyPair currencyPair, Object... args) {
    return getTrades(new FuturesContract(currencyPair, "PERPETUAL"), args);
  }

  @Override
  public Observable<List<OrderBookUpdate>> getOrderBookUpdates(
      Instrument instrument, Object... args) {
    return channel("book", instrument)
        .map(CoinbaseDerivativesStreamingAdapters::data)
        .map(CoinbaseDerivativesStreamingAdapters::toOrderBookUpdates);
  }

  @Override
  public Observable<OrderBook> getOrderBook(Instrument instrument, Object... args) {
    return books.computeIfAbsent(
        instrument,
        ignored ->
            channel("book", instrument)
                .map(CoinbaseDerivativesStreamingAdapters::data)
                .scan(new OrderBookState(), (state, data) -> state.apply(data))
                .skip(1)
                .map(OrderBookState::book)
                .doFinally(() -> books.remove(instrument))
                .replay(1)
                .refCount());
  }

  @Override
  public Observable<OrderBook> getOrderBook(CurrencyPair currencyPair, Object... args) {
    return getOrderBook(new FuturesContract(currencyPair, "PERPETUAL"), args);
  }

  @Override
  public Observable<FundingRate> getFundingRate(Instrument instrument, Object... args) {
    return channel("ticker", instrument)
        .map(CoinbaseDerivativesStreamingAdapters::data)
        .map(CoinbaseDerivativesStreamingAdapters::toFundingRate);
  }

  @Override
  public Observable<CandleStickData> getCandleStick(
      Instrument instrument, CandleStickInterval interval) {
    String nativeName = CoinbaseDerivativesStreamingAdapters.toNativeName(instrument);
    return streamingService
        .subscribePublicChannel("chart.trades." + nativeName + "." + chartResolution(interval))
        .map(CoinbaseDerivativesStreamingAdapters::data)
        .map(data -> CoinbaseDerivativesStreamingAdapters.toCandleStickData(instrument, data));
  }

  /** Exposes the exact provider ticker payload, including index and mark fields. */
  public Observable<JsonNode> getRawTicker(Instrument instrument) {
    return channel("ticker", instrument).map(CoinbaseDerivativesStreamingAdapters::data);
  }

  private Observable<JsonNode> channel(String type, Instrument instrument) {
    String nativeName = CoinbaseDerivativesStreamingAdapters.toNativeName(instrument);
    return streamingService.subscribePublicChannel(type + "." + nativeName + ".100ms");
  }

  private String chartResolution(CandleStickInterval interval) {
    switch (interval) {
      case m1:
        return "1";
      case m3:
        return "3";
      case m5:
        return "5";
      case m15:
        return "15";
      case m30:
        return "30";
      case h1:
        return "60";
      case h2:
        return "120";
      case h3:
        return "180";
      case h6:
        return "360";
      case h12:
        return "720";
      case d1:
        return "1D";
      default:
        throw new IllegalArgumentException(
            "Unsupported Coinbase derivatives chart interval " + interval);
    }
  }

  private static final class OrderBookState {
    private OrderBook book = new OrderBook(null, new ArrayList<>(), new ArrayList<>());

    private OrderBookState apply(JsonNode data) {
      List<OrderBookUpdate> updates = CoinbaseDerivativesStreamingAdapters.toOrderBookUpdates(data);
      if ("snapshot".equals(data.path("type").asText())) {
        book = new OrderBook(null, new ArrayList<>(), new ArrayList<>());
      }
      updates.forEach(book::update);
      return this;
    }

    private OrderBook book() {
      return book;
    }
  }
}
