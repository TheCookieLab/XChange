package info.bitrich.xchangestream.kraken;

import info.bitrich.xchangestream.core.StreamingMarketDataService;
import info.bitrich.xchangestream.kraken.dto.common.ChannelType;
import info.bitrich.xchangestream.kraken.dto.response.KrakenTickerMessage;
import io.reactivex.rxjava3.core.Observable;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.Ticker;

public class KrakenStreamingMarketDataService implements StreamingMarketDataService {

  private final KrakenStreamingService service;

  public KrakenStreamingMarketDataService(KrakenStreamingService service) {
    this.service = service;
  }

  @Override
  public Observable<Ticker> getTicker(CurrencyPair currencyPair, Object... args) {
    return service
        .subscribeChannel(ChannelType.TICKER.getValue(), currencyPair)
        .map(KrakenTickerMessage.class::cast)
        .map(krakenTickerMessage -> KrakenStreamingAdapters.toTicker(krakenTickerMessage.getData().get(0)));
  }
}
