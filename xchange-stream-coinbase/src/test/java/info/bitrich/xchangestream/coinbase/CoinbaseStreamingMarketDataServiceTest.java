package info.bitrich.xchangestream.coinbase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.reactivex.rxjava3.core.Maybe;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.OrderBook;

class CoinbaseStreamingMarketDataServiceTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  void orderBookStateAppliesSnapshotAndUpdates() throws Exception {
    Class<?> stateClass = Class.forName("info.bitrich.xchangestream.coinbase.CoinbaseStreamingMarketDataService$OrderBookState");
    Constructor<?> ctor = stateClass.getDeclaredConstructor(CurrencyPair.class);
    ctor.setAccessible(true);
    Object state = ctor.newInstance(CurrencyPair.BTC_USD);

    Method process = stateClass.getDeclaredMethod("process", JsonNode.class);
    process.setAccessible(true);

    JsonNode snapshot =
        MAPPER.readTree(
            "{\n"
                + "  \"events\": [\n"
                + "    {\n"
                + "      \"type\": \"snapshot\",\n"
                + "      \"product_id\": \"BTC-USD\",\n"
                + "      \"updates\": [\n"
                + "        {\"side\": \"bid\", \"price_level\": \"100\", \"new_quantity\": \"2\"},\n"
                + "        {\"side\": \"ask\", \"price_level\": \"110\", \"new_quantity\": \"3\"}\n"
                + "      ]\n"
                + "    }\n"
                + "  ]\n"
                + "}");

    Maybe<?> maybeSnapshot = (Maybe<?>) process.invoke(state, snapshot);
    OrderBook book = (OrderBook) maybeSnapshot.blockingGet();
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
                + "      \"updates\": [\n"
                + "        {\"side\": \"bid\", \"price_level\": \"100\", \"new_quantity\": \"1.5\"},\n"
                + "        {\"side\": \"ask\", \"price_level\": \"110\", \"new_quantity\": \"0\"}\n"
                + "      ]\n"
                + "    }\n"
                + "  ]\n"
                + "}");

    Maybe<?> maybeUpdate = (Maybe<?>) process.invoke(state, update);
    OrderBook updated = (OrderBook) maybeUpdate.blockingGet();
    assertEquals(1, updated.getBids().size());
    assertEquals("100", updated.getBids().get(0).getLimitPrice().toPlainString());
    assertEquals("1.5", updated.getBids().get(0).getOriginalAmount().toPlainString());
    assertTrue(updated.getAsks().isEmpty());
  }
}

