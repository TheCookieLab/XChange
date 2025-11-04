package info.bitrich.xchangestream.coinbase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.reactivex.rxjava3.core.Maybe;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.trade.LimitOrder;

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

    JsonNode snapshot =
        MAPPER.readTree(
            "{\n"
                + "  \"events\": [\n"
                + "    {\n"
                + "      \"type\": \"snapshot\",\n"
                + "      \"product_id\": \"BTC-USD\",\n"
                + "      \"sequence\": 42,\n"
                + "      \"updates\": [\n"
                + "        {\"side\": \"bid\", \"price_level\": \"100\", \"new_quantity\": \"2\"},\n"
                + "        {\"side\": \"ask\", \"price_level\": \"110\", \"new_quantity\": \"3\"}\n"
                + "      ]\n"
                + "    }\n"
                + "  ]\n"
                + "}");

    Maybe<OrderBook> maybeSnapshot = state.process(snapshot);
    OrderBook book = maybeSnapshot.blockingGet();
    assertEquals(1, book.getBids().size());
    assertEquals(1, book.getAsks().size());
    assertEquals("100", book.getBids().get(0).getLimitPrice().toPlainString());
    assertEquals("2", book.getBids().get(0).getOriginalAmount().toPlainString());
    assertEquals("110", book.getAsks().get(0).getLimitPrice().toPlainString());
    assertEquals("3", book.getAsks().get(0).getOriginalAmount().toPlainString());

    JsonNode update =
        MAPPER.readTree(
            "{\n"
                + "  \"events\": [\n"
                + "    {\n"
                + "      \"type\": \"l2update\",\n"
                + "      \"product_id\": \"BTC-USD\",\n"
                + "      \"sequence\": 43,\n"
                + "      \"updates\": [\n"
                + "        {\"side\": \"bid\", \"price_level\": \"100\", \"new_quantity\": \"1.5\"},\n"
                + "        {\"side\": \"ask\", \"price_level\": \"110\", \"new_quantity\": \"0\"}\n"
                + "      ]\n"
                + "    }\n"
                + "  ]\n"
                + "}");

    Maybe<OrderBook> maybeUpdate = state.process(update);
    OrderBook updated = maybeUpdate.blockingGet();
    assertEquals(1, updated.getBids().size());
    assertEquals("100", updated.getBids().get(0).getLimitPrice().toPlainString());
    assertEquals("1.5", updated.getBids().get(0).getOriginalAmount().toPlainString());
    assertTrue(updated.getAsks().isEmpty());
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

    OrderBook first = state.process(firstUpdate).blockingGet();
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

    OrderBook recovered = state.process(gapUpdate).blockingGet();
    assertEquals(2, provider.callCount());
    assertEquals(2, recovered.getBids().size());
    assertEquals("1.8", recovered.getAsks().get(0).getOriginalAmount().toPlainString());
    assertEquals("101", recovered.getBids().get(0).getLimitPrice().toPlainString());
    assertEquals("1.5", recovered.getBids().get(0).getOriginalAmount().toPlainString());
    assertEquals("100", recovered.getBids().get(1).getLimitPrice().toPlainString());
    assertEquals("1", recovered.getBids().get(1).getOriginalAmount().toPlainString());
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
}
