package info.bitrich.xchangestream.bitget.uta.v3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import info.bitrich.xchangestream.bitget.uta.v3.dto.BitgetUtaV3Action;
import info.bitrich.xchangestream.bitget.uta.v3.dto.BitgetUtaV3Channel;
import info.bitrich.xchangestream.bitget.uta.v3.dto.BitgetUtaV3WsNotification;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.subjects.PublishSubject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.OrderBook;

/**
 * End-to-end order-book stream behavior of {@link BitgetUtaV3StreamingMarketDataService}.
 *
 * <p>The UTA v3 depth channel pushes levels as positional arrays ({@code ["99756.7","23.9774"]},
 * see the Bitget "Depth Channel" documentation), which {@code BitgetUtaV3OrderBookLevel#fromArray}
 * maps onto {@code [price, size]}. A snapshot push must produce an {@link OrderBook}; a partial
 * envelope without a {@code data} array must be skipped, not terminate the stream.
 */
class BitgetUtaV3StreamingMarketDataServiceTest {

  private BitgetUtaV3PrivateStreamingService service;
  private PublishSubject<BitgetUtaV3WsNotification> channelSubject;
  private BitgetUtaV3StreamingMarketDataService marketDataService;

  @BeforeEach
  void setUp() {
    service = mock(BitgetUtaV3PrivateStreamingService.class);
    channelSubject = PublishSubject.create();
    when(service.subscribeChannel(isNull(), any(BitgetUtaV3Channel.class)))
        .thenReturn(channelSubject);
    marketDataService = new BitgetUtaV3StreamingMarketDataService(service);
  }

  private static BitgetUtaV3WsNotification push(BitgetUtaV3Action action, String dataJson)
      throws Exception {
    BitgetUtaV3WsNotification.BitgetUtaV3WsNotificationBuilder<?, ?> builder =
        BitgetUtaV3WsNotification.builder()
            .action(action)
            .channel(BitgetUtaV3Channel.builder().topic("books").build())
            .timestamp(1_700_000_000_000L);
    if (dataJson != null) {
      builder.payloadItem(new ObjectMapper().readTree(dataJson));
    }
    return builder.build();
  }

  @Test
  void snapshotPushProducesOrderBook() throws Exception {
    TestObserver<OrderBook> observer = marketDataService.getOrderBook(CurrencyPair.BTC_USDT).test();

    channelSubject.onNext(
        push(
            BitgetUtaV3Action.SNAPSHOT,
            "{\"a\":[[\"99756.7\",\"23.9774\"]],\"b\":[[\"99756.6\",\"0.0128\"]],"
                + "\"pseq\":0,\"seq\":1304314508780744705,\"maxDepth\":\"50\",\"ts\":\"1746698732562\"}"));

    observer.assertNoErrors();
    observer.assertValueCount(1);
    assertThat(observer.values().get(0).getAsks()).hasSize(1);
    assertThat(observer.values().get(0).getAsks().get(0).getLimitPrice())
        .isEqualByComparingTo("99756.7");
    assertThat(observer.values().get(0).getBids()).hasSize(1);
  }

  @Test
  void pushWithoutDataArrayDoesNotKillOrderBookStream() throws Exception {
    TestObserver<OrderBook> observer = marketDataService.getOrderBook(CurrencyPair.BTC_USDT).test();

    channelSubject.onNext(push(BitgetUtaV3Action.UPDATE, null));

    observer.assertNoErrors();
    assertThat(observer.values()).isEmpty();
  }

  /**
   * A book observable that is disposed and later resubscribed (supported Rx pattern) must keep
   * working: the assembler is evicted when the last subscriber leaves, and the next push must
   * recreate it instead of terminating the stream with a null assembler.
   */
  @Test
  void resubscribedOrderBookRecreatesAssembler() throws Exception {
    Observable<OrderBook> books = marketDataService.getOrderBook(CurrencyPair.BTC_USDT);

    TestObserver<OrderBook> first = books.test();
    channelSubject.onNext(
        push(
            BitgetUtaV3Action.SNAPSHOT,
            "{\"a\":[[\"99756.7\",\"23.9774\"]],\"b\":[[\"99756.6\",\"0.0128\"]],"
                + "\"pseq\":0,\"seq\":1304314508780744705,\"maxDepth\":\"50\",\"ts\":\"1746698732562\"}"));
    first.assertNoErrors();
    assertThat(first.values()).hasSize(1);
    first.dispose();

    TestObserver<OrderBook> second = books.test();
    channelSubject.onNext(
        push(
            BitgetUtaV3Action.SNAPSHOT,
            "{\"a\":[[\"99757.1\",\"24.1\"]],\"b\":[[\"99757.0\",\"0.02\"]],"
                + "\"pseq\":0,\"seq\":1304314508780744706,\"maxDepth\":\"50\",\"ts\":\"1746698732563\"}"));

    second.assertNoErrors();
    assertThat(second.values()).hasSize(1);
    assertThat(second.values().get(0).getAsks().get(0).getLimitPrice())
        .isEqualByComparingTo("99757.1");
  }
}
