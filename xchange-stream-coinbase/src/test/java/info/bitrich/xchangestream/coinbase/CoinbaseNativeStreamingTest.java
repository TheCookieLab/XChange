package info.bitrich.xchangestream.coinbase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import info.bitrich.xchangestream.coinbase.CoinbaseStreamingTestUtils.StubStreamingService;
import io.reactivex.rxjava3.core.Observable;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.coinbase.v3.CoinbaseProductIdentity;
import org.knowm.xchange.coinbase.v3.dto.products.CoinbaseProductResponse;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.CandleStickInterval;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trade;
import org.knowm.xchange.instrument.Instrument;

class CoinbaseNativeStreamingTest {
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String PRODUCT = "ETP-20DEC30-CDE";

  @Test
  void nativeTradesAndTickersKeepContractIdentityAndExcludeSpotPayloads() throws Exception {
    CoinbaseProductIdentity identity = identity();
    Instrument contract = identity.instrument(PRODUCT);
    StubStreamingService transport =
        transport(
            """
        {"events":[{"trades":[
          {"product_id":"ETH-USD","trade_id":"spot","price":"100","size":"9"},
          {"product_id":"ETP-20DEC30-CDE","trade_id":"native","price":"2000","size":"2","side":"BUY"}],
          "tickers":[{"product_id":"ETH-USD","price":"100"},
          {"product_id":"ETP-20DEC30-CDE","price":"2000","best_bid":"1999","best_ask":"2001"}]}]}
        """);
    CoinbaseStreamingMarketDataService service = service(transport, identity);
    Trade trade = service.getTrades(contract).singleOrError().blockingGet();
    assertEquals(contract, trade.getInstrument());
    assertEquals("native", trade.getId());
    assertEquals(new BigDecimal("2"), trade.getOriginalAmount());
    assertEquals(Collections.singletonList(PRODUCT), transport.lastRequest().getProductIds());
    Ticker ticker = service.getTicker(contract).singleOrError().blockingGet();
    assertEquals(contract, ticker.getInstrument());
    assertEquals(new BigDecimal("1999"), ticker.getBid());
    assertEquals(Collections.singletonList(PRODUCT), transport.lastRequest().getProductIds());
    Trade spot = service.getTrades((Instrument) CurrencyPair.ETH_USD).singleOrError().blockingGet();
    assertEquals(CurrencyPair.ETH_USD, spot.getInstrument());
    assertEquals("spot", spot.getId());
  }

  @Test
  void nativeCandlesAndDepthFilterByExactProduct() throws Exception {
    CoinbaseProductIdentity identity = identity();
    Instrument contract = identity.instrument(PRODUCT);
    StubStreamingService transport =
        transport(
            """
        {"events":[{"type":"snapshot","product_id":"ETP-20DEC30-CDE","sequence":1,
          "updates":[{"side":"bid","price_level":"1999","new_quantity":"2"},
          {"side":"offer","price_level":"2001","new_quantity":"3"}],"candles":[
          {"product_id":"ETH-USD","start":"1704067200","open":"100","close":"101"},
          {"product_id":"ETP-20DEC30-CDE","start":"1704067200","open":"2000","close":"2001","high":"2002","low":"1999","volume":"7"}]},
          {"type":"snapshot","product_id":"ETH-USD","sequence":2,"bids":[["100","99"]],"asks":[["101","99"]]},
          {"type":"snapshot","sequence":3,"bids":[["1","99"]],"asks":[["2","99"]]}]}
        """);
    CoinbaseStreamingMarketDataService service = service(transport, identity);
    assertEquals(
        new BigDecimal("2001"),
        service.getCandles(contract).singleOrError().blockingGet().getClose());
    assertEquals(Collections.singletonList(PRODUCT), transport.lastRequest().getProductIds());
    var genericCandles =
        service.getCandleStick(contract, CandleStickInterval.m5).singleOrError().blockingGet();
    assertEquals(contract, genericCandles.getInstrument());
    assertEquals(new BigDecimal("2001"), genericCandles.getCandleSticks().get(0).getClose());
    assertThrows(
        IllegalArgumentException.class,
        () -> service.getCandleStick(contract, CandleStickInterval.m1));
    OrderBook book = service.getOrderBook(contract).singleOrError().blockingGet();
    assertEquals(contract, book.getBids().get(0).getInstrument());
    assertEquals(new BigDecimal("1999"), book.getBids().get(0).getLimitPrice());
    assertEquals(new BigDecimal("3"), book.getAsks().get(0).getOriginalAmount());
    assertEquals(Collections.singletonList(PRODUCT), transport.lastRequest().getProductIds());
  }

  @Test
  void sharedProductOverrideKeepsEachRequestedOrderBookInstrument() throws Exception {
    StubStreamingService transport =
        transport(
            """
        {"events":[{"type":"snapshot","product_id":"ETP-20DEC30-CDE","sequence":1,
          "bids":[["1999","2"]],"asks":[["2001","3"]]}]}
        """);
    ExchangeSpecification spec = new ExchangeSpecification(CoinbaseStreamingExchange.class);
    spec.setExchangeSpecificParametersItem(
        CoinbaseStreamingExchange.PARAM_PRODUCT_ID_OVERRIDE, PRODUCT);
    CoinbaseStreamingMarketDataService service =
        new CoinbaseStreamingMarketDataService(transport, null, spec);
    OrderBook usd = service.getOrderBook(CurrencyPair.ETH_USD).singleOrError().blockingGet();
    CurrencyPair ethUsdc = new CurrencyPair("ETH/USDC");
    OrderBook usdc = service.getOrderBook(ethUsdc).singleOrError().blockingGet();
    assertEquals(CurrencyPair.ETH_USD, usd.getBids().get(0).getInstrument());
    assertEquals(ethUsdc, usdc.getBids().get(0).getInstrument());
    assertEquals(ethUsdc, usdc.getAsks().get(0).getInstrument());
    assertEquals(new BigDecimal("1999"), usdc.getBids().get(0).getLimitPrice());
  }

  @Test
  void nativeSequenceGapRecoversUsingTheContractRatherThanItsSpotPair() throws Exception {
    Instrument contract = identity().instrument(PRODUCT);
    CoinbaseStreamingMarketDataService.OrderBookState state =
        new CoinbaseStreamingMarketDataService.OrderBookState(
            contract,
            requested -> {
              assertEquals(contract, requested);
              return new OrderBook(null, Collections.emptyList(), Collections.emptyList());
            },
            PRODUCT);
    var gaps = state.gapEvents().test();
    state
        .process(
            info.bitrich.xchangestream.coinbase.adapters.CoinbaseStreamingAdapters
                .toStreamingMessage(
                    MAPPER.readTree(
                        "{\"events\":[{\"type\":\"snapshot\",\"product_id\":\"ETP-20DEC30-CDE\","
                            + "\"sequence\":1,\"bids\":[[\"1999\",\"2\"]],\"asks\":[[\"2001\",\"3\"]]}]}")))
        .blockingGet();
    OrderBook recovered =
        state
            .process(
                info.bitrich.xchangestream.coinbase.adapters.CoinbaseStreamingAdapters
                    .toStreamingMessage(
                        MAPPER.readTree(
                            "{\"events\":[{\"type\":\"l2update\",\"product_id\":\"ETP-20DEC30-CDE\","
                                + "\"sequence\":3,\"updates\":[{\"side\":\"bid\",\"price_level\":\"2000\","
                                + "\"new_quantity\":\"4\"}]}]}")))
            .blockingGet();
    assertEquals(new BigDecimal("4"), recovered.getBids().get(0).getOriginalAmount());
    assertEquals(contract, recovered.getBids().get(0).getInstrument());
    gaps.assertValue(
        gap ->
            contract.equals(gap.getInstrument())
                && gap.isRecovered()
                && gap.getExpectedSequence() == 2
                && gap.getReceivedSequence() == 3);
  }

  @Test
  void contractWithoutCatalogFailsBeforeSubscribing() {
    CoinbaseStreamingMarketDataService service =
        service(new StubStreamingService(Observable.never()), null);
    Instrument contract =
        new org.knowm.xchange.derivative.FuturesContract(CurrencyPair.ETH_USD, "PERP");
    assertThrows(
        CoinbaseProductIdentity.AmbiguousMappingException.class, () -> service.getTrades(contract));
    assertThrows(
        CoinbaseProductIdentity.AmbiguousMappingException.class, () -> service.getTicker(contract));
    assertThrows(
        CoinbaseProductIdentity.AmbiguousMappingException.class,
        () -> service.getOrderBook(contract));
    assertThrows(
        CoinbaseProductIdentity.AmbiguousMappingException.class,
        () -> service.getCandles(contract));
  }

  private static CoinbaseProductIdentity identity() throws Exception {
    CoinbaseProductResponse future =
        MAPPER.readValue(
            """
        {"product_id":"ETP-20DEC30-CDE","base_currency_id":"ETH","quote_currency_id":"USD",
         "product_type":"FUTURE","product_venue":"FCM","future_product_details":{"contract_root_unit":"ETH","contract_size":"0.1"}}
        """,
            CoinbaseProductResponse.class);
    CoinbaseProductResponse spot =
        new CoinbaseProductResponse(
            "ETH-USD", null, null, null, null, null, "ETH", "USD", "SPOT", "EXCHANGE", null);
    return CoinbaseProductIdentity.build(Arrays.asList(future, spot));
  }

  private static StubStreamingService transport(String payload) throws Exception {
    return new StubStreamingService(Observable.just(MAPPER.readTree(payload)));
  }

  private static CoinbaseStreamingMarketDataService service(
      StubStreamingService transport, CoinbaseProductIdentity identity) {
    ExchangeSpecification spec = new ExchangeSpecification(CoinbaseStreamingExchange.class);
    spec.setExchangeSpecificParametersItem(
        CoinbaseStreamingExchange.PARAM_PRODUCT_IDENTITY, identity);
    spec.setExchangeSpecificParametersItem(
        CoinbaseStreamingExchange.PARAM_DEFAULT_CANDLE_GRANULARITY,
        CoinbaseCandleGranularity.ONE_MINUTE);
    return new CoinbaseStreamingMarketDataService(transport, null, spec);
  }
}
