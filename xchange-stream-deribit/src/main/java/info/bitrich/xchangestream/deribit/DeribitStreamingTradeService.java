package info.bitrich.xchangestream.deribit;

import info.bitrich.xchangestream.core.StreamingTradeService;
import info.bitrich.xchangestream.deribit.dto.response.DeribitUserTradeNotification;
import io.reactivex.rxjava3.core.Observable;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.deribit.v2.DeribitAdapters;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.instrument.Instrument;

public class DeribitStreamingTradeService implements StreamingTradeService {

  private final DeribitStreamingService service;

  public DeribitStreamingTradeService(DeribitStreamingService service) {
    this.service = service;
  }

  @Override
  public Observable<UserTrade> getUserTrades(CurrencyPair currencyPair, Object... args) {
    return getUserTrades((Instrument) currencyPair, args);
  }

  @Override
  public Observable<UserTrade> getUserTrades(Instrument instrument, Object... args) {
    var channelName = String.format("user.trades.%s.100ms", DeribitAdapters.toString(instrument));
    return service
        .subscribeChannel(channelName)
        .map(DeribitUserTradeNotification.class::cast)
        .map(DeribitStreamingAdapters::toUserTrade);
  }
}
