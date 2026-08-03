package info.bitrich.xchangestream.kalshi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import info.bitrich.xchangestream.kalshi.dto.KalshiWsMarketLifecycle;
import info.bitrich.xchangestream.service.netty.StreamingObjectMapperHelper;
import io.reactivex.rxjava3.core.Observable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trade;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.exceptions.InstrumentNotValidException;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.prediction.PredictionMarketContract;

/**
 * Order-book snapshot/delta application, sequence-gap surfacing, and channel wiring for {@link
 * KalshiStreamingMarketDataService}, all driven by scripted messages without a live WebSocket.
 */
class KalshiStreamingMarketDataServiceTest {

  private static final String TICKER = "KXSB-26";
  private static final PredictionMarketContract CONTRACT =
      new PredictionMarketContract("kalshi", null, TICKER, "YES", Currency.USD);
  private static final ObjectMapper MAPPER = StreamingObjectMapperHelper.getObjectMapper();

  private static final String SNAPSHOT_SEQ_2 =
      "{\"type\":\"orderbook_snapshot\",\"sid\":7,\"seq\":2,\"msg\":{"
          + "\"market_ticker\":\""
          + TICKER
          + "\",\"market_id\":\"9b0f6b43\",\"yes_dollars_fp\":[[\"0.0800\",\"300.00\"],"
          + "[\"0.2200\",\"333.00\"]],\"no_dollars_fp\":[[\"0.5400\",\"20.00\"],"
          + "[\"0.5600\",\"146.00\"]]}}";

  /** Feeds scripted messages through the channel instead of a socket. */
  private static final class FakeService extends KalshiStreamingService {
    private final List<JsonNode> scripted;
    private final List<String> subscriptions = new ArrayList<>();

    FakeService(List<JsonNode> scripted) {
      super("wss://stream.test/ws", null, null);
      this.scripted = scripted;
    }

    @Override
    public Observable<JsonNode> subscribeChannel(String channelName, Object... args) {
      subscriptions.add(channelName + ":" + String.join(",", toStrings(args)));
      return Observable.fromIterable(scripted);
    }

    private static List<String> toStrings(Object... args) {
      List<String> out = new ArrayList<>();
      for (Object arg : args) {
        out.add(String.valueOf(arg));
      }
      return out;
    }
  }

  @Test
  void snapshotThenDeltasRebuildTheGenericYesLegBook() throws Exception {
    FakeService fake =
        new FakeService(
            List.of(
                MAPPER.readTree(SNAPSHOT_SEQ_2),
                MAPPER.readTree(
                    "{\"type\":\"orderbook_delta\",\"sid\":7,\"seq\":3,\"msg\":{"
                        + "\"market_ticker\":\""
                        + TICKER
                        + "\",\"price_dollars\":\"0.2200\",\"delta_fp\":\"-33.00\","
                        + "\"side\":\"yes\",\"ts_ms\":1669149841000}}"),
                MAPPER.readTree(
                    "{\"type\":\"orderbook_delta\",\"sid\":7,\"seq\":4,\"msg\":{"
                        + "\"market_ticker\":\""
                        + TICKER
                        + "\",\"price_dollars\":\"0.5400\",\"delta_fp\":\"-20.00\","
                        + "\"side\":\"no\",\"ts_ms\":1669149842000}}")));
    KalshiStreamingMarketDataService service = new KalshiStreamingMarketDataService(fake);

    List<OrderBook> books = service.getOrderBook(CONTRACT).toList().blockingGet();

    assertEquals(List.of("orderbook_delta:" + TICKER), fake.subscriptions);
    assertEquals(3, books.size());

    OrderBook snapshotBook = books.get(0);
    assertEquals(new BigDecimal("0.2200"), snapshotBook.getBids().get(0).getLimitPrice());
    assertEquals(new BigDecimal("333.00"), snapshotBook.getBids().get(0).getOriginalAmount());
    assertEquals(new BigDecimal("0.0800"), snapshotBook.getBids().get(1).getLimitPrice());
    // RULE_NO_BID_COMPLEMENT (dollar form): NO 0.54/0.56 become YES asks at 0.46/0.44.
    assertEquals(new BigDecimal("0.4400"), snapshotBook.getAsks().get(0).getLimitPrice());
    assertEquals(new BigDecimal("146.00"), snapshotBook.getAsks().get(0).getOriginalAmount());
    assertEquals(new BigDecimal("0.4600"), snapshotBook.getAsks().get(1).getLimitPrice());
    assertEquals(CONTRACT, snapshotBook.getBids().get(0).getInstrument());

    OrderBook afterYesDelta = books.get(1);
    assertEquals(new BigDecimal("300.00"), afterYesDelta.getBids().get(0).getOriginalAmount());

    OrderBook afterNoDelta = books.get(2);
    assertEquals(1, afterNoDelta.getAsks().size(), "the emptied NO level must disappear");
    assertEquals(new BigDecimal("0.4400"), afterNoDelta.getAsks().get(0).getLimitPrice());
  }

  @Test
  void sequenceGapTerminatesTheStreamInsteadOfContinuing() throws Exception {
    FakeService fake =
        new FakeService(
            List.of(
                MAPPER.readTree(SNAPSHOT_SEQ_2),
                MAPPER.readTree(
                    "{\"type\":\"orderbook_delta\",\"sid\":7,\"seq\":4,\"msg\":{"
                        + "\"market_ticker\":\""
                        + TICKER
                        + "\",\"price_dollars\":\"0.2200\",\"delta_fp\":\"-33.00\","
                        + "\"side\":\"yes\"}}")));
    KalshiStreamingMarketDataService service = new KalshiStreamingMarketDataService(fake);

    service
        .getOrderBook(CONTRACT)
        .test()
        .assertError(ExchangeException.class)
        .assertError(
            error ->
                ("Kalshi orderbook sequence gap for "
                        + TICKER
                        + ": expected seq 3 but received 4; resync over REST before continuing")
                    .equals(error.getMessage()));
  }

  @Test
  void deltaBeforeAnySnapshotTerminatesTheStream() throws Exception {
    FakeService fake =
        new FakeService(
            List.of(
                MAPPER.readTree(
                    "{\"type\":\"orderbook_delta\",\"sid\":7,\"seq\":1,\"msg\":{"
                        + "\"market_ticker\":\""
                        + TICKER
                        + "\",\"price_dollars\":\"0.2200\",\"delta_fp\":\"1.00\","
                        + "\"side\":\"yes\"}}")));
    KalshiStreamingMarketDataService service = new KalshiStreamingMarketDataService(fake);

    service
        .getOrderBook(CONTRACT)
        .test()
        .assertError(ExchangeException.class)
        .assertError(
            error -> error.getMessage().contains("before any snapshot"));
  }

  @Test
  void freshSnapshotReanchorsAfterSequenceUncertainty() throws Exception {
    FakeService fake =
        new FakeService(
            List.of(
                MAPPER.readTree(SNAPSHOT_SEQ_2),
                MAPPER.readTree(
                    "{\"type\":\"orderbook_snapshot\",\"sid\":7,\"seq\":9,\"msg\":{"
                        + "\"market_ticker\":\""
                        + TICKER
                        + "\",\"yes_dollars_fp\":[[\"0.6100\",\"5.00\"]],"
                        + "\"no_dollars_fp\":[]}}")));
    KalshiStreamingMarketDataService service = new KalshiStreamingMarketDataService(fake);

    List<OrderBook> books = service.getOrderBook(CONTRACT).toList().blockingGet();
    assertEquals(2, books.size());
    assertEquals(1, books.get(1).getBids().size());
    assertEquals(new BigDecimal("0.6100"), books.get(1).getBids().get(0).getLimitPrice());
    assertTrue(books.get(1).getAsks().isEmpty());
  }

  @Test
  void tradesStreamAdaptsPublicTrades() throws Exception {
    FakeService fake =
        new FakeService(
            List.of(
                MAPPER.readTree(
                    "{\"type\":\"trade\",\"sid\":11,\"msg\":{"
                        + "\"trade_id\":\"d91bc706\",\"market_ticker\":\""
                        + TICKER
                        + "\",\"yes_price_dollars\":\"0.360\",\"no_price_dollars\":\"0.640\","
                        + "\"count_fp\":\"136.00\",\"taker_side\":\"no\",\"ts\":1669149841,"
                        + "\"ts_ms\":1669149841000}}")));
    KalshiStreamingMarketDataService service = new KalshiStreamingMarketDataService(fake);

    Trade trade = service.getTrades(CONTRACT).blockingFirst();

    assertEquals(List.of("trade:" + TICKER), fake.subscriptions);
    assertEquals(OrderType.ASK, trade.getType(), "a NO taker is an ask-side aggressor on YES");
    assertEquals(new BigDecimal("0.360"), trade.getPrice());
    assertEquals(new BigDecimal("136.00"), trade.getOriginalAmount());
    assertEquals(CONTRACT, trade.getInstrument());
    assertEquals(new Date(1669149841000L), trade.getTimestamp());
    assertEquals("d91bc706", trade.getId());
  }

  @Test
  void tickerStreamAdaptsTopOfBook() throws Exception {
    FakeService fake =
        new FakeService(
            List.of(
                MAPPER.readTree(
                    "{\"type\":\"ticker\",\"sid\":11,\"msg\":{"
                        + "\"market_ticker\":\""
                        + TICKER
                        + "\",\"price_dollars\":\"0.480\",\"yes_bid_dollars\":\"0.450\","
                        + "\"yes_ask_dollars\":\"0.530\",\"volume_fp\":\"33896.00\","
                        + "\"ts_ms\":1669149841000}}")));
    KalshiStreamingMarketDataService service = new KalshiStreamingMarketDataService(fake);

    Ticker ticker = service.getTicker(CONTRACT).blockingFirst();

    assertEquals(List.of("ticker:" + TICKER), fake.subscriptions);
    assertEquals(new BigDecimal("0.450"), ticker.getBid());
    assertEquals(new BigDecimal("0.530"), ticker.getAsk());
    assertEquals(new BigDecimal("0.480"), ticker.getLast());
    assertEquals(new BigDecimal("33896.00"), ticker.getVolume());
    assertEquals(CONTRACT, ticker.getInstrument());
  }

  @Test
  void marketLifecycleStreamExposesRawStatusEvents() throws Exception {
    FakeService fake =
        new FakeService(
            List.of(
                MAPPER.readTree(
                    "{\"type\":\"market_lifecycle_v2\",\"sid\":13,\"msg\":{"
                        + "\"market_ticker\":\""
                        + TICKER
                        + "\",\"event_type\":\"determined\",\"result\":\"yes\","
                        + "\"settlement_value\":\"1.00\",\"determination_ts\":1694721600}}")));
    KalshiStreamingMarketDataService service = new KalshiStreamingMarketDataService(fake);

    KalshiWsMarketLifecycle event = service.getMarketLifecycle(CONTRACT).blockingFirst();

    assertEquals(List.of("market_lifecycle_v2:" + TICKER), fake.subscriptions);
    assertEquals(TICKER, event.marketTicker());
    assertEquals("determined", event.eventType());
    assertEquals("yes", event.result());
    assertEquals("1.00", event.settlementValue());
  }

  @Test
  void currencyPairsAreRejectedBeforeAnySubscription() {
    FakeService fake = new FakeService(List.of());
    KalshiStreamingMarketDataService service = new KalshiStreamingMarketDataService(fake);
    // Typed as Instrument so overload resolution reaches the validating methods instead of the
    // interface's legacy CurrencyPair defaults.
    Instrument pair = CurrencyPair.BTC_USD;

    assertThrows(InstrumentNotValidException.class, () -> service.getOrderBook(pair));
    assertThrows(InstrumentNotValidException.class, () -> service.getTrades(pair));
    assertThrows(InstrumentNotValidException.class, () -> service.getMarketLifecycle(pair));
    assertTrue(fake.subscriptions.isEmpty());
  }
}
