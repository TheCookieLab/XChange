package info.bitrich.xchangestream.mexc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.reactivex.rxjava3.core.Observable;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.CandleStickInterval;

/** Channel-string construction and interval mapping for the streaming market data service. */
class MexcV3StreamingMarketDataServiceTest {

  private static final CurrencyPair PAIR = new CurrencyPair(Currency.BTC, Currency.USDT);

  /** Records the channels the market data service subscribes to. */
  static final class CapturingService extends MexcV3StreamingService {

    final List<String> subscribed = new ArrayList<>();

    CapturingService() {
      super("wss://wbs-api.mexc.com/ws");
    }

    @Override
    public Observable<String> subscribeChannel(String channelName, Object... args) {
      subscribed.add(channelName);
      return Observable.never();
    }
  }

  private MexcV3StreamingMarketDataService service(CapturingService transport) {
    return new MexcV3StreamingMarketDataService(transport, null);
  }

  @Test
  void getTickerSubscribesToAggreBookTickerChannel() {
    CapturingService transport = new CapturingService();
    service(transport).getTicker(PAIR).subscribe();
    assertEquals(
        List.of("spot@public.aggre.bookTicker.v3.api.pb@100ms@BTCUSDT"), transport.subscribed);
  }

  @Test
  void getTradesSubscribesToAggreDealsChannel() {
    CapturingService transport = new CapturingService();
    service(transport).getTrades(PAIR).subscribe();
    assertEquals(List.of("spot@public.aggre.deals.v3.api.pb@100ms@BTCUSDT"), transport.subscribed);
  }

  @Test
  void getCandleStickSubscribesToKlineChannelWithInterval() {
    CapturingService transport = new CapturingService();
    service(transport).getCandleStick(PAIR, CandleStickInterval.m1).subscribe();
    assertEquals(List.of("spot@public.kline.v3.api.pb@BTCUSDT@Min1"), transport.subscribed);
  }

  @Test
  void toStreamIntervalMapsAllSupportedIntervals() {
    assertEquals("Min1", MexcV3StreamingMarketDataService.toStreamInterval(CandleStickInterval.m1));
    assertEquals("Min5", MexcV3StreamingMarketDataService.toStreamInterval(CandleStickInterval.m5));
    assertEquals(
        "Min15", MexcV3StreamingMarketDataService.toStreamInterval(CandleStickInterval.m15));
    assertEquals(
        "Min30", MexcV3StreamingMarketDataService.toStreamInterval(CandleStickInterval.m30));
    assertEquals("Min60", MexcV3StreamingMarketDataService.toStreamInterval(CandleStickInterval.h1));
    assertEquals("Hour4", MexcV3StreamingMarketDataService.toStreamInterval(CandleStickInterval.h4));
    assertEquals("Day1", MexcV3StreamingMarketDataService.toStreamInterval(CandleStickInterval.d1));
    assertEquals("Week1", MexcV3StreamingMarketDataService.toStreamInterval(CandleStickInterval.w1));
    assertEquals(
        "Month1", MexcV3StreamingMarketDataService.toStreamInterval(CandleStickInterval.M1));
  }

  @Test
  void toStreamIntervalRejectsUnsupportedInterval() {
    assertThrows(
        IllegalArgumentException.class,
        () -> MexcV3StreamingMarketDataService.toStreamInterval(CandleStickInterval.m3));
  }
}
