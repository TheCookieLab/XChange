package org.knowm.xchange.coinbase.v3.service;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
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
import org.knowm.xchange.coinbase.v3.dto.pricebook.CoinbaseBestBidAsksResponse;
import org.knowm.xchange.coinbase.v3.dto.products.CoinbaseProductCandlesResponse;
import org.knowm.xchange.coinbase.v3.dto.products.CoinbaseProductResponse;
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
}

