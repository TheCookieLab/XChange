package info.bitrich.xchangestream.coinbasederivatives;

import com.fasterxml.jackson.databind.JsonNode;
import info.bitrich.xchangestream.core.StreamingTradeService;
import io.reactivex.rxjava3.core.Observable;
import java.util.Collections;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.derivative.FuturesContract;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.account.OpenPosition;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.instrument.Instrument;

/** Private order, fill, and position subscriptions for Coinbase derivatives accounts. */
public final class CoinbaseDerivativesStreamingTradeService implements StreamingTradeService {

  private final CoinbaseDerivativesStreamingService streamingService;

  public CoinbaseDerivativesStreamingTradeService(
      CoinbaseDerivativesStreamingService streamingService) {
    this.streamingService = streamingService;
  }

  @Override
  public Observable<Order> getOrderChanges(Instrument instrument, Object... args) {
    return userChanges(instrument)
        .flatMapIterable(data -> iterable(data.path("orders")))
        .map(CoinbaseDerivativesStreamingAdapters::toOrder);
  }

  @Override
  public Observable<Order> getOrderChanges(CurrencyPair currencyPair, Object... args) {
    return getOrderChanges(new FuturesContract(currencyPair, "PERPETUAL"), args);
  }

  @Override
  public Observable<UserTrade> getUserTrades(Instrument instrument, Object... args) {
    return userChanges(instrument)
        .flatMapIterable(data -> iterable(data.path("trades")))
        .map(CoinbaseDerivativesStreamingAdapters::toUserTrade);
  }

  @Override
  public Observable<UserTrade> getUserTrades(CurrencyPair currencyPair, Object... args) {
    return getUserTrades(new FuturesContract(currencyPair, "PERPETUAL"), args);
  }

  @Override
  public Observable<OpenPosition> getPositionChanges(Instrument instrument) {
    return userChanges(instrument)
        .flatMapIterable(data -> iterable(data.path("positions")))
        .map(CoinbaseDerivativesStreamingAdapters::toPosition);
  }

  private Observable<JsonNode> userChanges(Instrument instrument) {
    String nativeName = CoinbaseDerivativesStreamingAdapters.toNativeName(instrument);
    return streamingService
        .subscribePrivateChannel("user.changes." + nativeName + ".100ms")
        .map(CoinbaseDerivativesStreamingAdapters::data);
  }

  private Iterable<JsonNode> iterable(JsonNode value) {
    return value != null && value.isArray() ? value : Collections.emptyList();
  }
}
