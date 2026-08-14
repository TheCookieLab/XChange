package info.bitrich.xchangestream.mexc;

import info.bitrich.xchangestream.core.StreamingTradeService;
import io.reactivex.rxjava3.core.Observable;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.instrument.Instrument;

/**
 * Trade stream for MEXC Spot v3 ({@code spot@private.orders.v3.api.pb} and {@code
 * spot@private.deals.v3.api.pb}).
 *
 * <p>Both channels are global: they push events for every symbol on the account. Events are
 * filtered by the {@link Instrument} argument after adaptation; pass {@code null} to receive every
 * symbol. Requires a connected private stream (listen key), see {@link MexcV3StreamingExchange}.
 */
public class MexcV3StreamingTradeService implements StreamingTradeService {

  private static final String CHANNEL_ORDERS = "spot@private.orders.v3.api.pb";
  private static final String CHANNEL_DEALS = "spot@private.deals.v3.api.pb";

  private final MexcV3StreamingService streamingService;

  public MexcV3StreamingTradeService(MexcV3StreamingService streamingService) {
    this.streamingService = streamingService;
  }

  @Override
  public Observable<Order> getOrderChanges(CurrencyPair currencyPair, Object... args) {
    return getOrderChanges((Instrument) currencyPair, args);
  }

  @Override
  public Observable<Order> getOrderChanges(Instrument instrument, Object... args) {
    return streamingService
        .subscribeChannel(CHANNEL_ORDERS)
        .map(MexcV3StreamingAdapters::adaptOrderPush)
        .filter(order -> instrument == null || instrument.equals(order.getInstrument()));
  }

  @Override
  public Observable<UserTrade> getUserTrades(CurrencyPair currencyPair, Object... args) {
    return getUserTrades((Instrument) currencyPair, args);
  }

  @Override
  public Observable<UserTrade> getUserTrades(Instrument instrument, Object... args) {
    return streamingService
        .subscribeChannel(CHANNEL_DEALS)
        .map(MexcV3StreamingAdapters::adaptUserTradePush)
        .filter(trade -> instrument == null || instrument.equals(trade.getInstrument()));
  }
}
