package org.knowm.xchange.cryptocom.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.client.ResilienceRegistries;
import org.knowm.xchange.cryptocom.CryptoCom;
import org.knowm.xchange.cryptocom.CryptoComExchange;
import org.knowm.xchange.cryptocom.dto.CryptoComResponse;
import org.knowm.xchange.cryptocom.dto.marketdata.CryptoComCandlestick;
import org.knowm.xchange.cryptocom.dto.marketdata.CryptoComExpiredSettlementPrice;
import org.knowm.xchange.cryptocom.dto.marketdata.CryptoComInstrument;
import org.knowm.xchange.cryptocom.dto.marketdata.CryptoComRiskParameters;

/** Raw market-data service coverage for the Phase-2 public reference endpoints. */
public class CryptoComMarketDataServiceRawTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private CryptoCom cryptoCom;
  private CryptoComMarketDataServiceRaw raw;

  @BeforeEach
  public void setUp() {
    cryptoCom = mock(CryptoCom.class);
    CryptoComExchange exchange = mock(CryptoComExchange.class);
    ExchangeSpecification spec = new ExchangeSpecification(CryptoComExchange.class);
    when(exchange.getExchangeSpecification()).thenReturn(spec);
    when(exchange.getCryptoCom()).thenReturn(cryptoCom);
    when(exchange.nextRequestId()).thenReturn(1L);
    raw = new CryptoComMarketDataServiceRaw(exchange, new ResilienceRegistries());
  }

  private CryptoComResponse load(String resource) throws IOException {
    return objectMapper.readValue(
        CryptoComMarketDataServiceRawTest.class.getResourceAsStream(resource),
        CryptoComResponse.class);
  }

  @Test
  public void candlesHaveAscendingRowsInWindowOrder() throws IOException {
    when(cryptoCom.getCandlestick(
            eq("BTCUSD-PERP"), eq("M5"), isNull(), eq(1771761338000L), eq(1771761638000L)))
        .thenReturn(
            load(
                "/org/knowm/xchange/cryptocom/dto/marketdata/get-candlestick.json"));

    List<CryptoComCandlestick> candles =
        raw.getCryptoComCandles(
            "BTCUSD-PERP", "M5", null, 1771761338000L, 1771761638000L);

    assertThat(candles).hasSize(3);
    assertThat(candles.get(0).getStartTimeMs()).isEqualTo("1771761038000");
    assertThat(candles.get(2).getClose()).isEqualTo("67231.10");
    verify(cryptoCom)
        .getCandlestick(
            eq("BTCUSD-PERP"), eq("M5"), isNull(), eq(1771761338000L), eq(1771761638000L));
  }

  @Test
  public void expiredSettlementPricesMapped() throws IOException {
    when(cryptoCom.getExpiredSettlementPrice("FUTURE", 1))
        .thenReturn(
            load(
                "/org/knowm/xchange/cryptocom/dto/marketdata/get-expired-settlement-price.json"));

    List<CryptoComExpiredSettlementPrice> prices =
        raw.getCryptoComExpiredSettlementPrices("FUTURE", 1);

    assertThat(prices).hasSize(2);
    assertThat(prices.get(0).getValue()).isEqualTo("62450.11");
  }

  @Test
  public void riskParametersMappedFromResultObject() throws IOException {
    when(cryptoCom.getRiskParameters())
        .thenReturn(
            load("/org/knowm/xchange/cryptocom/dto/marketdata/get-risk-parameters.json"));

    CryptoComRiskParameters risk = raw.getCryptoComRiskParameters();

    assertThat(risk).isNotNull();
    assertThat(risk.getDefaultMaxProductLeverageForPerps()).isEqualTo("100");
    assertThat(risk.getBaseCurrencyConfig()).hasSize(2);
  }

  @Test
  public void instrumentsWalkCursorUntilExhausted() throws IOException {
    CryptoComResponse page1 = load("/org/knowm/xchange/cryptocom/dto/marketdata/get-instruments.json");
    CryptoComResponse page2 = load("/org/knowm/xchange/cryptocom/dto/marketdata/get-instruments.json");
    com.fasterxml.jackson.databind.node.ObjectNode result2 =
        (com.fasterxml.jackson.databind.node.ObjectNode) page2.getResult();
    result2.remove("data"); // second page: no data, no next_cursor -> loop must end
    result2.remove("next_cursor");

    when(cryptoCom.getInstruments(isNull())).thenReturn(page1);
    when(cryptoCom.getInstruments(eq("cursor-1"))).thenReturn(page2);

    // force a continuation token onto the first page
    ((com.fasterxml.jackson.databind.node.ObjectNode) page1.getResult()).put("next_cursor", "cursor-1");

    List<CryptoComInstrument> instruments = raw.getCryptoComInstruments(null);

    assertThat(instruments).hasSize(7);
    verify(cryptoCom).getInstruments(isNull());
    verify(cryptoCom).getInstruments(eq("cursor-1"));
  }

  @Test
  public void instrumentsStopAfterHardPageBound() throws IOException {
    CryptoComResponse page = load("/org/knowm/xchange/cryptocom/dto/marketdata/get-instruments.json");
    ((com.fasterxml.jackson.databind.node.ObjectNode) page.getResult()).put("next_cursor", "always-same");
    when(cryptoCom.getInstruments(any())).thenReturn(page);

    List<CryptoComInstrument> instruments = raw.getCryptoComInstruments(null);

    // bounded: exactly MAX_REFERENCE_PAGES rounds, never an infinite loop
    assertThat(instruments).hasSize(7 * CryptoComMarketDataServiceRaw.MAX_REFERENCE_PAGES);
    verify(cryptoCom, org.mockito.Mockito.times(CryptoComMarketDataServiceRaw.MAX_REFERENCE_PAGES))
        .getInstruments(any());
  }
}