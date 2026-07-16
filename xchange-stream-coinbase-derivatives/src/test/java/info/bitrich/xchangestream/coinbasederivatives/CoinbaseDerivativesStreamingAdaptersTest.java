package info.bitrich.xchangestream.coinbasederivatives;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.coinbasederivatives.CoinbaseDerivativesAdapters;
import org.knowm.xchange.coinbasederivatives.dto.marketdata.CoinbaseDerivativesInstrument;
import org.knowm.xchange.derivative.FuturesContract;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.dto.trade.StopOrder;

class CoinbaseDerivativesStreamingAdaptersTest {

  private static final ObjectMapper MAPPER =
      new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

  @Test
  void mapsNativePerpetualAndPreservesScientificNotationNumbers() throws Exception {
    FuturesContract instrument =
        CoinbaseDerivativesStreamingAdapters.toInstrument("BTC_USDC-PERPETUAL");
    CoinbaseDerivativesAdapters.registerInstrument(providerInstrument());
    assertEquals("BTC/USDC/PERPETUAL", instrument.toString());
    assertEquals(
        "BTC_USDC-PERPETUAL", CoinbaseDerivativesStreamingAdapters.toNativeName(instrument));

    JsonNode ticker =
        MAPPER.readTree(
            "{\"instrument_name\":\"BTC_USDC-PERPETUAL\",\"last_price\":1.234567890123456789E+8,\"best_bid_price\":1E-8,\"best_ask_price\":2E-8,\"timestamp\":1,\"stats\":{}} ");
    assertEquals(
        new BigDecimal("123456789.0123456789"),
        CoinbaseDerivativesStreamingAdapters.toTicker(ticker).getLast());
    assertEquals(
        new BigDecimal("0.00000001"),
        CoinbaseDerivativesStreamingAdapters.toTicker(ticker).getBid());
  }

  @Test
  void rejectsTextualWireNumerics() throws Exception {
    JsonNode ticker =
        MAPPER.readTree(
            "{\"instrument_name\":\"BTC_USDC-PERPETUAL\",\"last_price\":\"1.2\",\"timestamp\":1,\"stats\":{}} ");
    assertThrows(
        CoinbaseDerivativesStreamException.class,
        () -> CoinbaseDerivativesStreamingAdapters.toTicker(ticker));
  }

  @Test
  void mapsMarketLimitAndStopNotificationsWithoutLosingLabels() throws Exception {
    Order market = CoinbaseDerivativesStreamingAdapters.toOrder(order("market", false));
    Order limit = CoinbaseDerivativesStreamingAdapters.toOrder(order("limit", false));
    Order stop = CoinbaseDerivativesStreamingAdapters.toOrder(order("stop_limit", true));

    assertInstanceOf(MarketOrder.class, market);
    assertInstanceOf(LimitOrder.class, limit);
    assertInstanceOf(StopOrder.class, stop);
    assertEquals("duplicate-label-is-allowed", market.getUserReference());
    assertEquals("duplicate-label-is-allowed", limit.getUserReference());
    assertEquals("duplicate-label-is-allowed", stop.getUserReference());
  }

  private JsonNode order(String type, boolean trigger) throws Exception {
    String triggerField = trigger ? ",\"trigger_price\":99" : "";
    return MAPPER.readTree(
        "{\"amount\":1.25,\"direction\":\"buy\",\"instrument_name\":\"BTC_USDC-PERPETUAL\","
            + "\"order_id\":\"order-1\",\"last_update_timestamp\":1,\"price\":100,\"average_price\":100,"
            + "\"filled_amount\":0.25,\"order_state\":\"open\",\"order_type\":\""
            + type
            + "\",\"label\":\"duplicate-label-is-allowed\""
            + triggerField
            + "}");
  }

  private CoinbaseDerivativesInstrument providerInstrument() {
    return new CoinbaseDerivativesInstrument(
        "BTC_USDC-PERPETUAL", "future", "BTC", "USDC", "USDC", true, null, null, null, null, null);
  }
}
