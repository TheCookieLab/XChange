package org.knowm.xchange.okx.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.Test;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.client.ResilienceRegistries;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.okx.Okx;
import org.knowm.xchange.okx.OkxExchange;
import org.knowm.xchange.okx.dto.OkxException;
import org.knowm.xchange.okx.dto.OkxInstType;
import org.knowm.xchange.okx.dto.OkxResponse;
import org.knowm.xchange.okx.dto.marketdata.OkxCandleStick;
import org.knowm.xchange.okx.dto.marketdata.OkxFundingRate;
import org.knowm.xchange.okx.dto.marketdata.OkxFundingRateHistory;
import org.knowm.xchange.okx.dto.marketdata.OkxOrderbook;
import org.knowm.xchange.okx.dto.marketdata.OkxTicker;
import org.knowm.xchange.okx.dto.marketdata.OkxTrade;
import org.knowm.xchange.service.trade.params.DefaultCandleStickParamWithLimit;

/**
 * Verifies that the {@link CurrencyPair} overload of {@code getCandleStickData} delegates to the
 * instrument-aware implementation instead of recursing into itself.
 */
public class OkxMarketDataServiceTest {

  private static class StubOkxMarketDataService extends OkxMarketDataService {
    private OkxResponse<List<OkxCandleStick>> historyCandle;
    private OkxResponse<List<OkxTicker>> tickerResponse;
    private OkxResponse<List<OkxTicker>> tickersResponse;
    private OkxResponse<List<OkxTrade>> tradesResponse;
    private OkxResponse<List<OkxOrderbook>> orderbookResponse;
    private OkxResponse<List<OkxFundingRate>> fundingRateResponse;

    StubOkxMarketDataService(OkxExchange exchange, ResilienceRegistries resilienceRegistries) {
      super(exchange, resilienceRegistries);
    }

    void setHistoryCandle(OkxResponse<List<OkxCandleStick>> historyCandle) {
      this.historyCandle = historyCandle;
    }

    void setTickerResponse(OkxResponse<List<OkxTicker>> tickerResponse) {
      this.tickerResponse = tickerResponse;
    }

    void setTickersResponse(OkxResponse<List<OkxTicker>> tickersResponse) {
      this.tickersResponse = tickersResponse;
    }

    void setTradesResponse(OkxResponse<List<OkxTrade>> tradesResponse) {
      this.tradesResponse = tradesResponse;
    }

    void setOrderbookResponse(OkxResponse<List<OkxOrderbook>> orderbookResponse) {
      this.orderbookResponse = orderbookResponse;
    }

    void setFundingRateResponse(OkxResponse<List<OkxFundingRate>> fundingRateResponse) {
      this.fundingRateResponse = fundingRateResponse;
    }

    @Override
    public OkxResponse<List<OkxCandleStick>> getHistoryCandle(
        String instrument, String after, String before, String bar, String limit) {
      return historyCandle;
    }

    @Override
    public OkxResponse<List<OkxTicker>> getOkxTicker(String instrumentId) {
      return tickerResponse;
    }

    @Override
    public OkxResponse<List<OkxTicker>> getOkxTickers(OkxInstType instType) {
      return tickersResponse;
    }

    @Override
    public OkxResponse<List<OkxTrade>> getOkxTrades(String instrument, int limit) {
      return tradesResponse;
    }

    @Override
    public OkxResponse<List<OkxOrderbook>> getOkxOrderbook(String instrument) {
      return orderbookResponse;
    }

    @Override
    public OkxResponse<List<OkxFundingRate>> getOkxFundingRate(String instrumentId) {
      return fundingRateResponse;
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

  @Test
  public void fundingRateHistoryPreservesBusinessFailures() throws Exception {
    OkxExchange exchange = new OkxExchange();
    ExchangeSpecification spec = exchange.getDefaultExchangeSpecification();
    spec.setShouldLoadRemoteMetaData(false);
    exchange.applySpecification(spec);

    OkxMarketDataServiceRaw service =
        new OkxMarketDataServiceRaw(exchange, new ResilienceRegistries());
    Okx okx = mock(Okx.class);
    when(okx.getFundingRateHistory(any(), any(), any(), any(), any()))
        .thenReturn(new OkxResponse<>("1", "51000", "Instrument does not exist", null));
    Field okxField = OkxBaseService.class.getDeclaredField("okx");
    okxField.setAccessible(true);
    okxField.set(service, okx);

    assertThatThrownBy(() -> service.getOkxFundingRateHistoryRaw("BTC-USDT", null, null, 5))
        .isInstanceOf(OkxException.class)
        .hasMessageContaining("Instrument does not exist")
        .extracting(e -> ((OkxException) e).getCode())
        .isEqualTo(51000);
  }

  @Test
  public void fundingRateHistoryReturnsDataOnSuccess() throws Exception {
    OkxExchange exchange = new OkxExchange();
    ExchangeSpecification spec = exchange.getDefaultExchangeSpecification();
    spec.setShouldLoadRemoteMetaData(false);
    exchange.applySpecification(spec);

    OkxMarketDataServiceRaw service =
        new OkxMarketDataServiceRaw(exchange, new ResilienceRegistries());
    Okx okx = mock(Okx.class);
    when(okx.getFundingRateHistory(any(), any(), any(), any(), any()))
        .thenReturn(
            new OkxResponse<>(
                "1",
                "0",
                "OK",
                List.of(
                    new OkxFundingRateHistory(
                        "SWAP",
                        "BTC-USDT",
                        new BigDecimal("0.0001"),
                        new BigDecimal("0.0002"),
                        1700000000000L,
                        "derivatives"))));
    Field okxField = OkxBaseService.class.getDeclaredField("okx");
    okxField.setAccessible(true);
    okxField.set(service, okx);

    List<OkxFundingRateHistory> result =
        service.getOkxFundingRateHistoryRaw("BTC-USDT", null, null, 5);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getInstrument()).isEqualTo(new CurrencyPair("BTC", "USDT"));
    assertThat(result.get(0).getFundingRate()).isEqualByComparingTo("0.0002");
  }

  // --- public-endpoint envelope validation ---------------------------------------------

  private StubOkxMarketDataService failingEnvelopeService(
      OkxResponse<List<OkxTicker>> tickerFailure,
      OkxResponse<List<OkxTrade>> tradesFailure,
      OkxResponse<List<OkxOrderbook>> orderbookFailure,
      OkxResponse<List<OkxFundingRate>> fundingRateFailure) {
    OkxExchange exchange = new OkxExchange();
    ExchangeSpecification spec = exchange.getDefaultExchangeSpecification();
    spec.setShouldLoadRemoteMetaData(false);
    exchange.applySpecification(spec);
    StubOkxMarketDataService service =
        new StubOkxMarketDataService(exchange, new ResilienceRegistries());
    service.setTickerResponse(tickerFailure);
    service.setTickersResponse(tickerFailure);
    service.setTradesResponse(tradesFailure);
    service.setOrderbookResponse(orderbookFailure);
    service.setFundingRateResponse(fundingRateFailure);
    return service;
  }

  @Test
  public void publicEndpointsSurfaceBusinessFailures() throws IOException {
    OkxResponse<List<OkxTicker>> tickerFailure =
        new OkxResponse<>("1", "51000", "Instrument does not exist", null);
    OkxResponse<List<OkxTrade>> tradesFailure =
        new OkxResponse<>("1", "51000", "Instrument does not exist", null);
    OkxResponse<List<OkxOrderbook>> orderbookFailure =
        new OkxResponse<>("1", "51000", "Instrument does not exist", null);
    OkxResponse<List<OkxFundingRate>> fundingRateFailure =
        new OkxResponse<>("1", "51000", "Instrument does not exist", null);
    StubOkxMarketDataService service =
        failingEnvelopeService(tickerFailure, tradesFailure, orderbookFailure, fundingRateFailure);
    Instrument instrument = new CurrencyPair("BTC/USDT");

    assertThatThrownBy(() -> service.getTicker(instrument))
        .isInstanceOf(OkxException.class)
        .hasMessageContaining("Instrument does not exist")
        .extracting(e -> ((OkxException) e).getCode())
        .isEqualTo(51000);
    assertThatThrownBy(() -> service.getTrades(instrument))
        .isInstanceOf(OkxException.class)
        .hasMessageContaining("Instrument does not exist");
    assertThatThrownBy(() -> service.getOrderBook(instrument))
        .isInstanceOf(OkxException.class)
        .hasMessageContaining("Instrument does not exist");
    assertThatThrownBy(() -> service.getFundingRate(instrument))
        .isInstanceOf(OkxException.class)
        .hasMessageContaining("Instrument does not exist");
    assertThatThrownBy(() -> service.getTickers(OkxInstType.SWAP))
        .isInstanceOf(OkxException.class)
        .hasMessageContaining("Instrument does not exist");
  }

  @Test
  public void candleStickDataSurfacesBusinessFailures() throws IOException {
    OkxExchange exchange = new OkxExchange();
    ExchangeSpecification spec = exchange.getDefaultExchangeSpecification();
    spec.setShouldLoadRemoteMetaData(false);
    exchange.applySpecification(spec);

    StubOkxMarketDataService service =
        new StubOkxMarketDataService(exchange, new ResilienceRegistries());
    service.setHistoryCandle(new OkxResponse<>("1", "51000", "Instrument does not exist", null));

    assertThatThrownBy(
            () ->
                service.getCandleStickData(
                    new CurrencyPair("BTC/USDT"),
                    new DefaultCandleStickParamWithLimit(
                        Date.from(Instant.parse("2023-07-21T12:00:00Z")),
                        Date.from(Instant.parse("2023-07-22T12:00:00Z")),
                        3600,
                        1)))
        .isInstanceOf(OkxException.class)
        .hasMessageContaining("Instrument does not exist")
        .extracting(e -> ((OkxException) e).getCode())
        .isEqualTo(51000);
  }

  @Test
  public void tickerAndOrderBookRejectEmptySuccessPayloads() throws IOException {
    OkxResponse<List<OkxTicker>> tickerEmpty = new OkxResponse<>("1", "0", "OK", List.of());
    OkxResponse<List<OkxTrade>> tradesEmpty = new OkxResponse<>("1", "0", "OK", List.of());
    OkxResponse<List<OkxOrderbook>> orderbookEmpty = new OkxResponse<>("1", "0", "OK", List.of());
    OkxResponse<List<OkxFundingRate>> fundingRateEmpty =
        new OkxResponse<>("1", "0", "OK", List.of());
    StubOkxMarketDataService service =
        failingEnvelopeService(tickerEmpty, tradesEmpty, orderbookEmpty, fundingRateEmpty);
    Instrument instrument = new CurrencyPair("BTC/USDT");

    assertThatThrownBy(() -> service.getTicker(instrument))
        .isInstanceOf(OkxException.class)
        .hasMessageContaining("Empty data");
    assertThatThrownBy(() -> service.getOrderBook(instrument))
        .isInstanceOf(OkxException.class)
        .hasMessageContaining("Empty data");
    assertThatThrownBy(() -> service.getFundingRate(instrument))
        .isInstanceOf(OkxException.class)
        .hasMessageContaining("Empty data");
  }

  @Test
  public void publicEndpointsRejectMissingPayloads() throws IOException {
    OkxResponse<List<OkxTicker>> tickerMissing = new OkxResponse<>("1", "0", "OK", null);
    OkxResponse<List<OkxTrade>> tradesMissing = new OkxResponse<>("1", "0", "OK", null);
    OkxResponse<List<OkxOrderbook>> orderbookMissing = new OkxResponse<>("1", "0", "OK", null);
    OkxResponse<List<OkxFundingRate>> fundingRateMissing = new OkxResponse<>("1", "0", "OK", null);
    StubOkxMarketDataService service =
        failingEnvelopeService(tickerMissing, tradesMissing, orderbookMissing, fundingRateMissing);
    Instrument instrument = new CurrencyPair("BTC/USDT");

    assertThatThrownBy(() -> service.getTrades(instrument))
        .isInstanceOf(OkxException.class)
        .hasMessageContaining("Missing data");
    assertThatThrownBy(() -> service.getTickers(OkxInstType.SWAP))
        .isInstanceOf(OkxException.class)
        .hasMessageContaining("Missing data");
  }
}
