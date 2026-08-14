package info.bitrich.xchangestream.okex;

import info.bitrich.xchangestream.core.StreamingMarketDataService;
import info.bitrich.xchangestream.okx.OkxStreamingMarketDataService;
import io.reactivex.rxjava3.core.Observable;
import java.util.List;
import org.knowm.xchange.dto.marketdata.CandleStickData;
import org.knowm.xchange.dto.marketdata.CandleStickInterval;
import org.knowm.xchange.dto.marketdata.FundingRate;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.OrderBookUpdate;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trade;
import org.knowm.xchange.dto.meta.ExchangeMetaData;
import org.knowm.xchange.instrument.Instrument;

/**
 * @deprecated use {@link info.bitrich.xchangestream.okx.OkxStreamingMarketDataService} instead.
 */
@Deprecated
public class OkexStreamingMarketDataService implements StreamingMarketDataService {

  private final OkxStreamingMarketDataService delegate;

  public OkexStreamingMarketDataService(OkxStreamingMarketDataService delegate) {
    this.delegate = delegate;
  }

  /**
   * Retained legacy constructor; delegates to the canonical service built from the legacy
   * transports (which extend the canonical transport classes).
   *
   * @deprecated use {@link info.bitrich.xchangestream.okx.OkxStreamingMarketDataService} instead.
   */
  @Deprecated
  public OkexStreamingMarketDataService(
      OkexStreamingService service,
      OkexBusinessStreamingService businessStreamingService,
      ExchangeMetaData exchangeMetaData) {
    this(new OkxStreamingMarketDataService(service, businessStreamingService, exchangeMetaData));
  }

  @Override
  public Observable<Ticker> getTicker(Instrument instrument, Object... args) {
    return delegate.getTicker(instrument, args);
  }

  @Override
  public Observable<Trade> getTrades(Instrument instrument, Object... args) {
    return delegate.getTrades(instrument, args);
  }

  @Override
  public Observable<FundingRate> getFundingRate(Instrument instrument, Object... args) {
    return delegate.getFundingRate(instrument, args);
  }

  @Override
  public Observable<OrderBook> getOrderBook(Instrument instrument, Object... args) {
    return delegate.getOrderBook(instrument, args);
  }

  @Override
  public Observable<List<OrderBookUpdate>> getOrderBookUpdates(
      Instrument instrument, Object... args) {
    return delegate.getOrderBookUpdates(instrument, args);
  }

  @Override
  public Observable<CandleStickData> getCandleStick(
      Instrument instrument, CandleStickInterval interval) {
    return delegate.getCandleStick(instrument, interval);
  }
}
