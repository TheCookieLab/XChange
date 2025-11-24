package info.bitrich.xchangestream.deribit;

import info.bitrich.xchangestream.core.StreamingMarketDataService;
import info.bitrich.xchangestream.deribit.dto.response.DeribitTickerNotification;
import io.reactivex.rxjava3.core.Observable;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.deribit.v2.DeribitAdapters;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.instrument.Instrument;

public class DeribitStreamingMarketDataService implements StreamingMarketDataService {

  private final DeribitStreamingService service;

  public DeribitStreamingMarketDataService(DeribitStreamingService service) {
    this.service = service;
  }

  @Override
  public Observable<Ticker> getTicker(CurrencyPair currencyPair, Object... args) {
    return getTicker((Instrument) currencyPair, args);
  }

  @Override
  public Observable<Ticker> getTicker(Instrument instrument, Object... args) {
    var channelName = String.format("ticker.%s.agg2", DeribitAdapters.toString(instrument));
    return service
        .subscribeChannel(channelName)
        .map(DeribitTickerNotification.class::cast)
        .map(DeribitStreamingAdapters::toTicker);
  }
}
