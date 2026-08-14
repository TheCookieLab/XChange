package info.bitrich.xchangestream.okx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import info.bitrich.xchangestream.service.netty.StreamingObjectMapperHelper;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.observers.TestObserver;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;
import org.junit.Before;
import org.junit.Test;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.OrderBookUpdate;
import org.knowm.xchange.dto.meta.ExchangeMetaData;
import org.knowm.xchange.dto.meta.InstrumentMetaData;
import org.knowm.xchange.instrument.Instrument;

/** Offline tests for order-book continuity handling in {@link OkxStreamingMarketDataService}. */
public class OkxStreamingOrderBookTest {

  private static final Instrument INSTRUMENT = CurrencyPair.BTC_USDT;
  private static final String CHANNEL_UNIQUE_ID = "booksBTC-USDT";

  private static final String[][] BID_100 = {{"100.0", "10"}};
  private static final String[][] ASK_101 = {{"101.0", "5"}};
  private static final String[][] BID_99 = {{"99.0", "1"}};
  private static final String[][] EMPTY = new String[0][0];

  private final ObjectMapper mapper = StreamingObjectMapperHelper.getObjectMapper();
  private OkxStreamingMarketDataService marketDataService;
  private OkxStreamingService streamingService;

  @Before
  public void setUp() {
    streamingService = mock(OkxStreamingService.class);
    OkxBusinessStreamingService businessStreamingService = mock(OkxBusinessStreamingService.class);
    Map<Instrument, InstrumentMetaData> instruments = new HashMap<>();
    instruments.put(INSTRUMENT, InstrumentMetaData.builder().build());
    ExchangeMetaData exchangeMetaData =
        new ExchangeMetaData(instruments, Collections.emptyMap(), null, null, null);
    marketDataService =
        new OkxStreamingMarketDataService(
            streamingService, businessStreamingService, exchangeMetaData);
  }

  /** Independent re-implementation of the OKX CRC32 checksum spec. */
  private static long expectedChecksum(String[][] bids, String[][] asks) {
    CRC32 crc = new CRC32();
    for (String[] level : bids) {
      crc.update((level[0] + ":" + level[1]).getBytes(StandardCharsets.UTF_8));
    }
    for (String[] level : asks) {
      crc.update((level[0] + ":" + level[1]).getBytes(StandardCharsets.UTF_8));
    }
    return crc.getValue();
  }

  private JsonNode bookMessage(
      String action, long seqId, long checksum, String[][] bids, String[][] asks) {
    ObjectNode root = mapper.createObjectNode();
    if (action != null) {
      root.put("action", action);
    }
    ObjectNode arg = root.putObject("arg");
    arg.put("channel", "books");
    arg.put("instId", "BTC-USDT");
    ObjectNode data = root.putArray("data").addObject();
    ArrayNode bidsArray = data.putArray("bids");
    for (String[] level : bids) {
      bidsArray.addArray().add(level[0]).add(level[1]).add("0").add("1");
    }
    ArrayNode asksArray = data.putArray("asks");
    for (String[] level : asks) {
      asksArray.addArray().add(level[0]).add(level[1]).add("0").add("1");
    }
    data.put("ts", "1699999999999");
    if (seqId != OkxBookContinuity.UNKNOWN_SEQ) {
      data.put("seqId", seqId);
    }
    if (checksum != 0) {
      data.put("checksum", checksum);
    }
    return root;
  }

  @Test
  public void testSnapshotAndUpdateAccepted() {
    JsonNode snapshot =
        bookMessage("snapshot", 1, expectedChecksum(BID_100, ASK_101), BID_100, ASK_101);
    String[][] bidsAfter = {{"100.0", "10"}, {"99.0", "1"}};
    JsonNode update = bookMessage("update", 2, expectedChecksum(bidsAfter, ASK_101), BID_99, EMPTY);

    when(streamingService.subscribeChannel(CHANNEL_UNIQUE_ID))
        .thenReturn(Observable.just(snapshot, update));

    TestObserver<OrderBook> observer = marketDataService.getOrderBook(INSTRUMENT).test();

    observer.assertNoErrors().assertValueCount(2);
    OrderBook book = observer.values().get(1);
    assertThat(book.getBids()).hasSize(2);
    assertThat(book.getAsks()).hasSize(1);
    assertThat(book.getBids().get(0).getLimitPrice()).isEqualByComparingTo("100.0");
    assertThat(book.getBids().get(0).getOriginalAmount()).isEqualByComparingTo("10");
    assertThat(book.getBids().get(1).getLimitPrice()).isEqualByComparingTo("99.0");
    assertThat(book.getBids().get(1).getOriginalAmount()).isEqualByComparingTo("1");
    verify(streamingService, never()).resubscribeChannel(CHANNEL_UNIQUE_ID);
  }

  @Test
  public void testDuplicateUpdateDropped() {
    JsonNode snapshot =
        bookMessage("snapshot", 1, expectedChecksum(BID_100, ASK_101), BID_100, ASK_101);
    String[][] bidsAfter = {{"100.0", "10"}, {"99.0", "1"}};
    JsonNode update = bookMessage("update", 2, expectedChecksum(bidsAfter, ASK_101), BID_99, EMPTY);

    when(streamingService.subscribeChannel(CHANNEL_UNIQUE_ID))
        .thenReturn(Observable.just(snapshot, update, update));

    TestObserver<OrderBook> observer = marketDataService.getOrderBook(INSTRUMENT).test();

    observer.assertNoErrors().assertValueCount(2);
    verify(streamingService, never()).resubscribeChannel(CHANNEL_UNIQUE_ID);
  }

  @Test
  public void testSequenceGapTriggersResubscribeAndRecoversOnSnapshot() {
    JsonNode snapshot =
        bookMessage("snapshot", 1, expectedChecksum(BID_100, ASK_101), BID_100, ASK_101);
    String[][] bidsAfter = {{"100.0", "10"}, {"99.0", "1"}};
    JsonNode update = bookMessage("update", 2, expectedChecksum(bidsAfter, ASK_101), BID_99, EMPTY);
    JsonNode gapUpdate = bookMessage("update", 5, 0, BID_99, EMPTY);
    JsonNode snapshotAfterGap =
        bookMessage("snapshot", 100, expectedChecksum(BID_100, ASK_101), BID_100, ASK_101);
    JsonNode updateAfterGap =
        bookMessage("update", 101, expectedChecksum(bidsAfter, ASK_101), BID_99, EMPTY);

    when(streamingService.subscribeChannel(CHANNEL_UNIQUE_ID))
        .thenReturn(Observable.just(snapshot, update, gapUpdate, snapshotAfterGap, updateAfterGap));

    TestObserver<OrderBook> observer = marketDataService.getOrderBook(INSTRUMENT).test();

    // snapshot, update, (gap dropped), snapshot, update
    observer.assertNoErrors().assertValueCount(4);
    verify(streamingService).resubscribeChannel(CHANNEL_UNIQUE_ID);
    OrderBook book = observer.values().get(3);
    assertThat(book.getBids()).hasSize(2);
  }

  @Test
  public void testChecksumMismatchTriggersResubscribe() {
    JsonNode snapshot =
        bookMessage("snapshot", 1, expectedChecksum(BID_100, ASK_101), BID_100, ASK_101);
    JsonNode badUpdate = bookMessage("update", 2, 12345L, BID_99, EMPTY);
    JsonNode snapshotAfterMismatch =
        bookMessage("snapshot", 100, expectedChecksum(BID_100, ASK_101), BID_100, ASK_101);

    when(streamingService.subscribeChannel(CHANNEL_UNIQUE_ID))
        .thenReturn(Observable.just(snapshot, badUpdate, snapshotAfterMismatch));

    TestObserver<OrderBook> observer = marketDataService.getOrderBook(INSTRUMENT).test();

    observer.assertNoErrors().assertValueCount(2);
    verify(streamingService).resubscribeChannel(CHANNEL_UNIQUE_ID);
  }

  @Test
  public void testZeroChecksumReliesOnSequenceOnly() {
    // Modern OKX always sends checksum 0: continuity is enforced via seqId alone.
    JsonNode snapshot = bookMessage("snapshot", 1, 0, BID_100, ASK_101);
    JsonNode update = bookMessage("update", 2, 0, BID_99, EMPTY);

    when(streamingService.subscribeChannel(CHANNEL_UNIQUE_ID))
        .thenReturn(Observable.just(snapshot, update, update));

    TestObserver<OrderBook> observer = marketDataService.getOrderBook(INSTRUMENT).test();

    observer.assertNoErrors().assertValueCount(2);
    verify(streamingService, never()).resubscribeChannel(CHANNEL_UNIQUE_ID);
    assertThat(observer.values().get(1).getBids()).hasSize(2);
  }

  @Test
  public void testAcceptedUpdateStreamsOrderBookUpdates() {
    JsonNode snapshot =
        bookMessage("snapshot", 1, expectedChecksum(BID_100, ASK_101), BID_100, ASK_101);
    String[][] bidsAfter = {{"100.0", "10"}, {"99.0", "1"}};
    JsonNode update = bookMessage("update", 2, expectedChecksum(bidsAfter, ASK_101), BID_99, EMPTY);

    TestObserver<List<OrderBookUpdate>> updatesObserver =
        marketDataService.getOrderBookUpdates(INSTRUMENT).test();

    when(streamingService.subscribeChannel(CHANNEL_UNIQUE_ID))
        .thenReturn(Observable.just(snapshot, update));

    marketDataService.getOrderBook(INSTRUMENT).test();

    updatesObserver.assertNoErrors().assertValueCount(1);
    assertThat(updatesObserver.values().get(0))
        .extracting(bookUpdate -> bookUpdate.getLimitOrder().getLimitPrice())
        .containsExactly(new BigDecimal("99.0"));
  }

  @Test
  public void testUpdateBeforeAnySnapshotRequestsResubscribe() {
    JsonNode update = bookMessage("update", 2, 0, BID_99, EMPTY);
    JsonNode snapshot =
        bookMessage("snapshot", 100, expectedChecksum(BID_100, ASK_101), BID_100, ASK_101);

    when(streamingService.subscribeChannel(CHANNEL_UNIQUE_ID))
        .thenReturn(Observable.just(update, snapshot));

    TestObserver<OrderBook> observer = marketDataService.getOrderBook(INSTRUMENT).test();

    observer.assertNoErrors().assertValueCount(1);
    verify(streamingService).resubscribeChannel(CHANNEL_UNIQUE_ID);
  }
}
