package info.bitrich.xchangestream.okex;

import info.bitrich.xchangestream.core.StreamingTradeService;
import info.bitrich.xchangestream.okx.OkxStreamingTradeService;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import org.knowm.xchange.client.ResilienceRegistries;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.account.OpenPosition;
import org.knowm.xchange.dto.meta.ExchangeMetaData;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.service.trade.params.CancelOrderParams;

/**
 * @deprecated use {@link info.bitrich.xchangestream.okx.OkxStreamingTradeService} instead.
 */
@Deprecated
public class OkexStreamingTradeService implements StreamingTradeService {

  private final OkxStreamingTradeService delegate;

  public OkexStreamingTradeService(OkxStreamingTradeService delegate) {
    this.delegate = delegate;
  }

  /**
   * Retained legacy constructor; delegates to the canonical service built from the legacy private
   * transport (which extends the canonical transport class).
   *
   * @deprecated use {@link info.bitrich.xchangestream.okx.OkxStreamingTradeService} instead.
   */
  @Deprecated
  public OkexStreamingTradeService(
      OkexPrivateStreamingService privateStreamingService,
      ExchangeMetaData exchangeMetaData,
      ResilienceRegistries resilienceRegistries) {
    this(
        new OkxStreamingTradeService(
            privateStreamingService, exchangeMetaData, resilienceRegistries));
  }

  @Override
  public Observable<Order> getOrderChanges(Instrument instrument, Object... args) {
    return delegate.getOrderChanges(instrument, args);
  }

  @Override
  public Observable<UserTrade> getUserTrades(Instrument instrument, Object... args) {
    return delegate.getUserTrades(instrument, args);
  }

  @Override
  public Observable<OpenPosition> getPositionChanges(Instrument instrument) {
    return delegate.getPositionChanges(instrument);
  }

  @Override
  public Single<Integer> placeLimitOrder(LimitOrder order, Object... args) {
    return delegate.placeLimitOrder(order, args);
  }

  @Override
  public Single<Integer> placeMarketOrder(MarketOrder order, Object... args) {
    return delegate.placeMarketOrder(order, args);
  }

  @Override
  public Single<Integer> changeOrder(LimitOrder order, Object... args) {
    return delegate.changeOrder(order, args);
  }

  @Override
  public Single<Integer> cancelOrder(CancelOrderParams params, Object... args) {
    return delegate.cancelOrder(params, args);
  }
}
