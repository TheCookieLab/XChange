package info.bitrich.xchangestream.krakenfutures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import info.bitrich.xchangestream.service.netty.StreamingObjectMapperHelper;
import io.reactivex.rxjava3.core.Observable;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.OrderBook;

/** Futures WS sequence continuity: gap detection, snapshot rebuild, private event dedup. */
class KrakenFuturesStreamingSequenceTest {

  private static final org.knowm.xchange.instrument.Instrument INSTRUMENT = CurrencyPair.BTC_USD;

  private final ObjectMapper objectMapper = StreamingObjectMapperHelper.getObjectMapper();

  KrakenFuturesStreamingService service;
  KrakenFuturesStreamingMarketDataService marketData;
  KrakenFuturesStreamingTradeService tradeService;

  @BeforeEach
  void setUp() {
    service = mock(KrakenFuturesStreamingService.class);
    when(service.subscribeChannel(org.mockito.ArgumentMatchers.any()))
        .thenReturn(Observable.never());
    marketData = new KrakenFuturesStreamingMarketDataService(service);
    tradeService = new KrakenFuturesStreamingTradeService(service);
  }

  private JsonNode json(String body) throws Exception {
    return objectMapper.readTree(body);
  }

  private String snapshotBody(
      long seq, String bidPrice, String bidQty, String askPrice, String askQty) {
    return "{\"feed\":\"book_snapshot\",\"product_id\":\"PI_XBTUSD\",\"timestamp\":\"2022-01-01T00:00:00.000Z\",\"seq\":"
        + seq
        + ",\"bids\":[{\"price\":\""
        + bidPrice
        + "\",\"qty\":\""
        + bidQty
        + "\"}],\"asks\":[{\"price\":\""
        + askPrice
        + "\",\"qty\":\""
        + askQty
        + "\"}]}";
  }

  private String deltaBody(long seq, String side, String price, String qty) {
    return "{\"feed\":\"book\",\"product_id\":\"PI_XBTUSD\",\"side\":\""
        + side
        + "\",\"seq\":"
        + seq
        + ",\"price\":\""
        + price
        + "\",\"qty\":\""
        + qty
        + "\",\"timestamp\":\"2022-01-01T00:00:01.000Z\"}";
  }

  @Test
  void sequential_deltas_are_applied() throws Exception {
    when(service.subscribeChannel(eq("bookPF_XBTUSD")))
        .thenReturn(
            Observable.just(
                json(snapshotBody(100, "100.0", "2", "101.0", "3")),
                json(deltaBody(101, "buy", "100.5", "1")),
                json(deltaBody(102, "sell", "101.5", "0.5"))));

    var observer = marketData.getOrderBook(INSTRUMENT).test();
    observer.awaitCount(3);
    observer.dispose();

    observer.assertNoErrors();
    assertThat(observer.values()).hasSize(3);

    OrderBook book = observer.values().get(2);
    assertThat(book.getBids())
        .extracting(b -> b.getLimitPrice())
        .containsExactly(new BigDecimal("100.5"), new BigDecimal("100.0"));
    assertThat(book.getAsks())
        .extracting(a -> a.getLimitPrice())
        .containsExactly(new BigDecimal("101.0"), new BigDecimal("101.5"));
    verify(service, never()).resubscribeChannel(eq("bookPF_XBTUSD"));
  }

  @Test
  void gap_delta_triggers_resubscribe_and_skips_stale_emissions() throws Exception {
    when(service.subscribeChannel(eq("bookPF_XBTUSD")))
        .thenReturn(
            Observable.just(
                json(snapshotBody(100, "100.0", "2", "101.0", "3")),
                json(deltaBody(103, "buy", "99.0", "1")), // gap: 101-102 missing
                json(snapshotBody(200, "90.0", "5", "95.0", "5"))));

    var observer = marketData.getOrderBook(INSTRUMENT).test();
    observer.awaitCount(2);
    observer.dispose();

    verify(service).resubscribeChannel(eq("bookPF_XBTUSD"));
    OrderBook rebuilt = observer.values().get(1);
    assertThat(rebuilt.getBids().get(0).getLimitPrice()).isEqualByComparingTo("90.0");
  }

  @Test
  void duplicate_delta_is_dropped_without_resubscribe() throws Exception {
    when(service.subscribeChannel(eq("bookPF_XBTUSD")))
        .thenReturn(
            Observable.just(
                json(snapshotBody(100, "100.0", "2", "101.0", "3")),
                json(deltaBody(100, "buy", "99.0", "1")), // duplicate of the snapshot seq
                json(deltaBody(101, "buy", "100.5", "1"))));

    var observer = marketData.getOrderBook(INSTRUMENT).test();
    observer.awaitCount(2);
    observer.dispose();

    verify(service, never()).resubscribeChannel(eq("bookPF_XBTUSD"));
    assertThat(observer.values().get(0).getBids().get(0).getLimitPrice())
        .isEqualByComparingTo("100.0");
    assertThat(observer.values().get(1).getBids().get(0).getLimitPrice())
        .isEqualByComparingTo("100.5");
  }

  @Test
  void fills_are_deduplicated_by_seq_after_redelivery() throws Exception {
    String fillsBody =
        "{\"feed\":\"fills\",\"username\":\"u\",\"fills\":[{\"instrument\":\"PI_XBTUSD\",\"time\":\"2022-01-01T00:00:01.000Z\",\"price\":\"100.0\",\"seq\":5,\"buy\":true,\"qty\":\"1\",\"order_id\":\"o1\",\"cli_ord_id\":null,\"fill_id\":\"f5\",\"fill_type\":\"maker\",\"fee_paid\":\"0.1\",\"fee_currency\":\"USD\"}]}";
    when(service.subscribeChannel(eq("fills")))
        .thenReturn(
            Observable.just(
                json(fillsBody), // seq 5
                json(
                    fillsBody
                        .replace("\"seq\":5", "\"seq\":6")
                        .replace("\"fill_id\":\"f5\"", "\"fill_id\":\"f6\"")), // seq 6
                json(
                    fillsBody
                        .replace("\"seq\":5", "\"seq\":6")
                        .replace(
                            "\"fill_id\":\"f5\"", "\"fill_id\":\"f6\"")))); // redelivered seq 6
    KrakenFuturesStreamingTradeService serviceUnderTest =
        new KrakenFuturesStreamingTradeService(service);

    var observer = serviceUnderTest.getUserTrades().test();
    observer.awaitCount(3);
    observer.dispose();

    assertThat(observer.values()).hasSize(2);
    assertThat(observer.values().get(0).getId()).isEqualTo("f5");
    assertThat(observer.values().get(1).getId()).isEqualTo("f6");
  }
}
