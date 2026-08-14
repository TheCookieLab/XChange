package info.bitrich.xchangestream.mexc;

import info.bitrich.xchangestream.core.StreamingMarketDataService;
import io.reactivex.rxjava3.core.Observable;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.CandleStickData;
import org.knowm.xchange.dto.marketdata.CandleStickInterval;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trade;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.mexc.v3.MexcV3Symbols;
import org.knowm.xchange.mexc.v3.service.MexcV3MarketDataServiceRaw;

/**
 * Streaming market data for MEXC Spot v3.
 *
 * <p>Channels: {@code aggre.bookTicker} for {@link #getTicker(CurrencyPair, Object...)}, {@code
 * aggre.deals} for {@link #getTrades(CurrencyPair, Object...)}, and {@code kline} for {@link
 * #getCandleStick(Instrument, CandleStickInterval)}. Each emitted value is a decoded binary push
 * adapted from its protobuf body.
 */
public class MexcV3StreamingMarketDataService implements StreamingMarketDataService {

  private static final String CHANNEL_TEMPLATE_AGGRE_DEALS =
      "spot@public.aggre.deals.v3.api.pb@100ms@%s";
  private static final String CHANNEL_TEMPLATE_AGGRE_BOOK_TICKER =
      "spot@public.aggre.bookTicker.v3.api.pb@100ms@%s";
  private static final String CHANNEL_TEMPLATE_KLINE = "spot@public.kline.v3.api.pb@%s@%s";

  private final MexcV3StreamingService streamingService;

  /**
   * @param streamingService the MEXC v3 WebSocket transport
   * @param rawMarketDataService REST access, used by order-book snapshot reconciliation
   */
  public MexcV3StreamingMarketDataService(
      MexcV3StreamingService streamingService, MexcV3MarketDataServiceRaw rawMarketDataService) {
    this.streamingService = streamingService;
  }

  @Override
  public Observable<Ticker> getTicker(CurrencyPair currencyPair, Object... args) {
    String symbol = MexcV3Symbols.toMexcSymbol(currencyPair);
    return streamingService
        .subscribeChannel(String.format(CHANNEL_TEMPLATE_AGGRE_BOOK_TICKER, symbol))
        .map(json -> MexcV3StreamingAdapters.adaptBookTicker(json, currencyPair));
  }

  @Override
  public Observable<Trade> getTrades(CurrencyPair currencyPair, Object... args) {
    String symbol = MexcV3Symbols.toMexcSymbol(currencyPair);
    return streamingService
        .subscribeChannel(String.format(CHANNEL_TEMPLATE_AGGRE_DEALS, symbol))
        .flatMapIterable(json -> MexcV3StreamingAdapters.adaptAggreDeals(json, currencyPair));
  }

  @Override
  public Observable<CandleStickData> getCandleStick(
      Instrument instrument, CandleStickInterval interval) {
    if (!(instrument instanceof CurrencyPair)) {
      throw new IllegalArgumentException(
          "MEXC Spot v3 klines require a CurrencyPair, got: " + instrument);
    }
    CurrencyPair pair = (CurrencyPair) instrument;
    return streamingService
        .subscribeChannel(
            String.format(
                CHANNEL_TEMPLATE_KLINE, MexcV3Symbols.toMexcSymbol(pair), toStreamInterval(interval)))
        .map(json -> MexcV3StreamingAdapters.adaptKline(json, pair));
  }

  /** Maps a XChange interval to the MEXC v3 stream interval token. */
  public static String toStreamInterval(CandleStickInterval interval) {
    switch (interval) {
      case m1:
        return "Min1";
      case m5:
        return "Min5";
      case m15:
        return "Min15";
      case m30:
        return "Min30";
      case h1:
        return "Min60";
      case h4:
        return "Hour4";
      case d1:
        return "Day1";
      case w1:
        return "Week1";
      case M1:
        return "Month1";
      default:
        throw new IllegalArgumentException("Unsupported MEXC v3 kline interval: " + interval);
    }
  }
}
