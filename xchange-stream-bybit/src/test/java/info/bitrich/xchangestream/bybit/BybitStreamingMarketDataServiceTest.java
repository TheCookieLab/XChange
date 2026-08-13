package info.bitrich.xchangestream.bybit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import info.bitrich.xchangestream.bybit.dto.marketdata.BybitOrderBookGap;
import info.bitrich.xchangestream.service.netty.StreamingObjectMapperHelper;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.subjects.PublishSubject;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Before;
import org.junit.Test;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.CandleStickData;
import org.knowm.xchange.dto.marketdata.CandleStickInterval;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.instrument.Instrument;

public class BybitStreamingMarketDataServiceTest {

  private BybitStreamingMarketDataService marketDataService;
  private BybitStreamingService streamingService;
  private final ObjectMapper mapper = StreamingObjectMapperHelper.getObjectMapper();

  @Before
  public void setUp() {
    streamingService = mock(BybitStreamingService.class);
    marketDataService = new BybitStreamingMarketDataService(streamingService);
  }

  @Test
  public void testGetCandleStick() throws Exception {
    JsonNode jsonNode =
        mapper.readTree(
            ClassLoader.getSystemClassLoader().getResourceAsStream("getCandleStickResponse.json"));

    when(streamingService.subscribeChannel(anyString())).thenReturn(Observable.just(jsonNode));

    Instrument instrument = CurrencyPair.BTC_USDT;
    Observable<CandleStickData> candleStickDataObservable =
        marketDataService.getCandleStick(instrument, CandleStickInterval.m5);

    CandleStickData candleStickData = candleStickDataObservable.blockingFirst();

    assertThat(candleStickData).isNotNull();
    assertThat(candleStickData.getInstrument()).isEqualTo(instrument);
    assertThat(candleStickData.getCandleSticks()).hasSize(1);
    assertThat(candleStickData.getCandleSticks().get(0).getOpen())
        .isEqualByComparingTo(new BigDecimal("16649.5"));
    assertThat(candleStickData.getCandleSticks().get(0).getClose())
        .isEqualByComparingTo(new BigDecimal("16677"));
    assertThat(candleStickData.getCandleSticks().get(0).getHigh())
        .isEqualByComparingTo(new BigDecimal("16677"));
    assertThat(candleStickData.getCandleSticks().get(0).getLow())
        .isEqualByComparingTo(new BigDecimal("16608"));
    assertThat(candleStickData.getCandleSticks().get(0).getVolume())
        .isEqualByComparingTo(new BigDecimal("2.081"));
    assertThat(candleStickData.getCandleSticks().get(0).getQuotaVolume())
        .isEqualByComparingTo(new BigDecimal("34666.4005"));
    assertThat(candleStickData.getCandleSticks().get(0).getTimestamp().toEpochMilli())
        .isEqualTo(1672324988882L);
  }

  /** Builds a Bybit order book WebSocket payload with the given update id and levels. */
  private JsonNode orderBook(String type, long u, String bidPrice, String askPrice) throws Exception {
    return mapper.readTree(
        "{\"topic\":\"orderbook.50.BTCUSDT\",\"type\":\""
            + type
            + "\",\"ts\":1672304484978,\"data\":{\"s\":\"BTCUSDT\",\"b\":[[\""
            + bidPrice
            + "\",\"0.006\"]],\"a\":[[\""
            + askPrice
            + "\",\"0.029\"]],\"u\":"
            + u
            + ",\"seq\":7961638724},\"cts\":1672304484976}");
  }

  @Test
  public void orderBookSequenceGapEmitsDedicatedEventAndRebuildsFromSnapshot() throws Exception {
    JsonNode snapshot = orderBook("snapshot", 100, "16493.50", "16611.00");
    JsonNode deltaOk = orderBook("delta", 101, "16494.00", "16612.00");
    JsonNode gapDelta = orderBook("delta", 150, "16495.00", "17000.00");
    JsonNode snapshotAfterResubscribe = orderBook("snapshot", 150, "16495.00", "17000.00");
    JsonNode deltaAfterResubscribe = orderBook("delta", 151, "16496.00", "17001.00");

    when(streamingService.subscribeChannel(anyString()))
        .thenReturn(
            Observable.fromIterable(
                List.of(snapshot, deltaOk, gapDelta, snapshotAfterResubscribe, deltaAfterResubscribe)));
    when(streamingService.isSocketOpen()).thenReturn(true);
    when(streamingService.getUnsubscribeMessage(anyString(), any(Object[].class)))
        .thenReturn("{\"op\":\"unsubscribe\"}");
    when(streamingService.getSubscribeMessage(anyString(), any(Object[].class)))
        .thenReturn("{\"op\":\"subscribe\"}");

    TestObserver<BybitOrderBookGap> gapObserver =
        marketDataService.getOrderBookGapEvents().test();
    TestObserver<OrderBook> bookObserver =
        marketDataService.getOrderBook((Instrument) CurrencyPair.BTC_USDT).test();

    // The gap is surfaced as a dedicated failure with the exact expected/actual update ids.
    assertThat(gapObserver.values()).hasSize(1);
    BybitOrderBookGap gap = gapObserver.values().get(0);
    assertThat(gap.getChannelUniqueId()).isEqualTo("orderbook.50.BTCUSDT");
    assertThat(gap.getExpectedU()).isEqualTo(102);
    assertThat(gap.getActualU()).isEqualTo(150);
    assertThat(gap.getReason()).isEqualTo("sequence");
    // Rebuild = unsubscribe + resubscribe, then a fresh snapshot resets the sequence.
    verify(streamingService, times(2)).sendMessage(anyString());
    OrderBook finalBook = bookObserver.values().get(bookObserver.values().size() - 1);
    assertThat(finalBook.getAsks())
        .anyMatch(level -> level.getLimitPrice().compareTo(new BigDecimal("17001.00")) == 0);
    assertThat(finalBook.getBids())
        .anyMatch(level -> level.getLimitPrice().compareTo(new BigDecimal("16496.00")) == 0);
  }

  @Test
  public void orderBookDeltaWithoutSnapshotSurfacesGapAndForcesRebuild() throws Exception {
    JsonNode orphanDelta = orderBook("delta", 100, "16493.50", "16611.00");
    JsonNode snapshot = orderBook("snapshot", 100, "16493.50", "16611.00");
    JsonNode deltaOk = orderBook("delta", 101, "16494.00", "16612.00");

    when(streamingService.subscribeChannel(anyString()))
        .thenReturn(Observable.fromIterable(List.of(orphanDelta, snapshot, deltaOk)));
    when(streamingService.isSocketOpen()).thenReturn(true);
    when(streamingService.getUnsubscribeMessage(anyString(), any(Object[].class)))
        .thenReturn("{\"op\":\"unsubscribe\"}");
    when(streamingService.getSubscribeMessage(anyString(), any(Object[].class)))
        .thenReturn("{\"op\":\"subscribe\"}");

    TestObserver<BybitOrderBookGap> gapObserver =
        marketDataService.getOrderBookGapEvents().test();
    TestObserver<OrderBook> bookObserver =
        marketDataService.getOrderBook((Instrument) CurrencyPair.BTC_USDT).test();

    assertThat(gapObserver.values()).hasSize(1);
    assertThat(gapObserver.values().get(0).getReason()).isEqualTo("missing-snapshot");
    OrderBook finalBook = bookObserver.values().get(bookObserver.values().size() - 1);
    assertThat(finalBook.getAsks())
        .anyMatch(level -> level.getLimitPrice().compareTo(new BigDecimal("16612.00")) == 0);
  }

  @Test
  public void orderBookSnapshotAfterReconnectResetsSequenceWithoutGap() throws Exception {
    JsonNode snapshot1 = orderBook("snapshot", 10, "16493.50", "16611.00");
    JsonNode delta1 = orderBook("delta", 11, "16494.00", "16612.00");
    // Reconnect: base transport resubscribes the channel and Bybit sends a fresh snapshot.
    JsonNode snapshot2 = orderBook("snapshot", 1000, "16500.00", "17000.00");
    JsonNode delta2 = orderBook("delta", 1001, "16501.00", "17001.00");

    when(streamingService.subscribeChannel(anyString()))
        .thenReturn(Observable.fromIterable(List.of(snapshot1, delta1, snapshot2, delta2)));

    TestObserver<BybitOrderBookGap> gapObserver =
        marketDataService.getOrderBookGapEvents().test();
    TestObserver<OrderBook> bookObserver =
        marketDataService.getOrderBook((Instrument) CurrencyPair.BTC_USDT).test();

    assertThat(gapObserver.values()).isEmpty();
    OrderBook finalBook = bookObserver.values().get(bookObserver.values().size() - 1);
    assertThat(finalBook.getBids())
        .anyMatch(level -> level.getLimitPrice().compareTo(new BigDecimal("16501.00")) == 0);
  }

  @Test
  public void shallowDepthGapDoesNotTruncateDeepBook() throws Exception {
    // The 200-level book is authoritative: its snapshot rebuilds the shared book and its delta
    // advances it. The 50-level channel then reconnects after a gap: orphan delta (continuity
    // broken), its own fresh snapshot, and a valid delta. The shallow snapshot must NOT rebuild
    // (truncate) the shared book; only its sequence is rebased and its deltas still apply.
    JsonNode depth200Snapshot = orderBook("snapshot", 100, "16493.50", "16611.00");
    JsonNode depth200Delta = orderBook("delta", 101, "16494.00", "16612.00");
    JsonNode depth50OrphanDelta = orderBook("delta", 101, "16494.00", "16612.00");
    JsonNode depth50Snapshot = orderBook("snapshot", 200, "16494.00", "16612.00");
    JsonNode depth50Delta = orderBook("delta", 201, "16495.00", "16613.00");

    when(streamingService.subscribeChannel(anyString()))
        .thenAnswer(
            invocation -> {
              String channel = invocation.getArgument(0);
              if (channel.equals("orderbook.200.BTCUSDT")) {
                return Observable.fromIterable(List.of(depth200Snapshot, depth200Delta));
              }
              return Observable.fromIterable(
                  List.of(depth50OrphanDelta, depth50Snapshot, depth50Delta));
            });
    when(streamingService.isSocketOpen()).thenReturn(true);
    when(streamingService.getUnsubscribeMessage(anyString(), any(Object[].class)))
        .thenReturn("{\"op\":\"unsubscribe\"}");
    when(streamingService.getSubscribeMessage(anyString(), any(Object[].class)))
        .thenReturn("{\"op\":\"subscribe\"}");

    TestObserver<BybitOrderBookGap> gapObserver =
        marketDataService.getOrderBookGapEvents().test();
    TestObserver<OrderBook> bookObserver =
        marketDataService.getOrderBook((Instrument) CurrencyPair.BTC_USDT, "50,200").test();

    assertThat(gapObserver.values()).hasSize(1);
    // The shallow channel's own snapshot must NOT rebuild (and thereby truncate) the shared
    // 200-level book; its deltas continue applying on top of the deep book.
    OrderBook finalBook = bookObserver.values().get(bookObserver.values().size() - 1);
    assertThat(finalBook.getAsks())
        .anyMatch(level -> level.getLimitPrice().compareTo(new BigDecimal("16613.00")) == 0);
  }

  @Test
  public void unsubscribedDepthObservableDoesNotGovernTheBook() throws Exception {
    // An observable created but never subscribed must not register its depth: the phantom
    // 200-level entry would otherwise discard the real 50-level snapshot and starve its deltas.
    JsonNode depth200Snapshot = orderBook("snapshot", 100, "16493.50", "16611.00");
    JsonNode depth50Snapshot = orderBook("snapshot", 100, "16493.50", "16611.00");
    JsonNode depth50Delta = orderBook("delta", 101, "16494.00", "16612.00");

    when(streamingService.subscribeChannel("orderbook.200.BTCUSDT"))
        .thenReturn(Observable.just(depth200Snapshot));
    when(streamingService.subscribeChannel("orderbook.50.BTCUSDT"))
        .thenReturn(Observable.fromIterable(List.of(depth50Snapshot, depth50Delta)));

    TestObserver<BybitOrderBookGap> gapObserver =
        marketDataService.getOrderBookGapEvents().test();
    // Cold observable obtained but never subscribed: no depth may be registered.
    marketDataService.getOrderBook((Instrument) CurrencyPair.BTC_USDT, "200");
    TestObserver<OrderBook> bookObserver =
        marketDataService.getOrderBook((Instrument) CurrencyPair.BTC_USDT, "50").test();

    assertThat(gapObserver.values()).isEmpty();
    OrderBook finalBook = bookObserver.values().get(bookObserver.values().size() - 1);
    assertThat(finalBook.getAsks())
        .anyMatch(level -> level.getLimitPrice().compareTo(new BigDecimal("16612.00")) == 0);
  }

  @Test
  public void duplicateDepthSubscriberDisposalKeepsDepthRegistered() throws Exception {
    // Two subscribers to the same depth share one registration: disposing one subscriber must
    // release one reference, not unregister the depth while the other subscriber is active.
    JsonNode snapshot = orderBook("snapshot", 100, "16493.50", "16611.00");
    JsonNode delta = orderBook("delta", 101, "16494.00", "16612.00");
    PublishSubject<JsonNode> channel = PublishSubject.create();
    when(streamingService.subscribeChannel("orderbook.50.BTCUSDT")).thenReturn(channel);
    when(streamingService.isSocketOpen()).thenReturn(true);
    when(streamingService.getUnsubscribeMessage(anyString(), any(Object[].class)))
        .thenReturn("{\"op\":\"unsubscribe\"}");
    when(streamingService.getSubscribeMessage(anyString(), any(Object[].class)))
        .thenReturn("{\"op\":\"subscribe\"}");

    TestObserver<BybitOrderBookGap> gapObserver =
        marketDataService.getOrderBookGapEvents().test();
    TestObserver<OrderBook> firstSubscriber =
        marketDataService.getOrderBook((Instrument) CurrencyPair.BTC_USDT).test();
    TestObserver<OrderBook> secondSubscriber =
        marketDataService.getOrderBook((Instrument) CurrencyPair.BTC_USDT).test();
    channel.onNext(snapshot);
    firstSubscriber.dispose();
    // The surviving subscriber's delta still applies: its depth stayed registered.
    channel.onNext(delta);

    assertThat(gapObserver.values()).isEmpty();
    OrderBook finalBook = secondSubscriber.values().get(secondSubscriber.values().size() - 1);
    assertThat(finalBook.getAsks())
        .anyMatch(level -> level.getLimitPrice().compareTo(new BigDecimal("16612.00")) == 0);
  }
}
