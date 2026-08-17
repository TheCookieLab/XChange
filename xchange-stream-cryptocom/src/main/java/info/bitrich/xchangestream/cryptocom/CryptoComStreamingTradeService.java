package info.bitrich.xchangestream.cryptocom;

import info.bitrich.xchangestream.core.StreamingTradeService;
import info.bitrich.xchangestream.cryptocom.dto.CryptoComUserTradeUpdate;
import io.reactivex.rxjava3.core.Observable;
import org.knowm.xchange.cryptocom.CryptoComAdapters;
import org.knowm.xchange.cryptocom.dto.trade.CryptoComOrder;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.instrument.Instrument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CryptoComStreamingTradeService implements StreamingTradeService {

  private static final Logger LOG = LoggerFactory.getLogger(CryptoComStreamingTradeService.class);

  private final CryptoComPrivateStreamingService service;
  private final CryptoComStreamingEventDeduplicator deduplicator =
      new CryptoComStreamingEventDeduplicator();

  public CryptoComStreamingTradeService(CryptoComPrivateStreamingService service) {
    this.service = service;
  }

  @Override
  public Observable<Order> getOrderChanges(CurrencyPair currencyPair, Object... args) {
    return getOrderChanges((Instrument) currencyPair, args);
  }

  @Override
  public Observable<Order> getOrderChanges(Instrument instrument, Object... args) {
    String channel = "user.order." + CryptoComAdapters.toInstrumentName(instrument);
    return service
        .subscribeChannel(channel)
        .flatMapIterable(message -> service.extractData(message, CryptoComOrder.class))
        .filter(order -> !isDuplicateOrder(deduplicator, channel, order))
        .map(CryptoComAdapters::adaptOrder);
  }

  /**
   * {@code true} when the order event is a replay of one already delivered: the stable identity
   * is {@code order_id + update_time}; without it, an exact payload repeat is treated as a
   * duplicate. Package-private for deterministic unit tests.
   */
  static boolean isDuplicateOrder(
      CryptoComStreamingEventDeduplicator deduplicator, String channel, CryptoComOrder order) {
    String key;
    if (order.getOrderId() != null && order.getUpdateTime() != null) {
      key = channel + "." + order.getOrderId() + "." + order.getUpdateTime();
    } else {
      // No stable identity: treat the exact replay of the same payload as a duplicate.
      key = channel + "." + order;
    }
    boolean duplicate = deduplicator.isDuplicate(key);
    if (duplicate) {
      LOG.debug("Dropping replayed order event {} on reconnect", key);
    }
    return duplicate;
  }

  @Override
  public Observable<UserTrade> getUserTrades(CurrencyPair currencyPair, Object... args) {
    return getUserTrades((Instrument) currencyPair, args);
  }

  @Override
  public Observable<UserTrade> getUserTrades(Instrument instrument, Object... args) {
    String channel = "user.trade." + CryptoComAdapters.toInstrumentName(instrument);
    return service
        .subscribeChannel(channel)
        .flatMapIterable(message -> service.extractData(message, CryptoComUserTradeUpdate.class))
        .filter(update -> !isDuplicateTrade(deduplicator, channel, update))
        .map(CryptoComStreamingAdapters::adaptUserTrade);
  }

  /**
   * {@code true} when the fill is a replay of one already delivered; fills carry a stable {@code
   * trade_id}. Package-private for deterministic unit tests.
   */
  static boolean isDuplicateTrade(
      CryptoComStreamingEventDeduplicator deduplicator, String channel, CryptoComUserTradeUpdate update) {
    if (update.getTradeId() == null) {
      return false;
    }
    String key = channel + "." + update.getTradeId();
    boolean duplicate = deduplicator.isDuplicate(key);
    if (duplicate) {
      LOG.debug("Dropping replayed user trade {} on reconnect", key);
    }
    return duplicate;
  }
}
