package org.knowm.xchange.yobit.dto.marketdata;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;
import org.junit.Test;

public class YoBitDeserializersTest {

  private static final String RESOURCE_ROOT = "/org/knowm/xchange/yobit/service/marketdata/";

  private static final String TICKERS_JSON =
      "{\"ltc_btc\":{\"high\":0.0061,\"low\":0.0055,\"avg\":0.0058,\"vol\":12.5,"
          + "\"vol_cur\":2150.75,\"last\":0.00584,\"buy\":0.00583,\"sell\":0.00585,"
          + "\"updated\":1477580702},"
          + "\"eth_btc\":{\"high\":0.02,\"low\":0.01,\"avg\":0.015,\"vol\":1,"
          + "\"vol_cur\":66.6,\"last\":0.012,\"buy\":0.011,\"sell\":0.013,"
          + "\"updated\":1477580800}}";

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  public void orderBooksDeserialization() throws Exception {
    YoBitOrderBooksReturn orderBooksReturn =
        mapper.readValue(resource("example-orderbook-data.json"), YoBitOrderBooksReturn.class);

    assertThat(orderBooksReturn.orderBooks).containsOnlyKeys("ltc_btc");
    YoBitOrderBook orderBook = orderBooksReturn.orderBooks.get("ltc_btc");

    assertThat(orderBook.getAsks()).hasSize(150);
    assertThat(orderBook.getBids()).hasSize(33);
    assertLevel(orderBook.getAsks().get(0), "0.00584001", "1");
    assertLevel(orderBook.getAsks().get(149), "0.00975906", "7.55717241");
    assertLevel(orderBook.getBids().get(0), "0.00584", "0.00608025");
    assertLevel(orderBook.getBids().get(32), "0.00000001", "10000");
  }

  @Test
  public void orderBooksIgnoreNonObjectPayload() throws Exception {
    YoBitOrderBooksReturn orderBooksReturn = mapper.readValue("[]", YoBitOrderBooksReturn.class);

    assertThat(orderBooksReturn.orderBooks).isEmpty();
  }

  @Test
  public void tickersDeserialization() throws Exception {
    YoBitTickersReturn tickersReturn = mapper.readValue(TICKERS_JSON, YoBitTickersReturn.class);

    assertThat(tickersReturn.tickers).containsOnlyKeys("ltc_btc", "eth_btc");
    YoBitTicker ticker = tickersReturn.tickers.get("ltc_btc");

    assertThat(ticker.getHigh()).isEqualByComparingTo("0.0061");
    assertThat(ticker.getLow()).isEqualByComparingTo("0.0055");
    assertThat(ticker.getAvg()).isEqualByComparingTo("0.0058");
    assertThat(ticker.getVol()).isEqualByComparingTo("12.5");
    assertThat(ticker.getVolCur()).isEqualByComparingTo("2150.75");
    assertThat(ticker.getLast()).isEqualByComparingTo("0.00584");
    assertThat(ticker.getBuy()).isEqualByComparingTo("0.00583");
    assertThat(ticker.getSell()).isEqualByComparingTo("0.00585");
    assertThat(ticker.getUpdated()).isEqualTo(1477580702L);
    assertThat(tickersReturn.tickers.get("eth_btc").getLast()).isEqualByComparingTo("0.012");
  }

  @Test
  public void tradesDeserialization() throws Exception {
    YoBitTrades trades = mapper.readValue(resource("example-trades-data.json"), YoBitTrades.class);

    assertThat(trades.getTrades()).containsOnlyKeys("ltc_btc");
    List<YoBitTrade> ltcBtc = trades.getTrades().get("ltc_btc");

    assertThat(ltcBtc).hasSize(150);
    assertTrade(ltcBtc.get(0), "bid", "0.005755", "0.4", 30520719L, 1477580702L);
    assertTrade(ltcBtc.get(149), "bid", "0.006098", "0.3", 30255574L, 1477499941L);
  }

  @Test
  public void repeatedDeserializationYieldsEqualResults() throws Exception {
    assertThat(readOrderBooks()).usingRecursiveComparison().isEqualTo(readOrderBooks());
    assertThat(readTickers()).usingRecursiveComparison().isEqualTo(readTickers());
    assertThat(readTrades()).usingRecursiveComparison().isEqualTo(readTrades());
  }

  @Test
  public void repeatedDeserializationCreatesIndependentResults() throws Exception {
    YoBitTickersReturn first = readTickers();
    YoBitTickersReturn second = readTickers();

    assertThat(first).isNotSameAs(second);
    assertThat(first.tickers.get("ltc_btc")).isNotSameAs(second.tickers.get("ltc_btc"));

    YoBitTrades firstTrades = readTrades();
    YoBitTrades secondTrades = readTrades();

    assertThat(firstTrades.getTrades().get("ltc_btc").get(0))
        .isNotSameAs(secondTrades.getTrades().get("ltc_btc").get(0));
  }

  private YoBitOrderBooksReturn readOrderBooks() throws IOException {
    return mapper.readValue(resource("example-orderbook-data.json"), YoBitOrderBooksReturn.class);
  }

  private YoBitTickersReturn readTickers() throws IOException {
    return mapper.readValue(TICKERS_JSON, YoBitTickersReturn.class);
  }

  private YoBitTrades readTrades() throws IOException {
    return mapper.readValue(resource("example-trades-data.json"), YoBitTrades.class);
  }

  private InputStream resource(String name) {
    InputStream stream = getClass().getResourceAsStream(RESOURCE_ROOT + name);
    assertThat(stream).as("test resource %s", name).isNotNull();
    return stream;
  }

  private static void assertLevel(YoBitAsksBidsData level, String rate, String quantity) {
    assertThat(level.getRate()).isEqualByComparingTo(new BigDecimal(rate));
    assertThat(level.getQuantity()).isEqualByComparingTo(new BigDecimal(quantity));
  }

  private static void assertTrade(
      YoBitTrade trade, String type, String price, String amount, long tid, long timestamp) {
    assertThat(trade.getType()).isEqualTo(type);
    assertThat(trade.getPrice()).isEqualByComparingTo(new BigDecimal(price));
    assertThat(trade.getAmount()).isEqualByComparingTo(new BigDecimal(amount));
    assertThat(trade.getTid()).isEqualTo(tid);
    assertThat(trade.getTimestamp()).isEqualTo(timestamp);
  }
}
