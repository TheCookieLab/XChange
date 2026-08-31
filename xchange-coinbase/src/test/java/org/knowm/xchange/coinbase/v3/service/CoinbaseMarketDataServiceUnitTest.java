package org.knowm.xchange.coinbase.v3.service;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.atLeastOnce;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Collections;
import org.junit.Test;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.coinbase.v3.CoinbaseAuthenticated;
import org.knowm.xchange.coinbase.v3.CoinbaseExchange;
import org.knowm.xchange.coinbase.v3.CoinbaseProductIdentity;
import org.knowm.xchange.coinbase.v3.dto.pricebook.CoinbasePriceBook;
import org.knowm.xchange.coinbase.v3.dto.pricebook.CoinbaseProductPriceBookResponse;
import org.knowm.xchange.derivative.FuturesContract;
import org.knowm.xchange.coinbase.v3.dto.pricebook.CoinbaseBestBidAsksResponse;
import org.knowm.xchange.coinbase.v3.dto.products.CoinbaseProductCandlesResponse;
import org.knowm.xchange.coinbase.v3.dto.products.CoinbaseProductMarketTradesResponse;
import org.knowm.xchange.coinbase.v3.dto.products.CoinbaseProductResponse;
import org.knowm.xchange.service.trade.params.DefaultCandleStickParamWithLimit;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.service.marketdata.MarketDataService;
import si.mazi.rescu.ParamsDigest;

/**
 * Unit tests for CoinbaseMarketDataService.
 * Tests service instantiation and basic structure to prevent regressions.
 */
public class CoinbaseMarketDataServiceUnitTest {

  private static final ObjectMapper mapper = new ObjectMapper();

  @Test
  public void testServiceCreationSucceeds() {
    Exchange exchange = ExchangeFactory.INSTANCE.createExchange(CoinbaseExchange.class);
    MarketDataService service = exchange.getMarketDataService();
    
    assertNotNull("Market data service should not be null", service);
  }

  @Test
  public void testServiceIsCorrectType() {
    Exchange exchange = ExchangeFactory.INSTANCE.createExchange(CoinbaseExchange.class);
    MarketDataService service = exchange.getMarketDataService();
    
    assertNotNull("Service should not be null", service);
    assert(service instanceof CoinbaseMarketDataService);
  }

  /**
   * Test that getTicker handles empty priceBooks gracefully without throwing IndexOutOfBoundsException.
   * This test verifies the safety check added to prevent accessing priceBooks.get(0) when the list is empty.
   */
  @Test
  public void testGetTickerWithEmptyPriceBooks() throws IOException {
    // Setup mocks
    Exchange exchange = mock(Exchange.class);
    CoinbaseAuthenticated api = mock(CoinbaseAuthenticated.class);
    ParamsDigest digest = mock(ParamsDigest.class);
    
    // Create a spy of the service so we can mock specific methods
    CoinbaseMarketDataService service = spy(new CoinbaseMarketDataService(exchange, api, digest));
    
    // Create test data
    String productId = "BTC-USD";
    CurrencyPair currencyPair = CurrencyPair.BTC_USD;
    
    // Mock getProduct to return a valid product response
    CoinbaseProductResponse product = new CoinbaseProductResponse(
        productId,
        new BigDecimal("50000.00"),
        new BigDecimal("2.5"),
        new BigDecimal("1000.0"),
        new BigDecimal("5.0"),
        new BigDecimal("50000000.0")
    );
    when(service.getProduct(productId)).thenReturn(product);
    
    // Mock getBestBidAsk to return an empty priceBooks list - this is the key test case
    CoinbaseBestBidAsksResponse emptyPriceBooksResponse = new CoinbaseBestBidAsksResponse(
        Collections.emptyList()
    );
    when(service.getBestBidAsk(productId)).thenReturn(emptyPriceBooksResponse);
    
    // Mock getProductCandles to return a valid candles response
    // Use Jackson to create the response since the constructor is package-private
    CoinbaseProductCandlesResponse candles = mapper.readValue(
        "{\"candles\":[]}", CoinbaseProductCandlesResponse.class);
    when(service.getProductCandles(productId, "ONE_DAY", 1, null, null)).thenReturn(candles);
    
    // Execute - this should not throw IndexOutOfBoundsException
    // Cast to Instrument to avoid calling the deprecated CurrencyPair overload
    Ticker ticker = service.getTicker((Instrument) currencyPair);
    
    // Verify that a ticker is returned (even without price book data, adaptTicker should handle null)
    assertNotNull("Ticker should not be null even with empty priceBooks", ticker);
  }

  @Test
  public void testInstrumentRequestsUseCatalogNativeProductId() throws IOException {
    String productId = "ETP-20DEC30-CDE";
    CoinbaseProductIdentity catalog =
        CoinbaseProductIdentity.build(
            Collections.singletonList(
                new CoinbaseProductResponse(
                    productId, null, null, null, null, null, "ETH", "USD", "FUTURE", "CDE", null)));
    FuturesContract instrument = (FuturesContract) catalog.instrument(productId);
    Exchange exchange = mock(Exchange.class);
    CoinbaseAuthenticated api = mock(CoinbaseAuthenticated.class);
    ParamsDigest digest = mock(ParamsDigest.class);
    CoinbaseMarketDataService service =
        spy(new CoinbaseMarketDataService(exchange, api, digest, catalog));

    CoinbaseProductResponse product =
        new CoinbaseProductResponse(
            productId, new BigDecimal("2500"), null, null, null, null, "ETH", "USD", "FUTURE", "CDE", null);
    when(service.getProduct(productId)).thenReturn(product);
    when(service.getBestBidAsk(productId))
        .thenReturn(new CoinbaseBestBidAsksResponse(Collections.emptyList()));
    when(service.getProductCandles(productId, "ONE_DAY", 1, null, null))
        .thenReturn(mapper.readValue("{\"candles\":[]}", CoinbaseProductCandlesResponse.class));
    CoinbasePriceBook priceBook =
        new CoinbasePriceBook(productId, Collections.emptyList(), Collections.emptyList(), "2026-01-01T00:00:00Z");
    when(service.getProductBook(productId, null, null))
        .thenReturn(new CoinbaseProductPriceBookResponse(priceBook, null, null, null, null));
    CoinbaseProductMarketTradesResponse trades =
        mapper.readValue("{}", CoinbaseProductMarketTradesResponse.class);
    when(service.getMarketTrades(productId, null, null, null)).thenReturn(trades);
    try {
      service.getTicker(instrument);
    } catch (RuntimeException expected) {
      // CDE responses are intentionally opaque to generic ticker adaptation.
    }
    try {
      service.getOrderBook(instrument);
    } catch (RuntimeException expected) {
      // CDE order books are intentionally opaque to generic order-book adaptation.
    }
    service.getTrades(instrument);
    service.getCandleStickData(
        instrument, new DefaultCandleStickParamWithLimit(null, null, 86_400, 1));

    verify(service, atLeastOnce()).getProductCandles(productId, "ONE_DAY", 1, null, null);

    verify(service, atLeastOnce()).getBestBidAsk(productId);
    verify(service, atLeastOnce()).getProductBook(productId, null, null);
    verify(service, atLeastOnce()).getMarketTrades(productId, null, null, null);
    assertEquals(productId, catalog.requireProductId(instrument));
  }

  @Test
  public void testAuthenticatedBestBidAskUsesProductListOverload() throws IOException {
    Exchange exchange = mock(Exchange.class);
    CoinbaseAuthenticated api = mock(CoinbaseAuthenticated.class);
    ParamsDigest digest = mock(ParamsDigest.class);
    CoinbaseMarketDataServiceRaw service =
        new CoinbaseMarketDataServiceRaw(exchange, api, digest);
    String productId = "ETP-20DEC30-CDE";
    CoinbaseBestBidAsksResponse response = mock(CoinbaseBestBidAsksResponse.class);
    when(api.getBestBidAsks(same(digest), eq(Collections.singletonList(productId))))
        .thenReturn(response);

    CoinbaseBestBidAsksResponse actual = service.getBestBidAsk(productId);

    assertSame("The raw response instance must be returned unchanged", response, actual);
    verify(api).getBestBidAsks(same(digest), eq(Collections.singletonList(productId)));
    verify(api, never()).getBestBidAsk(same(digest), eq(productId));
  }

  @Test
  public void testAuthenticatedBestBidAskUsesNullProductListForNullId() throws IOException {
    Exchange exchange = mock(Exchange.class);
    CoinbaseAuthenticated api = mock(CoinbaseAuthenticated.class);
    ParamsDigest digest = mock(ParamsDigest.class);
    CoinbaseMarketDataServiceRaw service =
        new CoinbaseMarketDataServiceRaw(exchange, api, digest);
    CoinbaseBestBidAsksResponse response = mock(CoinbaseBestBidAsksResponse.class);
    when(api.getBestBidAsks(same(digest), isNull())).thenReturn(response);

    CoinbaseBestBidAsksResponse actual = service.getBestBidAsk(null);

    assertSame("The raw response instance must be returned unchanged", response, actual);
    verify(api).getBestBidAsks(same(digest), isNull());
    verify(api, never()).getBestBidAsk(same(digest), isNull());
  }
}

