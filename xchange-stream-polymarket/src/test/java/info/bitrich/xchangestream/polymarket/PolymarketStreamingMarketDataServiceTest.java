package info.bitrich.xchangestream.polymarket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import info.bitrich.xchangestream.service.netty.StreamingObjectMapperHelper;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.observers.TestObserver;
import java.io.IOException;
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
import org.knowm.xchange.polymarket.PolymarketAdapters;
import org.knowm.xchange.prediction.PredictionMarketContract;

/**
 * Book snapshot/absolute-update application, integrity-violation surfacing, and channel
 * memoization for {@link PolymarketStreamingMarketDataService}, all driven by scripted messages
 * without a live WebSocket.
 */
class PolymarketStreamingMarketDataServiceTest {

  private static final String CONDITION_ID =
      "0x9b0f6b43e1a44c2fb2d3a1e5c7d8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b3c4d5e6";
  private static final String TOKEN_ID =
      "104173557214744537570424345347209544585775842950109756851652855913015295508992";
  private static final String TOKEN_ID_B =
      "71321045679252212594626385532706912750332728571942532289631379312455583992563";
  private static final PredictionMarketContract CONTRACT =
      new PredictionMarketContract("polymarket", null, CONDITION_ID, TOKEN_ID, PolymarketAdapters.COLLATERAL);
  private static final PredictionMarketContract CONTRACT_B =
      new PredictionMarketContract("polymarket", null, CONDITION_ID, TOKEN_ID_B, PolymarketAdapters.COLLATERAL);
  private static final ObjectMapper MAPPER = StreamingObjectMapperHelper.getObjectMapper();

  private static final String SNAPSHOT =
      "{\"event_type\":\"book\",\"market\":\""
          + CONDITION_ID
          + "\",\"asset_id\":\""
          + TOKEN_ID
          + "\",\"timestamp\":\"1669149841000\",\"hash\":\"0xaaa\","
          + "\"bids\":[{\"price\":\"0.40\",\"size\":\"300\"},{\"price\":\"0.44\",\"size\":\"100\"}],"
          + "\"asks\":[{\"price\":\"0.60\",\"size\":\"250\"},{\"price\":\"0.56\",\"size\":\"150\"}]}";

  private static final String SNAPSHOT_B =
      "{\"event_type\":\"book\",\"market\":\""
          + CONDITION_ID
          + "\",\"asset_id\":\""
          + TOKEN_ID_B
          + "\",\"timestamp\":\"1669149841000\",\"hash\":\"0xbbb\","
          + "\"bids\":[{\"price\":\"0.40\",\"size\":\"300\"},{\"price\":\"0.44\",\"size\":\"100\"}],"
          + "\"asks\":[{\"price\":\"0.60\",\"size\":\"250\"},{\"price\":\"0.56\",\"size\":\"150\"}]}";

  /** One wire event carrying level updates for two outcome tokens of the same market. */
  private static final String BATCHED_PRICE_CHANGE =
      "{\"event_type\":\"price_change\",\"market\":\""
          + CONDITION_ID
          + "\",\"timestamp\":\"1669149842000\",\"price_changes\":["
          + "{\"asset_id\":\""
          + TOKEN_ID
          + "\",\"price\":\"0.44\",\"size\":\"0\",\"side\":\"BUY\",\"hash\":\"0xb\","
          + "\"best_bid\":\"0.40\",\"best_ask\":\"0.56\"},"
          + "{\"asset_id\":\""
          + TOKEN_ID_B
          + "\",\"price\":\"0.58\",\"size\":\"75\",\"side\":\"SELL\",\"hash\":\"0xc\","
          + "\"best_bid\":\"0.50\",\"best_ask\":\"0.60\"}]}";

  /** Feeds scripted messages through the channel instead of a socket. */
  private static final class FakeService extends PolymarketStreamingService {
    private final List<JsonNode> scripted;
    private final List<String> subscriptions = new ArrayList<>();

    FakeService(List<JsonNode> scripted) {
      super("wss://stream.test/ws/market", null, null, null);
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

  /**
   * Registers channels with live emitters through the real dispatch path. The base {@code
   * subscribeChannel} errors the subscriber before connect, so this mirrors its registration
   * without the disconnected-state error; {@link #messageHandler} then routes inbound events
   * through the real {@link PolymarketStreamingService#handleMessage} fan-out logic.
   */
  private static final class LiveService extends PolymarketStreamingService {
    LiveService() {
      super("wss://stream.test/ws/market", null, null, null);
    }

    @Override
    public void sendMessage(String message) {
      // No socket in tests; the base would only log "not open" warnings.
    }

    @Override
    public Observable<JsonNode> subscribeChannel(String channelName, Object... args) {
      String subscriptionUniqueId = getSubscriptionUniqueId(channelName, args);
      return Observable.<JsonNode>create(
              emitter ->
                  channels.computeIfAbsent(
                      subscriptionUniqueId,
                      cid -> {
                        Subscription subscription = new Subscription(emitter, channelName, args);
                        try {
                          sendMessage(getSubscribeMessage(channelName, args));
                        } catch (IOException e) {
                          emitter.onError(e);
                        }
                        return subscription;
                      }))
          .share();
    }
  }

  @Test
  void snapshotArrivesWorstFirstAndIsResortedToGenericDepth() throws Exception {
    FakeService fake = new FakeService(List.of(MAPPER.readTree(SNAPSHOT)));
    PolymarketStreamingMarketDataService service =
        new PolymarketStreamingMarketDataService(fake);

    List<OrderBook> books = service.getOrderBook(CONTRACT).toList().blockingGet();

    assertEquals(1, books.size());
    OrderBook book = books.get(0);
    assertEquals(CONTRACT, book.getBids().get(0).getInstrument());
    assertEquals(new Date(1669149841000L), book.getTimeStamp());
    // Generic books are best-first: bids descending, asks ascending.
    assertEquals(new BigDecimal("0.44"), book.getBids().get(0).getLimitPrice());
    assertEquals(new BigDecimal("100"), book.getBids().get(0).getOriginalAmount());
    assertEquals(new BigDecimal("0.40"), book.getBids().get(1).getLimitPrice());
    assertEquals(new BigDecimal("0.56"), book.getAsks().get(0).getLimitPrice());
    assertEquals(new BigDecimal("150"), book.getAsks().get(0).getOriginalAmount());
    assertEquals(new BigDecimal("0.60"), book.getAsks().get(1).getLimitPrice());
    assertEquals(List.of("market:" + TOKEN_ID), fake.subscriptions);
  }

  @Test
  void priceChangesApplyAbsoluteSizesAndZeroRemovesTheLevel() throws Exception {
    FakeService fake =
        new FakeService(
            List.of(
                MAPPER.readTree(SNAPSHOT),
                MAPPER.readTree(
                    "{\"event_type\":\"price_change\",\"market\":\""
                        + CONDITION_ID
                        + "\",\"timestamp\":\"1669149842000\",\"price_changes\":["
                        + "{\"asset_id\":\""
                        + TOKEN_ID
                        + "\",\"price\":\"0.44\",\"size\":\"0\",\"side\":\"BUY\",\"hash\":\"0xb\","
                        + "\"best_bid\":\"0.40\",\"best_ask\":\"0.56\"},"
                        + "{\"asset_id\":\""
                        + TOKEN_ID
                        + "\",\"price\":\"0.58\",\"size\":\"75\",\"side\":\"SELL\",\"hash\":\"0xb\","
                        + "\"best_bid\":\"0.40\",\"best_ask\":\"0.56\"}]}")));
    PolymarketStreamingMarketDataService service =
        new PolymarketStreamingMarketDataService(fake);

    List<OrderBook> books = service.getOrderBook(CONTRACT).toList().blockingGet();

    assertEquals(2, books.size());
    OrderBook updated = books.get(1);
    assertEquals(new Date(1669149842000L), updated.getTimeStamp());
    // The zero-size change removed the 0.44 bid; the absolute change inserted a 0.58 ask.
    assertEquals(1, updated.getBids().size());
    assertEquals(new BigDecimal("0.40"), updated.getBids().get(0).getLimitPrice());
    assertEquals(3, updated.getAsks().size());
    assertEquals(new BigDecimal("0.56"), updated.getAsks().get(0).getLimitPrice());
    assertEquals(new BigDecimal("0.58"), updated.getAsks().get(1).getLimitPrice());
    assertEquals(new BigDecimal("75"), updated.getAsks().get(1).getOriginalAmount());
  }

  @Test
  void priceChangeBeforeAnySnapshotTerminatesTheStream() throws Exception {
    FakeService fake =
        new FakeService(
            List.of(
                MAPPER.readTree(
                    "{\"event_type\":\"price_change\",\"market\":\""
                        + CONDITION_ID
                        + "\",\"timestamp\":\"1669149842000\",\"price_changes\":[{"
                        + "\"asset_id\":\""
                        + TOKEN_ID
                        + "\",\"price\":\"0.44\",\"size\":\"10\",\"side\":\"BUY\"}]}")));
    PolymarketStreamingMarketDataService service =
        new PolymarketStreamingMarketDataService(fake);

    service
        .getOrderBook(CONTRACT)
        .test()
        .assertError(ExchangeException.class)
        .assertError(
            error ->
                ("Polymarket price_change for "
                        + TOKEN_ID
                        + " arrived before any book snapshot; resync over REST before continuing")
                    .equals(error.getMessage()));
  }

  @Test
  void priceChangeForAnUnexpectedAssetTerminatesTheStream() throws Exception {
    FakeService fake =
        new FakeService(
            List.of(
                MAPPER.readTree(SNAPSHOT),
                MAPPER.readTree(
                    "{\"event_type\":\"price_change\",\"market\":\""
                        + CONDITION_ID
                        + "\",\"timestamp\":\"1669149842000\",\"price_changes\":[{"
                        + "\"asset_id\":\"999\",\"price\":\"0.44\",\"size\":\"10\","
                        + "\"side\":\"BUY\"}]}")));
    PolymarketStreamingMarketDataService service =
        new PolymarketStreamingMarketDataService(fake);

    service
        .getOrderBook(CONTRACT)
        .test()
        .assertError(ExchangeException.class)
        .assertError(
            error ->
                ("Polymarket price_change for unexpected asset 999 on the channel for asset "
                        + TOKEN_ID)
                    .equals(error.getMessage()));
  }

  @Test
  void priceChangeWithAnUnrecognizedSideTerminatesTheStream() throws Exception {
    FakeService fake =
        new FakeService(
            List.of(
                MAPPER.readTree(SNAPSHOT),
                MAPPER.readTree(
                    "{\"event_type\":\"price_change\",\"market\":\""
                        + CONDITION_ID
                        + "\",\"timestamp\":\"1669149842000\",\"price_changes\":[{"
                        + "\"asset_id\":\""
                        + TOKEN_ID
                        + "\",\"price\":\"0.44\",\"size\":\"10\",\"side\":\"HOLD\"}]}")));
    PolymarketStreamingMarketDataService service =
        new PolymarketStreamingMarketDataService(fake);

    service
        .getOrderBook(CONTRACT)
        .test()
        .assertError(ExchangeException.class)
        .assertError(
            error ->
                "Polymarket price_change has unrecognized side: HOLD"
                    .equals(error.getMessage()));
  }

  @Test
  void freshSnapshotReanchorsTheBook() throws Exception {
    FakeService fake =
        new FakeService(
            List.of(
                MAPPER.readTree(SNAPSHOT),
                MAPPER.readTree(
                    "{\"event_type\":\"price_change\",\"market\":\""
                        + CONDITION_ID
                        + "\",\"timestamp\":\"1669149842000\",\"price_changes\":[{"
                        + "\"asset_id\":\""
                        + TOKEN_ID
                        + "\",\"price\":\"0.44\",\"size\":\"0\",\"side\":\"BUY\"}]}"),
                MAPPER.readTree(
                    "{\"event_type\":\"book\",\"market\":\""
                        + CONDITION_ID
                        + "\",\"asset_id\":\""
                        + TOKEN_ID
                        + "\",\"timestamp\":\"1669149843000\",\"hash\":\"0xccc\","
                        + "\"bids\":[{\"price\":\"0.50\",\"size\":\"10\"}],"
                        + "\"asks\":[]}")));
    PolymarketStreamingMarketDataService service =
        new PolymarketStreamingMarketDataService(fake);

    List<OrderBook> books = service.getOrderBook(CONTRACT).toList().blockingGet();

    assertEquals(3, books.size());
    OrderBook reanchored = books.get(2);
    assertEquals(new Date(1669149843000L), reanchored.getTimeStamp());
    assertEquals(1, reanchored.getBids().size());
    assertEquals(new BigDecimal("0.50"), reanchored.getBids().get(0).getLimitPrice());
    assertTrue(reanchored.getAsks().isEmpty());
  }

  @Test
  void batchedPriceChangeEventUpdatesBothOrderBooksWithoutError() throws Exception {
    LiveService service = new LiveService();
    PolymarketStreamingMarketDataService marketData =
        new PolymarketStreamingMarketDataService(service);

    TestObserver<OrderBook> bookA = marketData.getOrderBook(CONTRACT).test();
    TestObserver<OrderBook> bookB = marketData.getOrderBook(CONTRACT_B).test();

    service.messageHandler(SNAPSHOT);
    service.messageHandler(SNAPSHOT_B);
    // One wire event carrying changes for both tokens must fan out per asset: neither book may
    // encounter the other token's change (previously the whole node was routed by its first asset
    // and the second book threw, terminating the stream).
    service.messageHandler(BATCHED_PRICE_CHANGE);

    bookA.assertNoErrors().assertValueCount(2);
    bookB.assertNoErrors().assertValueCount(2);
    // Each book applied only its own asset's change: A's zero-size 0.44 bid was removed, B's
    // absolute 0.58 ask (size 75) was inserted.
    OrderBook updatedA = bookA.values().get(1);
    assertEquals(new BigDecimal("0.40"), updatedA.getBids().get(0).getLimitPrice());
    assertEquals(1, updatedA.getBids().size());
    OrderBook updatedB = bookB.values().get(1);
    assertEquals(new BigDecimal("0.58"), updatedB.getAsks().get(1).getLimitPrice());
    assertEquals(new BigDecimal("75"), updatedB.getAsks().get(1).getOriginalAmount());
    assertEquals(new Date(1669149842000L), updatedB.getTimeStamp());
  }

  @Test
  void tradesStreamAdaptsLastTradePriceWithTheSideRule() throws Exception {
    FakeService fake =
        new FakeService(
            List.of(
                MAPPER.readTree(SNAPSHOT),
                MAPPER.readTree(
                    "{\"event_type\":\"last_trade_price\",\"market\":\""
                        + CONDITION_ID
                        + "\",\"asset_id\":\""
                        + TOKEN_ID
                        + "\",\"price\":\"0.56\",\"size\":\"4\",\"fee_rate_bps\":\"0\","
                        + "\"side\":\"SELL\",\"timestamp\":\"1669149841500\","
                        + "\"transaction_hash\":\"0xtx1\"}"),
                MAPPER.readTree(
                    "{\"event_type\":\"last_trade_price\",\"market\":\""
                        + CONDITION_ID
                        + "\",\"asset_id\":\""
                        + TOKEN_ID
                        + "\",\"price\":\"0.57\",\"size\":\"2\",\"fee_rate_bps\":\"0\","
                        + "\"side\":\"BUY\",\"timestamp\":\"1669149841600\","
                        + "\"transaction_hash\":null}")));
    PolymarketStreamingMarketDataService service =
        new PolymarketStreamingMarketDataService(fake);

    List<Trade> trades = service.getTrades(CONTRACT).toList().blockingGet();

    // The book event must not leak into the trades stream.
    assertEquals(2, trades.size());
    Trade sell = trades.get(0);
    assertEquals(OrderType.ASK, sell.getType(), "a SELL aggressor reads as ask-side");
    assertEquals(new BigDecimal("0.56"), sell.getPrice());
    assertEquals(new BigDecimal("4"), sell.getOriginalAmount());
    assertEquals(CONTRACT, sell.getInstrument());
    assertEquals(new Date(1669149841500L), sell.getTimestamp());
    assertEquals("0xtx1", sell.getId());
    Trade buy = trades.get(1);
    assertEquals(OrderType.BID, buy.getType());
    assertNull(buy.getId(), "a null transaction hash stays a null id");
  }

  @Test
  void tickerStreamReadsTopOfBookFromSnapshotsAndBestBidAskFromChanges() throws Exception {
    FakeService fake =
        new FakeService(
            List.of(
                MAPPER.readTree(SNAPSHOT),
                MAPPER.readTree(
                    "{\"event_type\":\"price_change\",\"market\":\""
                        + CONDITION_ID
                        + "\",\"timestamp\":\"1669149842000\",\"price_changes\":[{"
                        + "\"asset_id\":\""
                        + TOKEN_ID
                        + "\",\"price\":\"0.44\",\"size\":\"10\",\"side\":\"BUY\","
                        + "\"best_bid\":\"0.45\",\"best_ask\":\"0.55\"}]}")));
    PolymarketStreamingMarketDataService service =
        new PolymarketStreamingMarketDataService(fake);

    List<Ticker> tickers = service.getTicker(CONTRACT).toList().blockingGet();

    assertEquals(2, tickers.size());
    Ticker fromSnapshot = tickers.get(0);
    assertEquals(CONTRACT, fromSnapshot.getInstrument());
    assertEquals(new BigDecimal("0.44"), fromSnapshot.getBid());
    assertEquals(new BigDecimal("0.56"), fromSnapshot.getAsk());
    assertEquals(new Date(1669149841000L), fromSnapshot.getTimestamp());
    Ticker fromChange = tickers.get(1);
    assertEquals(new BigDecimal("0.45"), fromChange.getBid());
    assertEquals(new BigDecimal("0.55"), fromChange.getAsk());
    assertEquals(new Date(1669149842000L), fromChange.getTimestamp());
  }

  @Test
  void marketChannelIsSubscribedOncePerTokenAcrossAllStreams() {
    FakeService fake = new FakeService(List.of());
    PolymarketStreamingMarketDataService service =
        new PolymarketStreamingMarketDataService(fake);

    service.getOrderBook(CONTRACT).subscribe(ignored -> {}, error -> {});
    service.getTrades(CONTRACT).subscribe(ignored -> {}, error -> {});
    service.getTicker(CONTRACT).subscribe(ignored -> {}, error -> {});

    // The provider multiplexes every market event onto one channel per token, and the base
    // streaming service would orphan any second subscriber — the subscription must be shared.
    assertEquals(List.of("market:" + TOKEN_ID), fake.subscriptions);
  }

  @Test
  void currencyPairsAreRejectedBeforeAnySubscription() {
    FakeService fake = new FakeService(List.of());
    PolymarketStreamingMarketDataService service =
        new PolymarketStreamingMarketDataService(fake);
    // Typed as Instrument so overload resolution reaches the validating methods instead of the
    // interface's legacy CurrencyPair defaults.
    Instrument pair = CurrencyPair.BTC_USD;

    assertThrows(InstrumentNotValidException.class, () -> service.getOrderBook(pair));
    assertThrows(InstrumentNotValidException.class, () -> service.getTrades(pair));
    assertThrows(InstrumentNotValidException.class, () -> service.getTicker(pair));
    assertTrue(fake.subscriptions.isEmpty());
  }
}
