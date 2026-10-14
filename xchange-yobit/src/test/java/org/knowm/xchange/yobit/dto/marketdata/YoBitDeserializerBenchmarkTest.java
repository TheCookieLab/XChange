package org.knowm.xchange.yobit.dto.marketdata;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import org.junit.Assert;
import org.junit.Test;

public class YoBitDeserializerBenchmarkTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  public void testOrderBooksDeserialization() throws Exception {
    InputStream is = getClass().getResourceAsStream("/org/knowm/xchange/yobit/service/marketdata/example-orderbook-data.json");
    Assert.assertNotNull(is);
    YoBitOrderBooksReturn orderBooksReturn = mapper.readValue(is, YoBitOrderBooksReturn.class);
    Assert.assertNotNull(orderBooksReturn);
    Assert.assertNotNull(orderBooksReturn.orderBooks.get("ltc_btc"));
  }

  @Test
  public void testTradesDeserialization() throws Exception {
    InputStream is = getClass().getResourceAsStream("/org/knowm/xchange/yobit/service/marketdata/example-trades-data.json");
    Assert.assertNotNull(is);
    YoBitTrades trades = mapper.readValue(is, YoBitTrades.class);
    Assert.assertNotNull(trades);
    Assert.assertNotNull(trades.getTrades().get("ltc_btc"));
  }

  @Test
  public void benchmarkDeserialization() throws Exception {
    byte[] orderBookBytes;
    try (InputStream is = getClass().getResourceAsStream("/org/knowm/xchange/yobit/service/marketdata/example-orderbook-data.json")) {
      orderBookBytes = is.readAllBytes();
    }
    byte[] tradesBytes;
    try (InputStream is = getClass().getResourceAsStream("/org/knowm/xchange/yobit/service/marketdata/example-trades-data.json")) {
      tradesBytes = is.readAllBytes();
    }

    // Warmup
    for (int i = 0; i < 500; i++) {
      mapper.readValue(orderBookBytes, YoBitOrderBooksReturn.class);
      mapper.readValue(tradesBytes, YoBitTrades.class);
    }

    int iterations = 5000;
    long start = System.nanoTime();
    for (int i = 0; i < iterations; i++) {
      mapper.readValue(orderBookBytes, YoBitOrderBooksReturn.class);
      mapper.readValue(tradesBytes, YoBitTrades.class);
    }
    long elapsedMs = (System.nanoTime() - start) / 1_000_000;
    System.out.println("Benchmark time for " + iterations + " iterations: " + elapsedMs + " ms");
  }
}
