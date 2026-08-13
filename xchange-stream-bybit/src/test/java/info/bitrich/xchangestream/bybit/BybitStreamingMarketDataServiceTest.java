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
import java.math.BigDecimal;
import java.util.List;
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
  public void shallowDepthGapRebuildsBookFromItsOwnSnapshot() throws Exception {
    JsonNode depth200Snapshot = orderBook("snapshot", 100, "16493.50", "16611.00");
    // The 50-level stream reconnects after a gap: its first messages are an orphan delta
    // (continuity broken) followed by its own fresh snapshot and a valid delta.
    JsonNode depth50OrphanDelta = orderBook("delta", 101, "16494.00", "16612.00");
    JsonNode depth50Snapshot = orderBook("snapshot", 200, "16494.00", "16612.00");
    JsonNode depth50Delta = orderBook("delta", 201, "16495.00", "16613.00");

    when(streamingService.subscribeChannel(anyString()))
        .thenAnswer(
            invocation -> {
              String channel = invocation.getArgument(0);
              if (channel.equals("orderbook.200.BTCUSDT")) {
                return Observable.fromIterable(List.of(depth200Snapshot));
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
    // The shallow depth's own snapshot must rebuild the shared book (any-depth snapshot).
    OrderBook finalBook = bookObserver.values().get(bookObserver.values().size() - 1);
    assertThat(finalBook.getAsks())
        .anyMatch(level -> level.getLimitPrice().compareTo(new BigDecimal("16613.00")) == 0);
  }
}
