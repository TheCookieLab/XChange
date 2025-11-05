package info.bitrich.xchangestream.coinbase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import info.bitrich.xchangestream.coinbase.adapters.CoinbaseStreamingAdapters;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.marketdata.CandleStick;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trade;
import org.knowm.xchange.dto.trade.LimitOrder;

class CoinbaseStreamingAdaptersTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  void adaptTickerParsesPrices() throws IOException {
    JsonNode node =
        MAPPER.readTree(
            "{\n"
                + "  \"channel\": \"ticker\",\n"
                + "  \"events\": [\n"
                + "    {\n"
                + "      \"type\": \"ticker\",\n"
                + "      \"tickers\": [\n"
                + "        {\n"
                + "          \"product_id\": \"BTC-USD\",\n"
                + "          \"price\": \"50000\",\n"
                + "          \"volume_24_h\": \"123.45\",\n"
                + "          \"best_bid\": \"49900\",\n"
                + "          \"best_ask\": \"50100\",\n"
                + "          \"time\": \"2024-01-01T00:00:00Z\"\n"
                + "        }\n"
                + "      ]\n"
                + "    }\n"
                + "  ]\n"
                + "}");

    List<Ticker> tickers = CoinbaseStreamingAdapters.adaptTickers(node);

    Assertions.assertEquals(1, tickers.size());
    Ticker ticker = tickers.get(0);
    Assertions.assertEquals(CurrencyPair.BTC_USD, ticker.getInstrument());
    Assertions.assertEquals(new BigDecimal("50000"), ticker.getLast());
    Assertions.assertEquals(new BigDecimal("123.45"), ticker.getVolume());
    Assertions.assertEquals(new BigDecimal("49900"), ticker.getBid());
    Assertions.assertEquals(new BigDecimal("50100"), ticker.getAsk());
  }

  @Test
  void adaptTradesParsesSide() throws IOException {
    JsonNode node =
        MAPPER.readTree(
            "{\n"
                + "  \"channel\": \"market_trades\",\n"
                + "  \"events\": [\n"
                + "    {\n"
                + "      \"type\": \"snapshot\",\n"
                + "      \"trades\": [\n"
                + "        {\n"
                + "          \"product_id\": \"BTC-USD\",\n"
                + "          \"trade_id\": \"1001\",\n"
                + "          \"price\": \"100\",\n"
                + "          \"size\": \"0.5\",\n"
                + "          \"side\": \"BUY\",\n"
                + "          \"time\": \"2024-01-01T00:00:05Z\"\n"
                + "        }\n"
                + "      ]\n"
                + "    }\n"
                + "  ]\n"
                + "}");

    List<Trade> trades = CoinbaseStreamingAdapters.adaptTrades(node);

    Assertions.assertEquals(1, trades.size());
    Trade trade = trades.get(0);
    Assertions.assertEquals(CurrencyPair.BTC_USD, trade.getInstrument());
    Assertions.assertEquals(new BigDecimal("100"), trade.getPrice());
    Assertions.assertEquals(new BigDecimal("0.5"), trade.getOriginalAmount());
    Assertions.assertEquals(Order.OrderType.BID, trade.getType());
  }

  @Test
  void adaptCandlesProducesCandleStick() throws IOException {
    JsonNode node =
        MAPPER.readTree(
            "{\n"
                + "  \"channel\": \"candles\",\n"
                + "  \"events\": [\n"
                + "    {\n"
                + "      \"type\": \"snapshot\",\n"
                + "      \"candles\": [\n"
                + "        {\n"
                + "          \"product_id\": \"BTC-USD\",\n"
                + "          \"start\": \"2024-01-01T00:00:00Z\",\n"
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

    List<CandleStick> candles =
        CoinbaseStreamingAdapters.adaptCandles(node, CurrencyPair.BTC_USD);

    Assertions.assertEquals(1, candles.size());
    CandleStick candle = candles.get(0);
    Assertions.assertEquals(new BigDecimal("100"), candle.getOpen());
    Assertions.assertEquals(new BigDecimal("110"), candle.getClose());
    Assertions.assertEquals(new BigDecimal("120"), candle.getHigh());
    Assertions.assertEquals(new BigDecimal("90"), candle.getLow());
    Assertions.assertEquals(new BigDecimal("5"), candle.getVolume());
  }

  @Test
  void adaptCandlesParsesUnixTimestamp() throws IOException {
    // Test that adaptCandles correctly parses UNIX timestamp (epoch seconds) from the "start" field
    // This is the correct format according to Coinbase API specification
    long expectedEpochSeconds = 1704067200L; // 2024-01-01T00:00:00Z
    JsonNode node =
        MAPPER.readTree(
            "{\n"
                + "  \"channel\": \"candles\",\n"
                + "  \"events\": [\n"
                + "    {\n"
                + "      \"type\": \"snapshot\",\n"
                + "      \"candles\": [\n"
                + "        {\n"
                + "          \"product_id\": \"BTC-USD\",\n"
                + "          \"start\": \""
                + expectedEpochSeconds
                + "\",\n"
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

    List<CandleStick> candles =
        CoinbaseStreamingAdapters.adaptCandles(node, CurrencyPair.BTC_USD);

    Assertions.assertEquals(1, candles.size());
    CandleStick candle = candles.get(0);
    Assertions.assertNotNull(candle.getTimestamp(), "Candle timestamp should not be null");
    Assertions.assertEquals(
        Date.from(Instant.ofEpochSecond(expectedEpochSeconds)),
        candle.getTimestamp(),
        "Candle timestamp should match the parsed UNIX timestamp");
    Assertions.assertEquals(new BigDecimal("100"), candle.getOpen());
    Assertions.assertEquals(new BigDecimal("110"), candle.getClose());
    Assertions.assertEquals(new BigDecimal("120"), candle.getHigh());
    Assertions.assertEquals(new BigDecimal("90"), candle.getLow());
    Assertions.assertEquals(new BigDecimal("5"), candle.getVolume());
  }

  @Test
  void parseUnixTimestampParsesEpochSeconds() throws IOException {
    // Test the parseUnixTimestamp helper method directly
    long expectedEpochSeconds = 1704067200L; // 2024-01-01T00:00:00Z
    JsonNode node = MAPPER.readTree("{\"start\": \"" + expectedEpochSeconds + "\"}");

    Optional<Instant> result =
        CoinbaseStreamingAdapters.parseUnixTimestamp(node.path("start"));

    Assertions.assertTrue(result.isPresent(), "Should successfully parse valid UNIX timestamp");
    Assertions.assertEquals(
        Instant.ofEpochSecond(expectedEpochSeconds),
        result.get(),
        "Parsed Instant should match expected epoch seconds");
  }

  @Test
  void parseUnixTimestampHandlesNullAndMissingNodes() throws IOException {
    // Test that parseUnixTimestamp handles null, missing, and invalid nodes gracefully
    JsonNode nullNode = MAPPER.readTree("{\"start\": null}");
    JsonNode missingNode = MAPPER.readTree("{}");
    JsonNode invalidNode = MAPPER.readTree("{\"start\": \"not-a-number\"}");

    Assertions.assertFalse(
        CoinbaseStreamingAdapters.parseUnixTimestamp(nullNode.path("start")).isPresent(),
        "Should return empty for null node");
    Assertions.assertFalse(
        CoinbaseStreamingAdapters.parseUnixTimestamp(missingNode.path("start")).isPresent(),
        "Should return empty for missing node");
    Assertions.assertFalse(
        CoinbaseStreamingAdapters.parseUnixTimestamp(invalidNode.path("start")).isPresent(),
        "Should return empty for invalid format");
  }

  @Test
  void adaptCandlesHandlesMissingStartTimestamp() throws IOException {
    // Test that adaptCandles handles missing start timestamp gracefully
    JsonNode node =
        MAPPER.readTree(
            "{\n"
                + "  \"channel\": \"candles\",\n"
                + "  \"events\": [\n"
                + "    {\n"
                + "      \"type\": \"snapshot\",\n"
                + "      \"candles\": [\n"
                + "        {\n"
                + "          \"product_id\": \"BTC-USD\",\n"
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

    List<CandleStick> candles =
        CoinbaseStreamingAdapters.adaptCandles(node, CurrencyPair.BTC_USD);

    Assertions.assertEquals(1, candles.size());
    CandleStick candle = candles.get(0);
    Assertions.assertNull(candle.getTimestamp(), "Candle timestamp should be null when start is missing");
  }

  @Test
  void adaptLevel2UpdatesProducesBidsAndAsks() throws IOException {
    JsonNode node =
        MAPPER.readTree(
            "{\n"
                + "  \"type\": \"snapshot\",\n"
                + "  \"product_id\": \"BTC-USD\",\n"
                + "  \"updates\": [\n"
                + "    {\"side\": \"bid\", \"price_level\": \"100\", \"new_quantity\": \"1\"},\n"
                + "    {\"side\": \"ask\", \"price_level\": \"105\", \"new_quantity\": \"2\"}\n"
                + "  ]\n"
                + "}");

    List<LimitOrder> bids =
        CoinbaseStreamingAdapters.adaptLevel2Updates(node, Order.OrderType.BID);
    List<LimitOrder> asks =
        CoinbaseStreamingAdapters.adaptLevel2Updates(node, Order.OrderType.ASK);

    Assertions.assertEquals(1, bids.size());
    Assertions.assertEquals(new BigDecimal("100"), bids.get(0).getLimitPrice());
    Assertions.assertEquals(new BigDecimal("1"), bids.get(0).getOriginalAmount());
    Assertions.assertEquals(Order.OrderType.BID, bids.get(0).getType());

    Assertions.assertEquals(1, asks.size());
    Assertions.assertEquals(new BigDecimal("105"), asks.get(0).getLimitPrice());
    Assertions.assertEquals(new BigDecimal("2"), asks.get(0).getOriginalAmount());
    Assertions.assertEquals(Order.OrderType.ASK, asks.get(0).getType());
  }
}
