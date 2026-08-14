package org.knowm.xchange.okx.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.Test;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.client.ResilienceRegistries;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.okx.OkxExchange;
import org.knowm.xchange.okx.dto.OkxResponse;
import org.knowm.xchange.okx.dto.marketdata.OkxCandleStick;
import org.knowm.xchange.service.trade.params.DefaultCandleStickParamWithLimit;

/**
 * Verifies that the {@link CurrencyPair} overload of {@code getCandleStickData} delegates to the
 * instrument-aware implementation instead of recursing into itself.
 */
public class OkxMarketDataServiceTest {

  private static class StubOkxMarketDataService extends OkxMarketDataService {
    private OkxResponse<List<OkxCandleStick>> historyCandle;

    StubOkxMarketDataService(OkxExchange exchange, ResilienceRegistries resilienceRegistries) {
      super(exchange, resilienceRegistries);
    }

    void setHistoryCandle(OkxResponse<List<OkxCandleStick>> historyCandle) {
      this.historyCandle = historyCandle;
    }

    @Override
    public OkxResponse<List<OkxCandleStick>> getHistoryCandle(
        String instrument, String after, String before, String bar, String limit) {
      return historyCandle;
    }
  }

  @Test
  public void currencyPairOverloadDelegatesToInstrumentImplementation() throws IOException {
    ObjectMapper mapper = new ObjectMapper();
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    OkxCandleStick candle =
        mapper.readValue(
            "[1690000000000,\"30000\",\"30150\",\"29900\",\"30100\",\"10\",\"10\",\"300000\",\"0\"]",
            OkxCandleStick.class);

    OkxExchange exchange = new OkxExchange();
    ExchangeSpecification spec = exchange.getDefaultExchangeSpecification();
    spec.setShouldLoadRemoteMetaData(false);
    exchange.applySpecification(spec);

    StubOkxMarketDataService service =
        new StubOkxMarketDataService(exchange, new ResilienceRegistries());
    service.setHistoryCandle(new OkxResponse<>("1", "0", "OK", List.of(candle)));

    var result =
        service.getCandleStickData(
            new CurrencyPair("BTC/USDT"),
            new DefaultCandleStickParamWithLimit(
                Date.from(Instant.parse("2023-07-21T12:00:00Z")),
                Date.from(Instant.parse("2023-07-22T12:00:00Z")),
                3600,
                1));

    assertThat(result.getCandleSticks()).hasSize(1);
    assertThat(result.getCandleSticks().get(0).getOpen()).isEqualByComparingTo("30000");
    assertThat(result.getCandleSticks().get(0).getHigh()).isEqualByComparingTo("30150");
    assertThat(result.getCandleSticks().get(0).getLow()).isEqualByComparingTo("29900");
    assertThat(result.getCandleSticks().get(0).getClose()).isEqualByComparingTo("30100");
    assertThat(result.getCandleSticks().get(0).getVolume()).isEqualByComparingTo("10");
  }
}
