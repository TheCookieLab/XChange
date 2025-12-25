package info.bitrich.xchangestream.coinbase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.marketdata.CandleStick;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.trade.LimitOrder;
import info.bitrich.xchangestream.coinbase.CoinbaseStreamingTestUtils.StubStreamingService;
import info.bitrich.xchangestream.coinbase.adapters.CoinbaseStreamingAdapters;

class CoinbaseStreamingMarketDataServiceTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  void orderBookStateAppliesSnapshotAndUpdates() throws Exception {
    CoinbaseStreamingMarketDataService.OrderBookSnapshotProvider provider =
        pair -> {
          throw new AssertionError("Snapshot provider should not be invoked for snapshot events");
        };
    CoinbaseStreamingMarketDataService.OrderBookState state =
        new CoinbaseStreamingMarketDataService.OrderBookState(CurrencyPair.BTC_USD, provider);

    // Snapshot with bids/asks arrays (correct format)
    JsonNode snapshot =
        MAPPER.readTree(
            "{\n"
                + "  \"events\": [\n"
                + "    {\n"
                + "      \"type\": \"snapshot\",\n"
                + "      \"product_id\": \"BTC-USD\",\n"
                + "      \"sequence\": 42,\n"
                + "      \"bids\": [\n"
                + "        [\"100.00\", \"2.0\"],\n"
                + "        [\"99.50\", \"1.5\"]\n"
                + "      ],\n"
                + "      \"asks\": [\n"
                + "        [\"110.00\", \"3.0\"],\n"
                + "        [\"110.50\", \"2.5\"]\n"
                + "      ]\n"
                + "    }\n"
                + "  ]\n"
                + "}");

    Maybe<OrderBook> maybeSnapshot =
        state.process(CoinbaseStreamingAdapters.toStreamingMessage(snapshot));
    assertNotNull(maybeSnapshot);
    OrderBook book = maybeSnapshot.blockingGet();
    assertNotNull(book);
    
    // Verify snapshot populated the order book correctly
    assertEquals(2, book.getBids().size(), "Snapshot should have 2 bid levels");
    assertEquals(2, book.getAsks().size(), "Snapshot should have 2 ask levels");
    
    // Bids are sorted descending (highest first)
    assertEquals("100.00", book.getBids().get(0).getLimitPrice().toPlainString());
    assertEquals("2.0", book.getBids().get(0).getOriginalAmount().toPlainString());
    assertEquals("99.50", book.getBids().get(1).getLimitPrice().toPlainString());
    assertEquals("1.5", book.getBids().get(1).getOriginalAmount().toPlainString());
    
    // Asks are sorted ascending (lowest first)
    assertEquals("110.00", book.getAsks().get(0).getLimitPrice().toPlainString());
    assertEquals("3.0", book.getAsks().get(0).getOriginalAmount().toPlainString());
    assertEquals("110.50", book.getAsks().get(1).getLimitPrice().toPlainString());
    assertEquals("2.5", book.getAsks().get(1).getOriginalAmount().toPlainString());
    
    // Verify hasSnapshot flag is set
    assertTrue(getHasSnapshotFlag(state), "hasSnapshot should be true after snapshot");

    // Apply updates
    JsonNode update =
        MAPPER.readTree(
            "{\n"
                + "  \"events\": [\n"
                + "    {\n"
                + "      \"type\": \"l2update\",\n"
                + "      \"product_id\": \"BTC-USD\",\n"
                + "      \"sequence\": 43,\n"
                + "      \"updates\": [\n"
                + "        {\"side\": \"bid\", \"price_level\": \"100.00\", \"new_quantity\": \"1.5\"},\n"
                + "        {\"side\": \"ask\", \"price_level\": \"110.00\", \"new_quantity\": \"0\"}\n"
                + "      ]\n"
                + "    }\n"
                + "  ]\n"
                + "}");

    Maybe<OrderBook> maybeUpdate =
        state.process(CoinbaseStreamingAdapters.toStreamingMessage(update));
    assertNotNull(maybeUpdate);
    OrderBook updated = maybeUpdate.blockingGet();
    assertNotNull(updated);
    
    // Verify updates were applied correctly
    assertEquals(2, updated.getBids().size(), "Should still have 2 bid levels");
    assertEquals("100.00", updated.getBids().get(0).getLimitPrice().toPlainString());
    assertEquals("1.5", updated.getBids().get(0).getOriginalAmount().toPlainString(), 
        "Bid at 100.00 should be updated to 1.5");
    assertEquals(1, updated.getAsks().size(), "Ask at 110.00 should be removed");
    assertEquals("110.50", updated.getAsks().get(0).getLimitPrice().toPlainString());
  }

  @Test
  void sequenceGapTriggersSnapshotRecovery() throws Exception {
    RecordingSnapshotProvider provider =
        new RecordingSnapshotProvider(
            Arrays.asList(
                orderBook(
                    Collections.singletonList(limitOrder(Order.OrderType.ASK, "110", "3")),
                    Collections.singletonList(limitOrder(Order.OrderType.BID, "100", "2"))),
                orderBook(
                    Collections.singletonList(limitOrder(Order.OrderType.ASK, "110", "2")),
                    Arrays.asList(
                        limitOrder(Order.OrderType.BID, "100", "1"),
                        limitOrder(Order.OrderType.BID, "101", "1.5")))));

    CoinbaseStreamingMarketDataService.OrderBookState state =
        new CoinbaseStreamingMarketDataService.OrderBookState(CurrencyPair.BTC_USD, provider);

    JsonNode firstUpdate =
        MAPPER.readTree(
            "{\n"
                + "  \"events\": [\n"
                + "    {\n"
                + "      \"type\": \"l2update\",\n"
                + "      \"product_id\": \"BTC-USD\",\n"
                + "      \"sequence\": 100,\n"
                + "      \"updates\": [\n"
                + "        {\"side\": \"bid\", \"price_level\": \"100\", \"new_quantity\": \"2.5\"}\n"
                + "      ]\n"
                + "    }\n"
                + "  ]\n"
                + "}");

    OrderBook first =
        state.process(CoinbaseStreamingAdapters.toStreamingMessage(firstUpdate)).blockingGet();
    assertEquals(1, provider.callCount());
    assertEquals("2.5", first.getBids().get(0).getOriginalAmount().toPlainString());
    assertEquals("3", first.getAsks().get(0).getOriginalAmount().toPlainString());

    JsonNode gapUpdate =
        MAPPER.readTree(
            "{\n"
                + "  \"events\": [\n"
                + "    {\n"
                + "      \"type\": \"l2update\",\n"
                + "      \"product_id\": \"BTC-USD\",\n"
                + "      \"sequence\": 102,\n"
                + "      \"updates\": [\n"
                + "        {\"side\": \"ask\", \"price_level\": \"110\", \"new_quantity\": \"1.8\"}\n"
                + "      ]\n"
                + "    }\n"
                + "  ]\n"
                + "}");

    OrderBook recovered =
        state.process(CoinbaseStreamingAdapters.toStreamingMessage(gapUpdate)).blockingGet();
    assertEquals(2, provider.callCount());
    assertEquals(2, recovered.getBids().size());
    assertEquals("1.8", recovered.getAsks().get(0).getOriginalAmount().toPlainString());
    assertEquals("101", recovered.getBids().get(0).getLimitPrice().toPlainString());
    assertEquals("1.5", recovered.getBids().get(0).getOriginalAmount().toPlainString());
    assertEquals("100", recovered.getBids().get(1).getLimitPrice().toPlainString());
    assertEquals("1", recovered.getBids().get(1).getOriginalAmount().toPlainString());
  }

  @Test
  void getCandlesSubscribesAndMapsPayload() throws Exception {
    ExchangeSpecification spec = new ExchangeSpecification(CoinbaseStreamingExchange.class);
    spec.setExchangeSpecificParametersItem(
        CoinbaseStreamingExchange.PARAM_DEFAULT_CANDLE_PRODUCT_TYPE, "SPOT");

    JsonNode message =
        MAPPER.readTree(
            "{\n"
                + "  \"channel\": \"candles\",\n"
                + "  \"events\": [\n"
                + "    {\n"
                + "      \"type\": \"snapshot\",\n"
                + "      \"candles\": [\n"
                + "        {\n"
                + "          \"product_id\": \"BTC-USD\",\n"
                + "          \"start\": \"1704067200\",\n"
                + "          \"open\": \"100\",\n"
                + "          \"close\": \"110\",\n"
                + "          \"high\": \"120\",\n"
                + "          \"low\": \"90\",\n"
                + "          \"volume\": \"5\"\n"
                + "        }\n"
                + "      ]\n"
                + "    }\n"
                + "  ]\n"
                + "}");

    StubStreamingService streamingService = new StubStreamingService(Observable.just(message));
    CoinbaseStreamingMarketDataService service =
        new CoinbaseStreamingMarketDataService(streamingService, null, spec);

    CoinbaseCandleSubscriptionParams params =
        new CoinbaseCandleSubscriptionParams(CoinbaseCandleGranularity.ONE_MINUTE, "SPOT");

    List<CandleStick> candles =
        service.getCandles(CurrencyPair.BTC_USD, params).toList().blockingGet();

    assertEquals(1, candles.size());
    CandleStick candle = candles.get(0);
    assertEquals(new BigDecimal("100"), candle.getOpen());
    assertEquals(new BigDecimal("110"), candle.getClose());
    assertEquals(new BigDecimal("120"), candle.getHigh());
    assertEquals(new BigDecimal("90"), candle.getLow());
    assertEquals(new BigDecimal("5"), candle.getVolume());
    // Verify timestamp: 1704067200 epoch seconds = 2024-01-01T00:00:00Z
    assertEquals(1704067200000L, candle.getTimestamp().toInstant().toEpochMilli());

    CoinbaseSubscriptionRequest request = streamingService.lastRequest();
    assertEquals(CoinbaseChannel.CANDLES, request.getChannel());
    assertEquals(Collections.singletonList("BTC-USD"), request.getProductIds());
    assertEquals("ONE_MINUTE", request.getChannelArgs().get("granularity"));
    assertEquals("SPOT", request.getChannelArgs().get("product_type"));
  }

  @Test
  void snapshotParsesBidsAsksArraysCorrectly() throws Exception {
    // This test specifically verifies the fix for the snapshot parsing issue
    CoinbaseStreamingMarketDataService.OrderBookState state =
        new CoinbaseStreamingMarketDataService.OrderBookState(
            CurrencyPair.BTC_USD, null);

    JsonNode snapshot =
        MAPPER.readTree(
            "{\n"
                + "  \"events\": [\n"
                + "    {\n"
                + "      \"type\": \"snapshot\",\n"
                + "      \"product_id\": \"BTC-USD\",\n"
                + "      \"sequence\": 100,\n"
                + "      \"bids\": [\n"
                + "        [\"50000.00\", \"1.5\"],\n"
                + "        [\"49999.00\", \"2.0\"],\n"
                + "        [\"49998.00\", \"0.5\"]\n"
                + "      ],\n"
                + "      \"asks\": [\n"
                + "        [\"50001.00\", \"1.0\"],\n"
                + "        [\"50002.00\", \"1.5\"],\n"
                + "        [\"50003.00\", \"2.0\"]\n"
                + "      ]\n"
                + "    }\n"
                + "  ]\n"
                + "}");

    Maybe<OrderBook> result = state.process(CoinbaseStreamingAdapters.toStreamingMessage(snapshot));
    assertNotNull(result, "Snapshot should produce an OrderBook");
    
    OrderBook book = result.blockingGet();
    assertNotNull(book, "OrderBook should not be null");
    
    // Verify all bid levels are populated
    assertEquals(3, book.getBids().size(), "Snapshot should populate all 3 bid levels");
    assertEquals("50000.00", book.getBids().get(0).getLimitPrice().toPlainString());
    assertEquals("1.5", book.getBids().get(0).getOriginalAmount().toPlainString());
    assertEquals("49999.00", book.getBids().get(1).getLimitPrice().toPlainString());
    assertEquals("2.0", book.getBids().get(1).getOriginalAmount().toPlainString());
    assertEquals("49998.00", book.getBids().get(2).getLimitPrice().toPlainString());
    assertEquals("0.5", book.getBids().get(2).getOriginalAmount().toPlainString());
    
    // Verify all ask levels are populated
    assertEquals(3, book.getAsks().size(), "Snapshot should populate all 3 ask levels");
    assertEquals("50001.00", book.getAsks().get(0).getLimitPrice().toPlainString());
    assertEquals("1.0", book.getAsks().get(0).getOriginalAmount().toPlainString());
    assertEquals("50002.00", book.getAsks().get(1).getLimitPrice().toPlainString());
    assertEquals("1.5", book.getAsks().get(1).getOriginalAmount().toPlainString());
    assertEquals("50003.00", book.getAsks().get(2).getLimitPrice().toPlainString());
    assertEquals("2.0", book.getAsks().get(2).getOriginalAmount().toPlainString());
    
    // Verify hasSnapshot flag is set
    assertTrue(getHasSnapshotFlag(state), "hasSnapshot flag should be true");
  }

  @Test
  void snapshotIgnoresZeroSizeLevels() throws Exception {
    CoinbaseStreamingMarketDataService.OrderBookState state =
        new CoinbaseStreamingMarketDataService.OrderBookState(
            CurrencyPair.BTC_USD, null);

    JsonNode snapshot =
        MAPPER.readTree(
            "{\n"
                + "  \"events\": [\n"
                + "    {\n"
                + "      \"type\": \"snapshot\",\n"
                + "      \"product_id\": \"BTC-USD\",\n"
                + "      \"sequence\": 100,\n"
                + "      \"bids\": [\n"
                + "        [\"100.00\", \"2.0\"],\n"
                + "        [\"99.00\", \"0\"],\n"
                + "        [\"98.00\", \"0.0\"]\n"
                + "      ],\n"
                + "      \"asks\": [\n"
                + "        [\"110.00\", \"3.0\"],\n"
                + "        [\"111.00\", \"0\"]\n"
                + "      ]\n"
                + "    }\n"
                + "  ]\n"
                + "}");

    OrderBook book =
        state.process(CoinbaseStreamingAdapters.toStreamingMessage(snapshot)).blockingGet();
    
    // Zero-size levels should be excluded
    assertEquals(1, book.getBids().size(), "Zero-size bid levels should be excluded");
    assertEquals("100.00", book.getBids().get(0).getLimitPrice().toPlainString());
    assertEquals(1, book.getAsks().size(), "Zero-size ask levels should be excluded");
    assertEquals("110.00", book.getAsks().get(0).getLimitPrice().toPlainString());
  }

  @Test
  void snapshotHandlesEmptyArrays() throws Exception {
    CoinbaseStreamingMarketDataService.OrderBookState state =
        new CoinbaseStreamingMarketDataService.OrderBookState(
            CurrencyPair.BTC_USD, null);

    JsonNode snapshot =
        MAPPER.readTree(
            "{\n"
                + "  \"events\": [\n"
                + "    {\n"
                + "      \"type\": \"snapshot\",\n"
                + "      \"product_id\": \"BTC-USD\",\n"
                + "      \"sequence\": 100,\n"
                + "      \"bids\": [],\n"
                + "      \"asks\": [\n"
                + "        [\"110.00\", \"3.0\"]\n"
                + "      ]\n"
                + "    }\n"
                + "  ]\n"
                + "}");

    OrderBook book = state.process(CoinbaseStreamingAdapters.toStreamingMessage(snapshot)).blockingGet();
    
    assertTrue(book.getBids().isEmpty(), "Empty bids array should result in empty bids");
    assertEquals(1, book.getAsks().size(), "Asks should still be populated");
  }

  @Test
  void snapshotHandlesMalformedLevels() throws Exception {
    CoinbaseStreamingMarketDataService.OrderBookState state =
        new CoinbaseStreamingMarketDataService.OrderBookState(
            CurrencyPair.BTC_USD, null);

    JsonNode snapshot =
        MAPPER.readTree(
            "{\n"
                + "  \"events\": [\n"
                + "    {\n"
                + "      \"type\": \"snapshot\",\n"
                + "      \"product_id\": \"BTC-USD\",\n"
                + "      \"sequence\": 100,\n"
                + "      \"bids\": [\n"
                + "        [\"100.00\", \"2.0\"],\n"
                + "        [\"invalid\", \"2.0\"],\n"
                + "        [\"99.00\"],\n"
                + "        [\"98.00\", \"invalid\"],\n"
                + "        [\"97.00\", \"1.5\"]\n"
                + "      ],\n"
                + "      \"asks\": [\n"
                + "        [\"110.00\", \"3.0\"]\n"
                + "      ]\n"
                + "    }\n"
                + "  ]\n"
                + "}");

    OrderBook book = state.process(CoinbaseStreamingAdapters.toStreamingMessage(snapshot)).blockingGet();
    
    // Only valid levels should be included
    assertEquals(2, book.getBids().size(), "Malformed levels should be skipped");
    assertEquals("100.00", book.getBids().get(0).getLimitPrice().toPlainString());
    assertEquals("97.00", book.getBids().get(1).getLimitPrice().toPlainString());
    assertEquals(1, book.getAsks().size());
  }

  @Test
  void snapshotHandlesMissingProductId() throws Exception {
    CoinbaseStreamingMarketDataService.OrderBookState state =
        new CoinbaseStreamingMarketDataService.OrderBookState(
            CurrencyPair.BTC_USD, null);

    JsonNode snapshot =
        MAPPER.readTree(
            "{\n"
                + "  \"events\": [\n"
                + "    {\n"
                + "      \"type\": \"snapshot\",\n"
                + "      \"sequence\": 100,\n"
                + "      \"bids\": [\n"
                + "        [\"100.00\", \"2.0\"]\n"
                + "      ],\n"
                + "      \"asks\": [\n"
                + "        [\"110.00\", \"3.0\"]\n"
                + "      ]\n"
                + "    }\n"
                + "  ]\n"
                + "}");

    // Missing product_id should cause snapshot to be skipped
    Maybe<OrderBook> result = state.process(CoinbaseStreamingAdapters.toStreamingMessage(snapshot));
    assertNotNull(result);
    // The book should still be created but empty or unchanged
    OrderBook book = result.blockingGet();
    // Since product_id is missing, the snapshot parsing will return early
    // and the book will be empty
    assertTrue(book.getBids().isEmpty() || book.getAsks().isEmpty(), 
        "Missing product_id should result in empty book");
  }

  @Test
  void snapshotClearsPreviousState() throws Exception {
    CoinbaseStreamingMarketDataService.OrderBookState state =
        new CoinbaseStreamingMarketDataService.OrderBookState(
            CurrencyPair.BTC_USD, null);

    // First snapshot
    JsonNode firstSnapshot =
        MAPPER.readTree(
            "{\n"
                + "  \"events\": [\n"
                + "    {\n"
                + "      \"type\": \"snapshot\",\n"
                + "      \"product_id\": \"BTC-USD\",\n"
                + "      \"sequence\": 100,\n"
                + "      \"bids\": [\n"
                + "        [\"100.00\", \"2.0\"]\n"
                + "      ],\n"
                + "      \"asks\": [\n"
                + "        [\"110.00\", \"3.0\"]\n"
                + "      ]\n"
                + "    }\n"
                + "  ]\n"
                + "}");

    OrderBook firstBook = state.process(CoinbaseStreamingAdapters.toStreamingMessage(firstSnapshot)).blockingGet();
    assertEquals(1, firstBook.getBids().size());
    assertEquals(1, firstBook.getAsks().size());

    // Second snapshot should clear previous state
    JsonNode secondSnapshot =
        MAPPER.readTree(
            "{\n"
                + "  \"events\": [\n"
                + "    {\n"
                + "      \"type\": \"snapshot\",\n"
                + "      \"product_id\": \"BTC-USD\",\n"
                + "      \"sequence\": 200,\n"
                + "      \"bids\": [\n"
                + "        [\"200.00\", \"5.0\"],\n"
                + "        [\"199.00\", \"4.0\"]\n"
                + "      ],\n"
                + "      \"asks\": [\n"
                + "        [\"210.00\", \"6.0\"]\n"
                + "      ]\n"
                + "    }\n"
                + "  ]\n"
                + "}");

    OrderBook secondBook = state.process(CoinbaseStreamingAdapters.toStreamingMessage(secondSnapshot)).blockingGet();
    
    // Previous state should be cleared
    assertEquals(2, secondBook.getBids().size());
    assertEquals(1, secondBook.getAsks().size());
    assertEquals("200.00", secondBook.getBids().get(0).getLimitPrice().toPlainString());
    assertEquals("5.0", secondBook.getBids().get(0).getOriginalAmount().toPlainString());
    // Old bid at 100.00 should not be present
    assertFalse(secondBook.getBids().stream()
        .anyMatch(bid -> bid.getLimitPrice().equals(new BigDecimal("100.00"))),
        "Previous bid level should be cleared");
  }

  @Test
  void snapshotTracksSequenceCorrectly() throws Exception {
    CoinbaseStreamingMarketDataService.OrderBookState state =
        new CoinbaseStreamingMarketDataService.OrderBookState(
            CurrencyPair.BTC_USD, null);

    JsonNode snapshot =
        MAPPER.readTree(
            "{\n"
                + "  \"events\": [\n"
                + "    {\n"
                + "      \"type\": \"snapshot\",\n"
                + "      \"product_id\": \"BTC-USD\",\n"
                + "      \"sequence\": 12345,\n"
                + "      \"bids\": [\n"
                + "        [\"100.00\", \"2.0\"]\n"
                + "      ],\n"
                + "      \"asks\": [\n"
                + "        [\"110.00\", \"3.0\"]\n"
                + "      ]\n"
                + "    }\n"
                + "  ]\n"
                + "}");

    state.process(CoinbaseStreamingAdapters.toStreamingMessage(snapshot)).blockingGet();
    
    Long lastSequence = getLastSequence(state);
    assertEquals(12345L, lastSequence, "Sequence should be tracked from snapshot");

    // Subsequent update should use this sequence
    JsonNode update =
        MAPPER.readTree(
            "{\n"
                + "  \"events\": [\n"
                + "    {\n"
                + "      \"type\": \"l2update\",\n"
                + "      \"product_id\": \"BTC-USD\",\n"
                + "      \"sequence\": 12346,\n"
                + "      \"updates\": [\n"
                + "        {\"side\": \"bid\", \"price_level\": \"100.00\", \"new_quantity\": \"1.5\"}\n"
                + "      ]\n"
                + "    }\n"
                + "  ]\n"
                + "}");

    state.process(CoinbaseStreamingAdapters.toStreamingMessage(update)).blockingGet();
    Long updatedSequence = getLastSequence(state);
    assertEquals(12346L, updatedSequence, "Sequence should be updated after l2update");
  }

  @Test
  void snapshotWithoutSequenceStillWorks() throws Exception {
    CoinbaseStreamingMarketDataService.OrderBookState state =
        new CoinbaseStreamingMarketDataService.OrderBookState(
            CurrencyPair.BTC_USD, null);

    JsonNode snapshot =
        MAPPER.readTree(
            "{\n"
                + "  \"events\": [\n"
                + "    {\n"
                + "      \"type\": \"snapshot\",\n"
                + "      \"product_id\": \"BTC-USD\",\n"
                + "      \"bids\": [\n"
                + "        [\"100.00\", \"2.0\"]\n"
                + "      ],\n"
                + "      \"asks\": [\n"
                + "        [\"110.00\", \"3.0\"]\n"
                + "      ]\n"
                + "    }\n"
                + "  ]\n"
                + "}");

    OrderBook book = state.process(CoinbaseStreamingAdapters.toStreamingMessage(snapshot)).blockingGet();
    assertNotNull(book);
    assertEquals(1, book.getBids().size());
    assertEquals(1, book.getAsks().size());
    assertTrue(getHasSnapshotFlag(state), "hasSnapshot should be true even without sequence");
  }

  private static boolean getHasSnapshotFlag(
      CoinbaseStreamingMarketDataService.OrderBookState state) throws Exception {
    Field field = CoinbaseStreamingMarketDataService.OrderBookState.class
        .getDeclaredField("hasSnapshot");
    field.setAccessible(true);
    return field.getBoolean(state);
  }

  private static Long getLastSequence(
      CoinbaseStreamingMarketDataService.OrderBookState state) throws Exception {
    Field field = CoinbaseStreamingMarketDataService.OrderBookState.class
        .getDeclaredField("lastSequence");
    field.setAccessible(true);
    java.util.concurrent.atomic.AtomicLong atomicLong = (java.util.concurrent.atomic.AtomicLong) field.get(state);
    long value = atomicLong.get();
    return value < 0 ? null : value;
  }

  private static OrderBook orderBook(List<LimitOrder> asks, List<LimitOrder> bids) {
    return new OrderBook(null, asks, bids);
  }

  private static LimitOrder limitOrder(Order.OrderType side, String price, String size) {
    return new LimitOrder(
        side,
        new BigDecimal(size),
        CurrencyPair.BTC_USD,
        null,
        null,
        new BigDecimal(price));
  }

  private static final class RecordingSnapshotProvider
      implements CoinbaseStreamingMarketDataService.OrderBookSnapshotProvider {
    private final List<OrderBook> snapshots;
    private final AtomicInteger calls = new AtomicInteger();

    private RecordingSnapshotProvider(List<OrderBook> snapshots) {
      this.snapshots = snapshots;
    }

    @Override
    public OrderBook fetchSnapshot(CurrencyPair currencyPair) {
      int index = Math.min(calls.getAndIncrement(), snapshots.size() - 1);
      return snapshots.get(index);
    }

    int callCount() {
      return calls.get();
    }
  }

  @Test
  void priceNormalizationInSnapshotAllowsUpdateWithDifferentScale() throws Exception {
    // Test that a price in snapshot with one scale can be updated with the same price
    // but different scale (e.g., "100.0" vs "100.00")
    CoinbaseStreamingMarketDataService.OrderBookState state =
        new CoinbaseStreamingMarketDataService.OrderBookState(CurrencyPair.BTC_USD, null);

    // Snapshot with price "100.0" (scale 1)
    JsonNode snapshot =
        MAPPER.readTree(
            "{\n"
                + "  \"events\": [\n"
                + "    {\n"
                + "      \"type\": \"snapshot\",\n"
                + "      \"product_id\": \"BTC-USD\",\n"
                + "      \"sequence\": 100,\n"
                + "      \"bids\": [\n"
                + "        [\"100.0\", \"2.0\"]\n"
                + "      ],\n"
                + "      \"asks\": [\n"
                + "        [\"110.0\", \"3.0\"]\n"
                + "      ]\n"
                + "    }\n"
                + "  ]\n"
                + "}");

    OrderBook book = state.process(CoinbaseStreamingAdapters.toStreamingMessage(snapshot)).blockingGet();
    assertEquals(1, book.getBids().size());
    assertEquals(new BigDecimal("2.0"), book.getBids().get(0).getOriginalAmount());

    // Update with price "100.00" (scale 2) - should match and update the same level
    JsonNode update =
        MAPPER.readTree(
            "{\n"
                + "  \"events\": [\n"
                + "    {\n"
                + "      \"type\": \"l2update\",\n"
                + "      \"product_id\": \"BTC-USD\",\n"
                + "      \"sequence\": 101,\n"
                + "      \"updates\": [\n"
                + "        {\"side\": \"bid\", \"price_level\": \"100.00\", \"new_quantity\": \"5.5\"}\n"
                + "      ]\n"
                + "    }\n"
                + "  ]\n"
                + "}");

    OrderBook updated = state.process(CoinbaseStreamingAdapters.toStreamingMessage(update)).blockingGet();
    assertEquals(1, updated.getBids().size());
    assertEquals(new BigDecimal("5.5"), updated.getBids().get(0).getOriginalAmount(),
        "Price 100.00 should update the same level as 100.0");
    // The LimitOrder stores the original price from the update, but the map key is normalized
    // So the price should be "100.00" (from the update) but it matches the normalized key from snapshot
    assertEquals(0, updated.getBids().get(0).getLimitPrice().compareTo(new BigDecimal("100.00")),
        "LimitOrder should have the price from the update");
  }

  @Test
  void priceNormalizationInSnapshotAllowsRemovalWithDifferentScale() throws Exception {
    // Test that a price in snapshot can be removed with a different scale
    CoinbaseStreamingMarketDataService.OrderBookState state =
        new CoinbaseStreamingMarketDataService.OrderBookState(CurrencyPair.BTC_USD, null);

    // Snapshot with price "100.00" (scale 2)
    JsonNode snapshot =
        MAPPER.readTree(
            "{\n"
                + "  \"events\": [\n"
                + "    {\n"
                + "      \"type\": \"snapshot\",\n"
                + "      \"product_id\": \"BTC-USD\",\n"
                + "      \"sequence\": 100,\n"
                + "      \"bids\": [\n"
                + "        [\"100.00\", \"2.0\"],\n"
                + "        [\"99.50\", \"1.5\"]\n"
                + "      ],\n"
                + "      \"asks\": [\n"
                + "        [\"110.00\", \"3.0\"]\n"
                + "      ]\n"
                + "    }\n"
                + "  ]\n"
                + "}");

    OrderBook book = state.process(CoinbaseStreamingAdapters.toStreamingMessage(snapshot)).blockingGet();
    assertEquals(2, book.getBids().size());

    // Remove with price "100.0" (scale 1) - should match and remove
    JsonNode update =
        MAPPER.readTree(
            "{\n"
                + "  \"events\": [\n"
                + "    {\n"
                + "      \"type\": \"l2update\",\n"
                + "      \"product_id\": \"BTC-USD\",\n"
                + "      \"sequence\": 101,\n"
                + "      \"updates\": [\n"
                + "        {\"side\": \"bid\", \"price_level\": \"100.0\", \"new_quantity\": \"0\"}\n"
                + "      ]\n"
                + "    }\n"
                + "  ]\n"
                + "}");

    OrderBook updated = state.process(CoinbaseStreamingAdapters.toStreamingMessage(update)).blockingGet();
    assertEquals(1, updated.getBids().size(), "Price 100.0 should remove the same level as 100.00");
    assertEquals(new BigDecimal("99.50"), updated.getBids().get(0).getLimitPrice());
  }

  @Test
  void priceNormalizationInUpdatesAllowsMatchingExistingLevels() throws Exception {
    // Test that updates with different scales can match existing levels
    CoinbaseStreamingMarketDataService.OrderBookState state =
        new CoinbaseStreamingMarketDataService.OrderBookState(CurrencyPair.BTC_USD, null);

    // Snapshot
    JsonNode snapshot =
        MAPPER.readTree(
            "{\n"
                + "  \"events\": [\n"
                + "    {\n"
                + "      \"type\": \"snapshot\",\n"
                + "      \"product_id\": \"BTC-USD\",\n"
                + "      \"sequence\": 100,\n"
                + "      \"bids\": [\n"
                + "        [\"100.000\", \"2.0\"]\n"
                + "      ],\n"
                + "      \"asks\": [\n"
                + "        [\"110.000\", \"3.0\"]\n"
                + "      ]\n"
                + "    }\n"
                + "  ]\n"
                + "}");

    state.process(CoinbaseStreamingAdapters.toStreamingMessage(snapshot)).blockingGet();

    // Multiple updates with different scales for same price
    JsonNode update1 =
        MAPPER.readTree(
            "{\n"
                + "  \"events\": [\n"
                + "    {\n"
                + "      \"type\": \"l2update\",\n"
                + "      \"product_id\": \"BTC-USD\",\n"
                + "      \"sequence\": 101,\n"
                + "      \"updates\": [\n"
                + "        {\"side\": \"bid\", \"price_level\": \"100.0\", \"new_quantity\": \"5.0\"}\n"
                + "      ]\n"
                + "    }\n"
                + "  ]\n"
                + "}");

    OrderBook updated1 = state.process(CoinbaseStreamingAdapters.toStreamingMessage(update1)).blockingGet();
    assertEquals(1, updated1.getBids().size());
    assertEquals(new BigDecimal("5.0"), updated1.getBids().get(0).getOriginalAmount());

    // Update again with yet another scale
    JsonNode update2 =
        MAPPER.readTree(
            "{\n"
                + "  \"events\": [\n"
                + "    {\n"
                + "      \"type\": \"l2update\",\n"
                + "      \"product_id\": \"BTC-USD\",\n"
                + "      \"sequence\": 102,\n"
                + "      \"updates\": [\n"
                + "        {\"side\": \"bid\", \"price_level\": \"100.00\", \"new_quantity\": \"7.5\"}\n"
                + "      ]\n"
                + "    }\n"
                + "  ]\n"
                + "}");

    OrderBook updated2 = state.process(CoinbaseStreamingAdapters.toStreamingMessage(update2)).blockingGet();
    assertEquals(1, updated2.getBids().size());
    assertEquals(new BigDecimal("7.5"), updated2.getBids().get(0).getOriginalAmount(),
        "Price 100.00 should update the same level as 100.000 and 100.0");
  }

  @Test
  void priceNormalizationInSnapshotRecovery() throws Exception {
    // Test that snapshot recovery normalizes prices correctly
    RecordingSnapshotProvider provider =
        new RecordingSnapshotProvider(
            Collections.singletonList(
                orderBook(
                    Collections.singletonList(limitOrder(Order.OrderType.ASK, "110.00", "3")),
                    Collections.singletonList(limitOrder(Order.OrderType.BID, "100.0", "2")))));

    CoinbaseStreamingMarketDataService.OrderBookState state =
        new CoinbaseStreamingMarketDataService.OrderBookState(CurrencyPair.BTC_USD, provider);

    // Trigger recovery by sending an update without snapshot
    JsonNode update =
        MAPPER.readTree(
            "{\n"
                + "  \"events\": [\n"
                + "    {\n"
                + "      \"type\": \"l2update\",\n"
                + "      \"product_id\": \"BTC-USD\",\n"
                + "      \"sequence\": 100,\n"
                + "      \"updates\": [\n"
                + "        {\"side\": \"bid\", \"price_level\": \"100.00\", \"new_quantity\": \"5.0\"}\n"
                + "      ]\n"
                + "    }\n"
                + "  ]\n"
                + "}");

    OrderBook recovered = state.process(CoinbaseStreamingAdapters.toStreamingMessage(update)).blockingGet();
    
    // The snapshot had "100.0" and the update has "100.00" - they should match after normalization
    assertEquals(1, recovered.getBids().size());
    assertEquals(new BigDecimal("5.0"), recovered.getBids().get(0).getOriginalAmount(),
        "Price 100.00 should update the same level as 100.0 from snapshot recovery");
  }

  @Test
  void priceNormalizationHandlesMultiplePricesWithDifferentScales() throws Exception {
    // Test snapshot with multiple prices that have different scales
    CoinbaseStreamingMarketDataService.OrderBookState state =
        new CoinbaseStreamingMarketDataService.OrderBookState(CurrencyPair.BTC_USD, null);

    // Snapshot with prices having different scales
    JsonNode snapshot =
        MAPPER.readTree(
            "{\n"
                + "  \"events\": [\n"
                + "    {\n"
                + "      \"type\": \"snapshot\",\n"
                + "      \"product_id\": \"BTC-USD\",\n"
                + "      \"sequence\": 100,\n"
                + "      \"bids\": [\n"
                + "        [\"100.0\", \"2.0\"],\n"
                + "        [\"99.50\", \"1.5\"],\n"
                + "        [\"99.000\", \"1.0\"]\n"
                + "      ],\n"
                + "      \"asks\": [\n"
                + "        [\"110.00\", \"3.0\"],\n"
                + "        [\"110.5\", \"2.5\"],\n"
                + "        [\"111.000\", \"2.0\"]\n"
                + "      ]\n"
                + "    }\n"
                + "  ]\n"
                + "}");

    OrderBook book = state.process(CoinbaseStreamingAdapters.toStreamingMessage(snapshot)).blockingGet();
    assertEquals(3, book.getBids().size());
    assertEquals(3, book.getAsks().size());

    // Update each level with different scales
    JsonNode update =
        MAPPER.readTree(
            "{\n"
                + "  \"events\": [\n"
                + "    {\n"
                + "      \"type\": \"l2update\",\n"
                + "      \"product_id\": \"BTC-USD\",\n"
                + "      \"sequence\": 101,\n"
                + "      \"updates\": [\n"
                + "        {\"side\": \"bid\", \"price_level\": \"100.00\", \"new_quantity\": \"10.0\"},\n"
                + "        {\"side\": \"bid\", \"price_level\": \"99.5\", \"new_quantity\": \"20.0\"},\n"
                + "        {\"side\": \"bid\", \"price_level\": \"99.0000\", \"new_quantity\": \"30.0\"},\n"
                + "        {\"side\": \"ask\", \"price_level\": \"110.0\", \"new_quantity\": \"15.0\"},\n"
                + "        {\"side\": \"ask\", \"price_level\": \"110.50\", \"new_quantity\": \"25.0\"},\n"
                + "        {\"side\": \"ask\", \"price_level\": \"111.00\", \"new_quantity\": \"35.0\"}\n"
                + "      ]\n"
                + "    }\n"
                + "  ]\n"
                + "}");

    OrderBook updated = state.process(CoinbaseStreamingAdapters.toStreamingMessage(update)).blockingGet();
    assertEquals(3, updated.getBids().size());
    assertEquals(3, updated.getAsks().size());
    
    // Verify all updates matched correctly
    assertEquals(new BigDecimal("10.0"), updated.getBids().get(0).getOriginalAmount());
    assertEquals(new BigDecimal("20.0"), updated.getBids().get(1).getOriginalAmount());
    assertEquals(new BigDecimal("30.0"), updated.getBids().get(2).getOriginalAmount());
    assertEquals(new BigDecimal("15.0"), updated.getAsks().get(0).getOriginalAmount());
    assertEquals(new BigDecimal("25.0"), updated.getAsks().get(1).getOriginalAmount());
    assertEquals(new BigDecimal("35.0"), updated.getAsks().get(2).getOriginalAmount());
  }

  @Test
  void priceNormalizationInSnapshotWithIntegerPrices() throws Exception {
    // Test that integer prices (like "100" vs "100.0") are normalized correctly
    CoinbaseStreamingMarketDataService.OrderBookState state =
        new CoinbaseStreamingMarketDataService.OrderBookState(CurrencyPair.BTC_USD, null);

    // Snapshot with integer price "100"
    JsonNode snapshot =
        MAPPER.readTree(
            "{\n"
                + "  \"events\": [\n"
                + "    {\n"
                + "      \"type\": \"snapshot\",\n"
                + "      \"product_id\": \"BTC-USD\",\n"
                + "      \"sequence\": 100,\n"
                + "      \"bids\": [\n"
                + "        [\"100\", \"2.0\"]\n"
                + "      ],\n"
                + "      \"asks\": [\n"
                + "        [\"110\", \"3.0\"]\n"
                + "      ]\n"
                + "    }\n"
                + "  ]\n"
                + "}");

    OrderBook book = state.process(CoinbaseStreamingAdapters.toStreamingMessage(snapshot)).blockingGet();
    assertEquals(1, book.getBids().size());

    // Update with decimal price "100.0" - should match
    JsonNode update =
        MAPPER.readTree(
            "{\n"
                + "  \"events\": [\n"
                + "    {\n"
                + "      \"type\": \"l2update\",\n"
                + "      \"product_id\": \"BTC-USD\",\n"
                + "      \"sequence\": 101,\n"
                + "      \"updates\": [\n"
                + "        {\"side\": \"bid\", \"price_level\": \"100.0\", \"new_quantity\": \"5.0\"}\n"
                + "      ]\n"
                + "    }\n"
                + "  ]\n"
                + "}");

    OrderBook updated = state.process(CoinbaseStreamingAdapters.toStreamingMessage(update)).blockingGet();
    assertEquals(1, updated.getBids().size());
    assertEquals(new BigDecimal("5.0"), updated.getBids().get(0).getOriginalAmount(),
        "Price 100.0 should match integer price 100");
  }

  @Test
  void priceNormalizationPreventsDuplicateEntries() throws Exception {
    // Test that the same price with different scales doesn't create duplicate entries
    CoinbaseStreamingMarketDataService.OrderBookState state =
        new CoinbaseStreamingMarketDataService.OrderBookState(CurrencyPair.BTC_USD, null);

    // Snapshot with price "100.0"
    JsonNode snapshot =
        MAPPER.readTree(
            "{\n"
                + "  \"events\": [\n"
                + "    {\n"
                + "      \"type\": \"snapshot\",\n"
                + "      \"product_id\": \"BTC-USD\",\n"
                + "      \"sequence\": 100,\n"
                + "      \"bids\": [\n"
                + "        [\"100.0\", \"2.0\"]\n"
                + "      ],\n"
                + "      \"asks\": []\n"
                + "    }\n"
                + "  ]\n"
                + "}");

    OrderBook book = state.process(CoinbaseStreamingAdapters.toStreamingMessage(snapshot)).blockingGet();
    assertEquals(1, book.getBids().size());

    // Update with "100.00" - should not create a duplicate
    JsonNode update =
        MAPPER.readTree(
            "{\n"
                + "  \"events\": [\n"
                + "    {\n"
                + "      \"type\": \"l2update\",\n"
                + "      \"product_id\": \"BTC-USD\",\n"
                + "      \"sequence\": 101,\n"
                + "      \"updates\": [\n"
                + "        {\"side\": \"bid\", \"price_level\": \"100.00\", \"new_quantity\": \"5.0\"}\n"
                + "      ]\n"
                + "    }\n"
                + "  ]\n"
                + "}");

    OrderBook updated = state.process(CoinbaseStreamingAdapters.toStreamingMessage(update)).blockingGet();
    assertEquals(1, updated.getBids().size(),
        "Should not create duplicate entry for same price with different scale");
    assertEquals(new BigDecimal("5.0"), updated.getBids().get(0).getOriginalAmount());
  }

}
