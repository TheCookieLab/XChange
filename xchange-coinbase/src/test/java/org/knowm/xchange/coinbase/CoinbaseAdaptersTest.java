package org.knowm.xchange.coinbase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Collections;
import org.junit.Test;
import org.knowm.xchange.coinbase.v3.dto.futures.CoinbaseFuturesPosition;
import org.knowm.xchange.coinbase.v3.dto.futures.CoinbaseFuturesBalanceSummaryResponse;
import org.knowm.xchange.coinbase.v3.dto.perpetuals.CoinbasePerpetualsPosition;
import org.knowm.xchange.coinbase.v3.dto.perpetuals.CoinbasePerpetualsBalancesResponse;
import org.knowm.xchange.coinbase.v3.dto.pricebook.CoinbasePriceBook;
import org.knowm.xchange.coinbase.v3.dto.pricebook.CoinbasePriceBookEntry;
import org.knowm.xchange.coinbase.v3.dto.products.CoinbaseProductCandle;
import org.knowm.xchange.coinbase.v3.dto.products.CoinbaseProductCandlesResponse;
import org.knowm.xchange.coinbase.v3.dto.products.CoinbaseProductResponse;
import org.knowm.xchange.derivative.FuturesContract;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dto.account.OpenPosition;
import org.knowm.xchange.dto.account.OpenPositions;
import org.knowm.xchange.dto.account.Wallet;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.instrument.Instrument;

/**
 * Unit tests for CoinbaseAdapters.
 * Tests adapter methods to ensure proper null handling and data transformation.
 */
public class CoinbaseAdaptersTest {

  private static final ObjectMapper mapper = new ObjectMapper();

  /**
   * Helper method to create CoinbaseProductCandlesResponse using Jackson deserialization
   * since the constructor is private.
   */
  private CoinbaseProductCandlesResponse createCandlesResponse(CoinbaseProductCandle... candles) 
      throws Exception {
    StringBuilder json = new StringBuilder("{\"candles\":[");
    for (int i = 0; i < candles.length; i++) {
      if (i > 0) json.append(",");
      CoinbaseProductCandle c = candles[i];
      json.append(String.format(
          "{\"start\":\"%s\",\"low\":\"%s\",\"high\":\"%s\",\"open\":\"%s\",\"close\":\"%s\",\"volume\":\"%s\"}",
          c.getStart(), c.getLow(), c.getHigh(), c.getOpen(), c.getClose(), c.getVolume()));
    }
    json.append("]}");
    return mapper.readValue(json.toString(), CoinbaseProductCandlesResponse.class);
  }

  /**
   * Helper method to create empty CoinbaseProductCandlesResponse.
   */
  private CoinbaseProductCandlesResponse createEmptyCandlesResponse() throws Exception {
    String json = "{\"candles\":[]}";
    return mapper.readValue(json, CoinbaseProductCandlesResponse.class);
  }

  @Test
  public void testAdaptTickerWithAllFieldsNonNull() throws Exception {
    // Given: Product with all fields populated
    CoinbaseProductResponse product = new CoinbaseProductResponse(
        "BTC-USD",
        new BigDecimal("50000.00"),
        new BigDecimal("5.25"),
        new BigDecimal("1000.50"),
        new BigDecimal("10.5"),
        new BigDecimal("50025000.00")
    );

    CoinbaseProductCandle candle = new CoinbaseProductCandle(
        "1609459200",
        new BigDecimal("49000.00"),
        new BigDecimal("51000.00"),
        new BigDecimal("50000.00"),
        new BigDecimal("50500.00"),
        new BigDecimal("100.5")
    );
    CoinbaseProductCandlesResponse candlesResponse = createCandlesResponse(candle);

    CoinbasePriceBookEntry ask = new CoinbasePriceBookEntry(
        new BigDecimal("50001.00"), new BigDecimal("0.5"));
    CoinbasePriceBookEntry bid = new CoinbasePriceBookEntry(
        new BigDecimal("49999.00"), new BigDecimal("0.75"));
    CoinbasePriceBook priceBook = new CoinbasePriceBook(
        "BTC-USD",
        Collections.singletonList(bid),
        Collections.singletonList(ask),
        "2024-01-01T00:00:00Z"
    );

    // When: Adapting to ticker
    Ticker ticker = CoinbaseAdapters.adaptTicker(product, candlesResponse, priceBook);

    // Then: All fields should be present
    assertNotNull("Ticker should not be null", ticker);
    assertNotNull("Percentage change should not be null", ticker.getPercentageChange());
    assertEquals("Percentage change should be rounded to 2 sig figs", 
        new BigDecimal("5.2"), ticker.getPercentageChange());
    assertEquals("Volume should match", new BigDecimal("1000.50"), ticker.getVolume());
    assertEquals("Quote volume should match", 
        new BigDecimal("50025000.00"), ticker.getQuoteVolume());
    assertEquals("Ask should match", new BigDecimal("50001.00"), ticker.getAsk());
    assertEquals("Bid should match", new BigDecimal("49999.00"), ticker.getBid());
    assertEquals("Low should match", new BigDecimal("49000.00"), ticker.getLow());
    assertEquals("High should match", new BigDecimal("51000.00"), ticker.getHigh());
    assertEquals("Open should match", new BigDecimal("50000.00"), ticker.getOpen());
    assertEquals("Last should match", new BigDecimal("50500.00"), ticker.getLast());
  }

  @Test
  public void testAdaptTickerWithNullProduct() throws Exception {
    // Given: null product but valid candle and priceBook
    CoinbaseProductCandle candle = new CoinbaseProductCandle(
        "1609459200",
        new BigDecimal("49000.00"),
        new BigDecimal("51000.00"),
        new BigDecimal("50000.00"),
        new BigDecimal("50500.00"),
        new BigDecimal("100.5")
    );
    CoinbaseProductCandlesResponse candlesResponse = createCandlesResponse(candle);

    CoinbasePriceBookEntry ask = new CoinbasePriceBookEntry(
        new BigDecimal("50001.00"), new BigDecimal("0.5"));
    CoinbasePriceBookEntry bid = new CoinbasePriceBookEntry(
        new BigDecimal("49999.00"), new BigDecimal("0.75"));
    CoinbasePriceBook priceBook = new CoinbasePriceBook(
        "BTC-USD",
        Collections.singletonList(bid),
        Collections.singletonList(ask),
        "2024-01-01T00:00:00Z"
    );

    // When: Adapting to ticker with null product
    Ticker ticker = CoinbaseAdapters.adaptTicker(null, candlesResponse, priceBook);

    // Then: Ticker should be created without product fields
    assertNotNull("Ticker should not be null", ticker);
    assertNull("Percentage change should be null", ticker.getPercentageChange());
    assertNull("Volume should be null", ticker.getVolume());
    assertNull("Quote volume should be null", ticker.getQuoteVolume());
    // But candle and priceBook fields should be present
    assertEquals("Ask should match", new BigDecimal("50001.00"), ticker.getAsk());
    assertEquals("Bid should match", new BigDecimal("49999.00"), ticker.getBid());
    assertEquals("Low should match", new BigDecimal("49000.00"), ticker.getLow());
  }

  @Test
  public void testAdaptTickerWithNullPricePercentageChange() {
    // Given: Product with null pricePercentageChange24H
    CoinbaseProductResponse product = new CoinbaseProductResponse(
        "BTC-USD",
        new BigDecimal("50000.00"),
        null, // null percentage change
        new BigDecimal("1000.50"),
        new BigDecimal("10.5"),
        new BigDecimal("50025000.00")
    );

    // When: Adapting to ticker
    Ticker ticker = CoinbaseAdapters.adaptTicker(product, null, null);

    // Then: Should not throw NPE and percentage change should be null
    assertNotNull("Ticker should not be null", ticker);
    assertNull("Percentage change should be null", ticker.getPercentageChange());
    assertEquals("Volume should match", new BigDecimal("1000.50"), ticker.getVolume());
    assertEquals("Quote volume should match", 
        new BigDecimal("50025000.00"), ticker.getQuoteVolume());
  }

  @Test
  public void testAdaptTickerWithNullVolume24H() {
    // Given: Product with null volume24H
    CoinbaseProductResponse product = new CoinbaseProductResponse(
        "BTC-USD",
        new BigDecimal("50000.00"),
        new BigDecimal("5.25"),
        null, // null volume
        new BigDecimal("10.5"),
        new BigDecimal("50025000.00")
    );

    // When: Adapting to ticker
    Ticker ticker = CoinbaseAdapters.adaptTicker(product, null, null);

    // Then: Should not throw NPE and volume should be null
    assertNotNull("Ticker should not be null", ticker);
    assertNotNull("Percentage change should not be null", ticker.getPercentageChange());
    assertNull("Volume should be null", ticker.getVolume());
    assertEquals("Quote volume should match", 
        new BigDecimal("50025000.00"), ticker.getQuoteVolume());
  }

  @Test
  public void testAdaptTickerWithNullApproximateQuoteVolume() {
    // Given: Product with null approximateQuoteVolume24H
    CoinbaseProductResponse product = new CoinbaseProductResponse(
        "BTC-USD",
        new BigDecimal("50000.00"),
        new BigDecimal("5.25"),
        new BigDecimal("1000.50"),
        new BigDecimal("10.5"),
        null // null quote volume
    );

    // When: Adapting to ticker
    Ticker ticker = CoinbaseAdapters.adaptTicker(product, null, null);

    // Then: Should not throw NPE and quote volume should be null
    assertNotNull("Ticker should not be null", ticker);
    assertNotNull("Percentage change should not be null", ticker.getPercentageChange());
    assertEquals("Volume should match", new BigDecimal("1000.50"), ticker.getVolume());
    assertNull("Quote volume should be null", ticker.getQuoteVolume());
  }

  @Test
  public void testAdaptInstrumentForFuturesContract() {
    Instrument instrument = CoinbaseAdapters.adaptInstrument("BTC-USD-PERP");
    assertNotNull("Instrument should not be null", instrument);
    assertEquals("Instrument should be FuturesContract", FuturesContract.class, instrument.getClass());
    assertEquals("BTC/USD/PERP", instrument.toString());
  }

  @Test
  public void testAdaptFuturesOpenPositions() {
    CoinbaseFuturesPosition position =
        new CoinbaseFuturesPosition(
            "BTC-USD-240628",
            "1",
            "LONG",
            new BigDecimal("2"),
            new BigDecimal("30000"),
            new BigDecimal("31000"),
            new BigDecimal("150"),
            "2024-06-28T00:00:00Z",
            new BigDecimal("2"),
            new BigDecimal("50"),
            new BigDecimal("30000"));

    OpenPositions positions =
        CoinbaseAdapters.adaptFuturesOpenPositions(Collections.singletonList(position));

    assertNotNull("Open positions should not be null", positions);
    assertEquals(1, positions.getOpenPositions().size());
    OpenPosition open = positions.getOpenPositions().get(0);
    assertEquals("BTC-USD-240628", open.getId());
    assertEquals(OpenPosition.Type.LONG, open.getType());
    assertEquals(new BigDecimal("2"), open.getSize());
    assertEquals(new BigDecimal("30000"), open.getPrice());
    assertEquals(new BigDecimal("150"), open.getUnRealisedPnl());
  }

  @Test
  public void testAdaptPerpetualsOpenPositions() {
    CoinbasePerpetualsPosition position =
        new CoinbasePerpetualsPosition(
            "BTC-USD-PERP",
            "product-uuid",
            "portfolio-uuid",
            "BTC-USD-PERP",
            "30010",
            "SHORT",
            new BigDecimal("-1.5"),
            "30000",
            new BigDecimal("45000"),
            new BigDecimal("2"),
            new BigDecimal("25"),
            null);

    OpenPositions positions =
        CoinbaseAdapters.adaptPerpetualsOpenPositions(Collections.singletonList(position));

    assertNotNull("Open positions should not be null", positions);
    assertEquals(1, positions.getOpenPositions().size());
    OpenPosition open = positions.getOpenPositions().get(0);
    assertEquals("BTC-USD-PERP", open.getId());
    assertEquals(OpenPosition.Type.SHORT, open.getType());
    assertEquals(new BigDecimal("1.5"), open.getSize());
    assertEquals(new BigDecimal("30000"), open.getPrice());
    assertEquals(new BigDecimal("25"), open.getUnRealisedPnl());
  }

  @Test
  public void testAdaptFuturesWallet() {
    CoinbaseFuturesBalanceSummaryResponse response =
        new CoinbaseFuturesBalanceSummaryResponse(
            new BigDecimal("5000"),
            new BigDecimal("8000"),
            null,
            null,
            null,
            null,
            null,
            null,
            new BigDecimal("3000"),
            null,
            null,
            null,
            null,
            null,
            null);

    Wallet wallet = CoinbaseAdapters.adaptFuturesWallet(response);
    assertNotNull("Wallet should not be null", wallet);
    assertEquals("futures", wallet.getId());
    assertNotNull("Wallet features should not be null", wallet.getFeatures());
    assertEquals(1, wallet.getFeatures().size());
    assertEquals(Wallet.WalletFeature.FUTURES_TRADING, wallet.getFeatures().iterator().next());
    assertEquals(new BigDecimal("8000"), wallet.getBalance(Currency.USD).getTotal());
    assertEquals(new BigDecimal("3000"), wallet.getBalance(Currency.USD).getAvailable());
  }

  @Test
  public void testAdaptPerpetualsWallet() {
    CoinbasePerpetualsBalancesResponse.CoinbasePerpetualsBalances balances =
        new CoinbasePerpetualsBalancesResponse.CoinbasePerpetualsBalances(
            "portfolio-uuid",
            "USD",
            new BigDecimal("1500"),
            new BigDecimal("1200"),
            null,
            null,
            null);
    CoinbasePerpetualsBalancesResponse response =
        new CoinbasePerpetualsBalancesResponse(balances);

    Wallet wallet = CoinbaseAdapters.adaptPerpetualsWallet(response);
    assertNotNull("Wallet should not be null", wallet);
    assertEquals("portfolio-uuid", wallet.getId());
    assertEquals(new BigDecimal("1500"), wallet.getBalance(Currency.USD).getTotal());
    assertEquals(new BigDecimal("1200"), wallet.getBalance(Currency.USD).getAvailable());
  }

  @Test
  public void testAdaptTickerWithAllProductFieldsNull() {
    // Given: Product with all nullable fields as null
    CoinbaseProductResponse product = new CoinbaseProductResponse(
        "BTC-USD",
        null, // null price
        null, // null percentage change
        null, // null volume
        null, // null volume percentage change
        null  // null quote volume
    );

    // When: Adapting to ticker
    Ticker ticker = CoinbaseAdapters.adaptTicker(product, null, null);

    // Then: Should not throw NPE and all product-derived fields should be null
    assertNotNull("Ticker should not be null", ticker);
    assertNull("Percentage change should be null", ticker.getPercentageChange());
    assertNull("Volume should be null", ticker.getVolume());
    assertNull("Quote volume should be null", ticker.getQuoteVolume());
  }

  @Test
  public void testAdaptTickerWithEmptyCandles() throws Exception {
    // Given: CandlesResponse with empty list
    CoinbaseProductCandlesResponse candlesResponse = createEmptyCandlesResponse();

    // When: Adapting to ticker
    Ticker ticker = CoinbaseAdapters.adaptTicker(null, candlesResponse, null);

    // Then: Should not throw exception, candle fields should be null
    assertNotNull("Ticker should not be null", ticker);
    assertNull("Low should be null", ticker.getLow());
    assertNull("High should be null", ticker.getHigh());
    assertNull("Open should be null", ticker.getOpen());
    assertNull("Last should be null", ticker.getLast());
  }

  @Test
  public void testAdaptTickerWithNullCandles() {
    // Given: null candles response
    // When: Adapting to ticker
    Ticker ticker = CoinbaseAdapters.adaptTicker(null, null, null);

    // Then: Should not throw NPE
    assertNotNull("Ticker should not be null", ticker);
    assertNull("Low should be null", ticker.getLow());
    assertNull("High should be null", ticker.getHigh());
  }

  @Test
  public void testAdaptTickerWithEmptyPriceBook() {
    // Given: PriceBook with empty asks/bids
    CoinbasePriceBook priceBook = new CoinbasePriceBook(
        "BTC-USD",
        Collections.emptyList(), // empty bids
        Collections.emptyList(), // empty asks
        "2024-01-01T00:00:00Z"
    );

    // When: Adapting to ticker
    Ticker ticker = CoinbaseAdapters.adaptTicker(null, null, priceBook);

    // Then: Should not throw exception, priceBook fields should be null
    assertNotNull("Ticker should not be null", ticker);
    assertNull("Ask should be null", ticker.getAsk());
    assertNull("Bid should be null", ticker.getBid());
  }

  @Test
  public void testAdaptTickerWithNullPriceBook() {
    // Given: null priceBook
    // When: Adapting to ticker
    Ticker ticker = CoinbaseAdapters.adaptTicker(null, null, null);

    // Then: Should not throw NPE
    assertNotNull("Ticker should not be null", ticker);
    assertNull("Ask should be null", ticker.getAsk());
    assertNull("Bid should be null", ticker.getBid());
  }

  @Test
  public void testAdaptTickerRoundingBehavior() {
    // Given: Product with percentage change that needs rounding
    // MathContext(2, HALF_EVEN) rounds to 2 significant figures with banker's rounding
    CoinbaseProductResponse product1 = new CoinbaseProductResponse(
        "BTC-USD", null, new BigDecimal("5.24"), null, null, null);
    CoinbaseProductResponse product2 = new CoinbaseProductResponse(
        "ETH-USD", null, new BigDecimal("5.25"), null, null, null);
    CoinbaseProductResponse product3 = new CoinbaseProductResponse(
        "LTC-USD", null, new BigDecimal("5.26"), null, null, null);

    // When: Adapting to tickers
    Ticker ticker1 = CoinbaseAdapters.adaptTicker(product1, null, null);
    Ticker ticker2 = CoinbaseAdapters.adaptTicker(product2, null, null);
    Ticker ticker3 = CoinbaseAdapters.adaptTicker(product3, null, null);

    // Then: Should round to 2 significant figures using HALF_EVEN
    assertEquals("5.24 should round to 5.2", 
        new BigDecimal("5.2"), ticker1.getPercentageChange());
    assertEquals("5.25 should round to 5.2 (HALF_EVEN - rounds to even)", 
        new BigDecimal("5.2"), ticker2.getPercentageChange());
    assertEquals("5.26 should round to 5.3", 
        new BigDecimal("5.3"), ticker3.getPercentageChange());
  }
}
