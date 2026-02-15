package info.bitrich.xchangestream.binance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import info.bitrich.xchangestream.core.ProductSubscription;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.observers.TestObserver;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.knowm.xchange.binance.service.BinanceMarketDataService;
import org.knowm.xchange.derivative.FuturesContract;
import org.knowm.xchange.dto.marketdata.FundingRate;
import org.knowm.xchange.instrument.Instrument;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Unit tests for funding rate subscription initialization, ensuring consistency with ticker and
 * trade subscription patterns and guarding against the regression where initFundingRateSubscription
 * used rawFundingRate (computeIfAbsent + put overwrite) and double-wrapped the observable.
 */
@RunWith(MockitoJUnitRunner.class)
public class BinanceStreamingMarketDataServiceFundingRateTest {

  private static final Instrument INSTRUMENT = new FuturesContract("ETH/USDT/PERP");

  @Mock private BinanceStreamingService streamingService;
  @Mock private BinanceMarketDataService marketDataService;

  private BinanceStreamingMarketDataService marketDataServiceUnderTest;

  @Before
  public void setUp() throws Exception {
    when(streamingService.isLiveSubscriptionEnabled()).thenReturn(false);
    when(streamingService.getProductSubscription())
        .thenReturn(
            ProductSubscription.create().addFundingRates(INSTRUMENT).build());
    doReturn(Observable.never()).when(streamingService).subscribeChannel(anyString());
    when(marketDataService.getBinanceFundingRateInfo()).thenReturn(Collections.emptyList());
    marketDataServiceUnderTest =
        new BinanceStreamingMarketDataService(
            streamingService,
            marketDataService,
            () -> {},
            null,
            false,
            1000);
  }

  /**
   * Regression test: after openSubscriptions with funding rate, the observable returned by
   * getFundingRate(instrument) must be the same instance as the one stored in
   * fundingRateInfoSubscriptions. This ensures init uses the same single-wrap pattern as ticker
   * and trade (direct raw stream + put), and that getFundingRate returns the map entry rather than
   * a different observable (which would happen with double wrap or init overwriting a
   * computeIfAbsent-created entry with a different one).
   */
  @Test
  public void fundingRateSubscriptionStoredIsSameAsGetFundingRateReturn() throws Exception {
    ProductSubscription subscription =
        ProductSubscription.create().addFundingRates(INSTRUMENT).build();
    KlineSubscription klineSubscription = new KlineSubscription(Collections.emptyMap());

    marketDataServiceUnderTest.openSubscriptions(subscription, klineSubscription);

    Observable<FundingRate> fromApi = marketDataServiceUnderTest.getFundingRate(INSTRUMENT);

    Map<Instrument, Observable<FundingRate>> map = getFundingRateInfoSubscriptionsMap();
    assertThat(map).containsKey(INSTRUMENT);
    Observable<FundingRate> stored = map.get(INSTRUMENT);
    assertThat(fromApi)
        .as("getFundingRate() must return the same observable instance stored by init")
        .isSameAs(stored);
  }

  /**
   * Ensures the funding rate channel is subscribed to exactly once when openSubscriptions is called
   * with one funding rate instrument, matching the single-subscription contract (no duplicate
   * channel subscriptions from double-wrapping or redundant computeIfAbsent + put).
   */
  @Test
  public void fundingRateChannelSubscribedOnceOnOpenSubscriptions() {
    ProductSubscription subscription =
        ProductSubscription.create().addFundingRates(INSTRUMENT).build();
    KlineSubscription klineSubscription = new KlineSubscription(Collections.emptyMap());

    marketDataServiceUnderTest.openSubscriptions(subscription, klineSubscription);

    ArgumentCaptor<String> channelCaptor = ArgumentCaptor.forClass(String.class);
    verify(streamingService).subscribeChannel(channelCaptor.capture());
    assertThat(channelCaptor.getValue()).isEqualTo("ethusdt@markPrice");
  }

  /**
   * Multiple subscribers to getFundingRate() after openSubscriptions share the same underlying
   * stream (share() semantics). Verifies that the stored observable is shared and that multiple
   * getFundingRate().subscribe() calls do not create additional channel subscriptions.
   */
  @Test
  public void getFundingRateAfterOpenSubscriptionsIsShared() {
    ProductSubscription subscription =
        ProductSubscription.create().addFundingRates(INSTRUMENT).build();
    KlineSubscription klineSubscription = new KlineSubscription(Collections.emptyMap());
    marketDataServiceUnderTest.openSubscriptions(subscription, klineSubscription);

    Observable<FundingRate> obs = marketDataServiceUnderTest.getFundingRate(INSTRUMENT);
    TestObserver<FundingRate> o1 = obs.test();
    TestObserver<FundingRate> o2 = obs.test();

    assertThat(o1).isNotNull();
    assertThat(o2).isNotNull();
    o1.dispose();
    o2.dispose();

    verify(streamingService).subscribeChannel(eq("ethusdt@markPrice"));
  }

  @SuppressWarnings("unchecked")
  private Map<Instrument, Observable<FundingRate>> getFundingRateInfoSubscriptionsMap()
      throws Exception {
    Field field =
        BinanceStreamingMarketDataService.class.getDeclaredField("fundingRateInfoSubscriptions");
    field.setAccessible(true);
    return (Map<Instrument, Observable<FundingRate>>) field.get(marketDataServiceUnderTest);
  }
}
