package org.knowm.xchange.coinbase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.Path;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.derivative.FuturesContract;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.StopOrder;
import org.knowm.xchange.coinbase.v3.CoinbaseAuthenticated;
import org.knowm.xchange.coinbase.v3.dto.accounts.CoinbaseAmount;
import org.knowm.xchange.coinbase.v3.dto.futures.CoinbaseCurrentMarginWindowResponse;
import org.knowm.xchange.coinbase.v3.dto.futures.CoinbaseFuturesBalanceSummary;
import org.knowm.xchange.coinbase.v3.dto.futures.CoinbaseFuturesBalanceSummaryResponse;
import org.knowm.xchange.coinbase.v3.dto.futures.CoinbaseFuturesPosition;
import org.knowm.xchange.coinbase.v3.dto.futures.CoinbaseMarginWindowMeasure;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseCancelOrdersResponse;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseMarketMarketIoc;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseOrderConfiguration;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseOrderDetail;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseOrdersResponse;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseFill;
import org.knowm.xchange.coinbase.v3.dto.trade.CoinbaseUserTrade;
import org.knowm.xchange.coinbase.v3.dto.perpetuals.CoinbasePerpetualsPosition;
import org.knowm.xchange.coinbase.v3.dto.perpetuals.CoinbasePerpetualsBalancesResponse;
import org.knowm.xchange.coinbase.v3.dto.futures.CoinbaseFuturesPositionsResponse;
import org.knowm.xchange.coinbase.v3.dto.transactions.CoinbaseFeeTier;
import org.knowm.xchange.coinbase.v3.dto.transactions.CoinbaseTransactionSummaryResponse;
import org.knowm.xchange.coinbase.v3.dto.pricebook.CoinbasePriceBook;
import org.knowm.xchange.coinbase.v3.dto.pricebook.CoinbaseBestBidAsksResponse;
import org.knowm.xchange.coinbase.v3.dto.pricebook.CoinbasePriceBookEntry;
import org.knowm.xchange.coinbase.v3.dto.products.CoinbaseProductCandle;
import org.knowm.xchange.coinbase.v3.dto.products.CoinbaseMarketTrade;
import org.knowm.xchange.coinbase.v3.dto.products.CoinbaseProductCandlesResponse;
import org.knowm.xchange.coinbase.v3.dto.products.CoinbaseProductResponse;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.exceptions.NotAvailableFromExchangeException;
import org.knowm.xchange.dto.account.OpenPosition;
import org.knowm.xchange.dto.account.OpenPositions;
import org.knowm.xchange.dto.account.Wallet;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.instrument.Instrument;
import si.mazi.rescu.ParamsDigest;

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
            "2024-06-28T00:00:00Z",
            "LONG",
            new BigDecimal("2"),
            new BigDecimal("31000"),
            new BigDecimal("30000"),
            new BigDecimal("150"),
            new BigDecimal("50"));

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
  public void testAdaptLegacyFuturesPositionFallsBackToAmountAndEntryPrice() throws Exception {
    CoinbaseFuturesPosition position =
        legacyFuturesPosition(
            "BTC-USD-PERP",
            "1",
            "LONG",
            new BigDecimal("3"),
            null,
            new BigDecimal("31000"),
            new BigDecimal("150"),
            "2026-12-20T00:00:00Z",
            null,
            BigDecimal.ZERO,
            new BigDecimal("29900"));

    OpenPosition open =
        CoinbaseAdapters.adaptFuturesOpenPositions(Collections.singletonList(position))
            .getOpenPositions()
            .get(0);

    assertEquals(new BigDecimal("3"), open.getSize());
    assertEquals(new BigDecimal("29900"), open.getPrice());
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
            new CoinbaseFuturesBalanceSummary(
                new CoinbaseAmount("USD", new BigDecimal("5000")),
                new CoinbaseAmount("USD", new BigDecimal("8000")),
                null, null, null, null, null, null,
                new CoinbaseAmount("USD", new BigDecimal("3000")),
                null, null, null, null, null, null, null));

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
  @Test
  public void testAdaptOrderReadsNestedMarketConfiguration() {
    CoinbaseOrderConfiguration configuration =
        CoinbaseOrderConfiguration.marketMarketIoc(
            new CoinbaseMarketMarketIoc(null, new BigDecimal("2.5")));
    CoinbaseOrderDetail nested =
        new CoinbaseOrderDetail(
            "order", "client", "BUY", "BTC-USD", null, "FILLED", "MARKET", "IOC", null, null,
            configuration, null, null, null, null, null, null, null, null, null, false, false,
            false, true, "2026-02-08T00:00:00Z", null, null, null, null, null, null, null);

    Order adapted = CoinbaseAdapters.adaptOrder(nested);

    assertEquals(new BigDecimal("2.5"), adapted.getOriginalAmount());
  }
  @Test
  public void testDeserializeCurrentCfmWireShapes() throws Exception {
    String balanceJson =
        "{\"balance_summary\":{"
            + "\"futures_buying_power\":{\"value\":\"5000\",\"currency\":\"USD\"},"
            + "\"total_usd_balance\":{\"value\":\"5100\",\"currency\":\"USD\"},"
            + "\"cbi_usd_balance\":{\"value\":\"100\",\"currency\":\"USD\"},"
            + "\"cfm_usd_balance\":{\"value\":\"5000\",\"currency\":\"USD\"},"
            + "\"total_open_orders_hold_amount\":{\"value\":\"50\",\"currency\":\"USD\"},"
            + "\"unrealized_pnl\":{\"value\":\"25\",\"currency\":\"USD\"},"
            + "\"daily_realized_pnl\":{\"value\":\"10\",\"currency\":\"USD\"},"
            + "\"initial_margin\":{\"value\":\"400\",\"currency\":\"USD\"},"
            + "\"available_margin\":{\"value\":\"4500\",\"currency\":\"USD\"},"
            + "\"liquidation_threshold\":{\"value\":\"250\",\"currency\":\"USD\"},"
            + "\"liquidation_buffer_amount\":{\"value\":\"125.5\",\"currency\":\"USD\"},"
            + "\"liquidation_buffer_percentage\":\"0.05\","
            + "\"intraday_margin_window_measure\":{\"margin_window_type\":\"FCM_MARGIN_WINDOW_TYPE_INTRADAY\","
            + "\"margin_level\":\"MARGIN_LEVEL_TYPE_BASE\",\"initial_margin\":\"400\","
            + "\"maintenance_margin\":\"300\",\"liquidation_buffer\":\"125.5\","
            + "\"total_hold\":\"50\",\"futures_buying_power\":\"5000\"},"
            + "\"overnight_margin_window_measure\":{\"margin_window_type\":\"FCM_MARGIN_WINDOW_TYPE_OVERNIGHT\","
            + "\"margin_level\":\"MARGIN_LEVEL_TYPE_BASE\",\"initial_margin\":\"450\","
            + "\"maintenance_margin\":\"350\",\"liquidation_buffer\":\"100.5\","
            + "\"total_hold\":\"60\",\"futures_buying_power\":\"4900\"},"
            + "\"total_pending_transfers_amount\":{\"value\":\"5\",\"currency\":\"USD\"},"
            + "\"funding_pnl\":{\"value\":\"-2.5\",\"currency\":\"USD\"}}}";
    CoinbaseFuturesBalanceSummaryResponse balance =
        new ObjectMapper().readValue(balanceJson, CoinbaseFuturesBalanceSummaryResponse.class);
    CoinbaseFuturesBalanceSummary summary = balance.getBalanceSummary();
    assertAmount(summary.getFuturesBuyingPower(), "5000");
    assertAmount(summary.getTotalUsdBalance(), "5100");
    assertAmount(summary.getCbiUsdBalance(), "100");
    assertAmount(summary.getCfmUsdBalance(), "5000");
    assertAmount(summary.getTotalOpenOrdersHoldAmount(), "50");
    assertAmount(summary.getUnrealizedPnl(), "25");
    assertAmount(summary.getDailyRealizedPnl(), "10");
    assertAmount(summary.getInitialMargin(), "400");
    assertAmount(summary.getAvailableMargin(), "4500");
    assertAmount(summary.getLiquidationThreshold(), "250");
    assertAmount(summary.getLiquidationBufferAmount(), "125.5");
    assertEquals("0.05", summary.getLiquidationBufferPercentage());
    assertEquals("FCM_MARGIN_WINDOW_TYPE_INTRADAY",
        summary.getIntradayMarginWindowMeasure().getMarginWindowType());
    assertEquals("MARGIN_LEVEL_TYPE_BASE",
        summary.getIntradayMarginWindowMeasure().getMarginLevel());
    assertEquals(new BigDecimal("400"),
        summary.getIntradayMarginWindowMeasure().getInitialMargin());
    assertEquals(new BigDecimal("300"),
        summary.getIntradayMarginWindowMeasure().getMaintenanceMargin());
    assertEquals(new BigDecimal("125.5"),
        summary.getIntradayMarginWindowMeasure().getLiquidationBuffer());
    assertEquals(new BigDecimal("50"), summary.getIntradayMarginWindowMeasure().getTotalHold());
    assertEquals(new BigDecimal("5000"),
        summary.getIntradayMarginWindowMeasure().getFuturesBuyingPower());
    assertEquals("FCM_MARGIN_WINDOW_TYPE_OVERNIGHT",
        summary.getOvernightMarginWindowMeasure().getMarginWindowType());
    assertEquals("MARGIN_LEVEL_TYPE_BASE",
        summary.getOvernightMarginWindowMeasure().getMarginLevel());
    assertEquals(new BigDecimal("450"),
        summary.getOvernightMarginWindowMeasure().getInitialMargin());
    assertEquals(new BigDecimal("350"),
        summary.getOvernightMarginWindowMeasure().getMaintenanceMargin());
    assertEquals(new BigDecimal("100.5"),
        summary.getOvernightMarginWindowMeasure().getLiquidationBuffer());
    assertEquals(new BigDecimal("60"), summary.getOvernightMarginWindowMeasure().getTotalHold());
    assertEquals(new BigDecimal("4900"),
        summary.getOvernightMarginWindowMeasure().getFuturesBuyingPower());
    assertAmount(summary.getTotalPendingTransfersAmount(), "5");
    assertAmount(summary.getFundingPnl(), "-2.5");

    CoinbaseFuturesPosition position =
        new ObjectMapper().readValue(
            "{\"product_id\":\"ETP-20DEC30-CDE\",\"expiration_time\":\"2026-12-20T00:00:00Z\","
                + "\"side\":\"LONG\",\"number_of_contracts\":\"2.5\",\"contract_size\":\"0.1\","
                + "\"amount\":\"0.25\",\"expiry_time\":\"2026-12-20T00:00:00Z\","
                + "\"realized_pnl\":\"4.5\",\"entry_price\":\"3200\"}",
            CoinbaseFuturesPosition.class);
    assertEquals(new BigDecimal("2.5"), position.getNumberOfContracts());
    assertEquals("0.1", position.getContractSize());
    assertEquals(new BigDecimal("0.25"), position.getAmount());
    assertEquals("2026-12-20T00:00:00Z", position.getExpiryTime());
    assertEquals(new BigDecimal("4.5"), position.getRealizedPnl());
    assertEquals(new BigDecimal("3200"), position.getEntryPrice());

    CoinbaseTransactionSummaryResponse transactionSummary =
        new ObjectMapper().readValue(
            "{\"margin_rate\":{\"value\":\"0.12\"}}",
            CoinbaseTransactionSummaryResponse.class);
    assertEquals(new BigDecimal("0.12"), transactionSummary.getMarginRate());

    CoinbaseCurrentMarginWindowResponse marginWindow =
        new ObjectMapper().readValue(
            "{\"margin_window\":{\"margin_window_type\":\"MARGIN_WINDOW_TYPE_INTRADAY\","
                + "\"end_time\":\"2026-08-31T20:00:00Z\"},"
                + "\"is_intraday_margin_killswitch_enabled\":false,"
                + "\"is_intraday_margin_enrollment_killswitch_enabled\":true}",
            CoinbaseCurrentMarginWindowResponse.class);
    assertEquals("MARGIN_WINDOW_TYPE_INTRADAY", marginWindow.getMarginWindow());
    assertEquals("MARGIN_WINDOW_TYPE_INTRADAY", marginWindow.getMarginWindowType());
    assertEquals("2026-08-31T20:00:00Z", marginWindow.getEndTime());
    assertEquals(Boolean.FALSE, marginWindow.getIsIntradayMarginKillswitchEnabled());
    assertEquals(
        Boolean.TRUE, marginWindow.getIsIntradayMarginEnrollmentKillswitchEnabled());
  }

  @Test
  public void testCatalogResolvedCdePositionRetainsFuturesContract() throws Exception {
    CoinbaseFuturesPosition position =
        new ObjectMapper()
            .readValue(
                "{\"product_id\":\"ETP-20DEC30-CDE\",\"side\":\"LONG\","
                    + "\"number_of_contracts\":\"2.5\",\"contract_size\":\"0.1\","
                    + "\"amount\":\"0.25\",\"entry_price\":\"3200\"}",
                CoinbaseFuturesPosition.class);
    FuturesContract configured = new FuturesContract(CurrencyPair.ETH_USD, "CDE");

    assertEquals(
        configured,
        CoinbaseAdapters.adaptFuturesOpenPositions(
                Collections.singletonList(position), ignored -> configured)
            .getOpenPositions()
            .get(0)
            .getInstrument());
  }

  @Test
  public void testRejectOpaqueCdeInstrumentFromGenericAdapters() {
    String productId = "ETP-20DEC30-CDE";
    CoinbasePriceBookEntry entry = new CoinbasePriceBookEntry(BigDecimal.TEN, BigDecimal.ONE);
    CoinbasePriceBook priceBook =
        new CoinbasePriceBook(
            productId,
            Collections.singletonList(entry),
            Collections.singletonList(entry),
            "2026-02-08T00:00:00Z");
    CoinbaseMarketTrade trade =
        new CoinbaseMarketTrade(
            "trade",
            productId,
            BigDecimal.TEN,
            BigDecimal.ONE,
            "2026-02-08T00:00:00Z",
            "BUY",
            null,
            null,
            "coinbase");
    CoinbaseFuturesPosition position =
        new CoinbaseFuturesPosition(
            productId,
            "2026-12-20T00:00:00Z",
            "LONG",
            BigDecimal.ONE,
            BigDecimal.TEN,
            BigDecimal.TEN,
            BigDecimal.ZERO,
            BigDecimal.ZERO);

    assertUnavailable(() -> CoinbaseAdapters.adaptOrderBook(priceBook));
    assertUnavailable(() -> CoinbaseAdapters.adaptTrade(trade));
    assertUnavailable(() -> CoinbaseAdapters.adaptTicker(null, null, priceBook));
    assertUnavailable(
        () -> CoinbaseAdapters.adaptFuturesOpenPositions(Collections.singletonList(position)));
  }

  @Test
  public void testAdaptQuoteSizedFillConvertsToBaseAmount() {
    CoinbaseFill fill =
        new CoinbaseFill(
            "entry", "trade", "order", "2026-02-08T00:00:00Z", "FILL",
            new BigDecimal("2500"), new BigDecimal("100"), BigDecimal.ZERO, "ETH-USD",
            "MAKER", true, "user", "BUY", "portfolio");

    CoinbaseUserTrade adapted = (CoinbaseUserTrade) CoinbaseAdapters.adaptFill(fill);
    assertEquals(new BigDecimal("0.04"), adapted.getOriginalAmount());
    assertEquals(Currency.USD, adapted.getFeeCurrency());
    assertEquals("trade", adapted.getId());
    assertEquals("entry", adapted.getEntryId());
  }

  @Test
  public void testAdaptFillRetainsCatalogResolvedFuturesInstrument() {
    CoinbaseFill fill =
        new CoinbaseFill(
            "entry", "trade", "order", "2026-02-08T00:00:00Z", "FILL",
            new BigDecimal("2500"), BigDecimal.ONE, BigDecimal.ZERO,
            "ETP-20DEC30-CDE", "MAKER", false, "user", "BUY", "portfolio");
    FuturesContract configured = new FuturesContract(CurrencyPair.ETH_USD, "CDE");

    assertEquals(configured, CoinbaseAdapters.adaptFill(fill, configured).getInstrument());
  }
  @Test
  public void testFuturesFillFeeCurrencyUsesSettlementConvention() {
    CoinbaseFill cdeFill =
        new CoinbaseFill(
            "entry", "trade", "order", "2026-02-08T00:00:00Z", "FILL",
            new BigDecimal("2500"), BigDecimal.ONE, new BigDecimal("0.15"),
            "ETP-20DEC30-CDE", "MAKER", false, "user", "BUY", "portfolio");
    CoinbaseFill datedFutureFill =
        new CoinbaseFill(
            "entry", "trade", "order", "2026-02-08T00:00:00Z", "FILL",
            new BigDecimal("2500"), BigDecimal.ONE, new BigDecimal("0.15"),
            "BTC-USD-240628", "MAKER", false, "user", "BUY", "portfolio");

    assertEquals(Currency.USD, cdeFill.getFeeCurrency());
    assertEquals(Currency.USD, datedFutureFill.getFeeCurrency());
  }

  @Test
  public void testAdaptFilledQuoteSizedOrderUsesAuthoritativeFilledSize() throws Exception {
    CoinbaseOrderDetail detail =
        new ObjectMapper().readValue(
            "{\"order_id\":\"order\",\"side\":\"BUY\",\"product_id\":\"ETH-USD\","
                + "\"status\":\"FILLED\",\"order_type\":\"LIMIT\","
                + "\"order_configuration\":{\"limit_limit_fok\":"
                + "{\"quote_size\":\"100\",\"limit_price\":\"2500\"}},"
                + "\"average_filled_price\":\"2500\",\"filled_size\":\"0.039\","
                + "\"size_in_quote\":true}",
            CoinbaseOrderDetail.class);

    assertEquals(
        0,
        new BigDecimal("0.039")
            .compareTo(CoinbaseAdapters.adaptOrder(detail).getOriginalAmount()));
  }
  @Test
  public void testFilledMarketOrderRetainsExecutionState() throws Exception {
    CoinbaseOrderDetail detail =
        new ObjectMapper().readValue(
            "{\"order_id\":\"market\",\"side\":\"BUY\",\"product_id\":\"ETH-USD\","
                + "\"status\":\"FILLED\",\"order_type\":\"MARKET\","
                + "\"order_configuration\":{\"market_market_ioc\":{\"base_size\":\"1\"}},"
                + "\"average_filled_price\":\"2500\",\"filled_size\":\"1\","
                + "\"total_fees\":\"0.25\"}",
            CoinbaseOrderDetail.class);

    Order adapted = CoinbaseAdapters.adaptOrder(detail);

    assertEquals(Order.OrderStatus.FILLED, adapted.getStatus());
    assertEquals(new BigDecimal("2500"), adapted.getAveragePrice());
    assertEquals(BigDecimal.ONE, adapted.getCumulativeAmount());
    assertEquals(new BigDecimal("0.25"), adapted.getFee());
  }

  @Test
  public void testFailedOrderIsTerminalAndExcludedFromOpenOrders() throws Exception {
    CoinbaseOrderDetail detail =
        new ObjectMapper().readValue(
            "{\"order_id\":\"failed\",\"side\":\"BUY\",\"product_id\":\"ETH-USD\","
                + "\"status\":\"FAILED\",\"order_type\":\"LIMIT\","
                + "\"order_configuration\":{\"limit_limit_gtc\":"
                + "{\"base_size\":\"1\",\"limit_price\":\"2500\"}}}",
            CoinbaseOrderDetail.class);

    assertEquals(Order.OrderStatus.REJECTED, CoinbaseAdapters.adaptOrder(detail).getStatus());
    assertTrue(CoinbaseAdapters.adaptOpenOrders(Collections.singletonList(detail))
        .getAllOpenOrders().isEmpty());
  }

  @Test
  public void testRejectFilledQuoteSizedOrderWithoutFilledSize() throws Exception {
    CoinbaseOrderDetail detail =
        new ObjectMapper().readValue(
            "{\"order_id\":\"order\",\"side\":\"BUY\",\"product_id\":\"ETH-USD\","
                + "\"status\":\"FILLED\",\"order_type\":\"LIMIT\","
                + "\"order_configuration\":{\"limit_limit_fok\":"
                + "{\"quote_size\":\"100\",\"limit_price\":\"2500\"}},"
                + "\"average_filled_price\":\"2500\",\"size_in_quote\":true}",
            CoinbaseOrderDetail.class);

    assertUnavailable(() -> CoinbaseAdapters.adaptOrder(detail));
  }

  @Test
  public void testAdaptStopLimitOrdersPreservesTriggerSemantics() throws Exception {
    CoinbaseOrderDetail buyStopLoss =
        new ObjectMapper().readValue(
            "{\"order_id\":\"buy-stop\",\"side\":\"BUY\",\"product_id\":\"ETH-USD\","
                + "\"status\":\"OPEN\",\"order_type\":\"STOP_LIMIT\","
                + "\"order_configuration\":{\"stop_limit_stop_limit_gtc\":"
                + "{\"base_size\":\"2\",\"limit_price\":\"2600\",\"stop_price\":\"2550\","
                + "\"stop_direction\":\"STOP_DIRECTION_STOP_UP\"}}}",
            CoinbaseOrderDetail.class);
    CoinbaseOrderDetail sellTakeProfit =
        new ObjectMapper().readValue(
            "{\"order_id\":\"sell-stop\",\"side\":\"SELL\",\"product_id\":\"ETH-USD\","
                + "\"status\":\"OPEN\",\"order_type\":\"STOP_LIMIT\","
                + "\"order_configuration\":{\"stop_limit_stop_limit_gtd\":"
                + "{\"base_size\":\"3\",\"limit_price\":\"2450\",\"stop_price\":\"2500\","
                + "\"end_time\":\"2026-12-20T00:00:00Z\","
                + "\"stop_direction\":\"STOP_DIRECTION_STOP_UP\"}}}",
            CoinbaseOrderDetail.class);
    CoinbaseOrderDetail missingDirection =
        new ObjectMapper().readValue(
            "{\"order_id\":\"missing-direction\",\"side\":\"BUY\",\"product_id\":\"ETH-USD\","
                + "\"status\":\"OPEN\",\"order_type\":\"STOP_LIMIT\","
                + "\"order_configuration\":{\"stop_limit_stop_limit_gtc\":"
                + "{\"base_size\":\"1\",\"limit_price\":\"2600\",\"stop_price\":\"2550\"}}}",
            CoinbaseOrderDetail.class);

    StopOrder adaptedBuy = (StopOrder) CoinbaseAdapters.adaptOrder(buyStopLoss);
    StopOrder adaptedSell = (StopOrder) CoinbaseAdapters.adaptOrder(sellTakeProfit);

    assertEquals(new BigDecimal("2550"), adaptedBuy.getStopPrice());
    assertEquals(new BigDecimal("2600"), adaptedBuy.getLimitPrice());
    assertEquals(StopOrder.Intention.STOP_LOSS, adaptedBuy.getIntention());
    assertEquals(new BigDecimal("2500"), adaptedSell.getStopPrice());
    assertEquals(new BigDecimal("2450"), adaptedSell.getLimitPrice());
    assertEquals(StopOrder.Intention.TAKE_PROFIT, adaptedSell.getIntention());
    assertEquals(
        Arrays.asList(adaptedBuy, adaptedSell),
        CoinbaseAdapters.adaptOpenOrders(Arrays.asList(buyStopLoss, sellTakeProfit))
            .getHiddenOrders());
    assertUnavailable(() -> CoinbaseAdapters.adaptOrder(missingDirection));
  }

  @Test
  public void testOpaqueCdeOpenOrderFailsBeforeStatusFiltering() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    CoinbaseOrderDetail cde =
        mapper.readValue(
            "{\"order_id\":\"cde\",\"side\":\"BUY\",\"product_id\":\"ETP-20DEC30-CDE\","
                + "\"status\":\"CANCELLED\",\"order_type\":\"LIMIT\","
                + "\"order_configuration\":{\"limit_limit_fok\":"
                + "{\"base_size\":\"1\",\"limit_price\":\"2500\"}}}",
            CoinbaseOrderDetail.class);
    CoinbaseOrderDetail spot =
        mapper.readValue(
            "{\"order_id\":\"spot\",\"side\":\"BUY\",\"product_id\":\"ETH-USD\","
                + "\"status\":\"CANCELLED\",\"order_type\":\"LIMIT\","
                + "\"order_configuration\":{\"limit_limit_fok\":"
                + "{\"base_size\":\"1\",\"limit_price\":\"2500\"}}}",
            CoinbaseOrderDetail.class);

    assertUnavailable(() -> CoinbaseAdapters.adaptOpenOrders(Collections.singletonList(cde)));
    assertTrue(
        CoinbaseAdapters.adaptOpenOrders(Collections.singletonList(spot))
            .getOpenOrders()
            .isEmpty());
  }

  @Test
  public void testCatalogResolvedCdeOrderRetainsFuturesContract() throws Exception {
    CoinbaseOrderDetail cde =
        new ObjectMapper()
            .readValue(
                "{\"order_id\":\"cde\",\"side\":\"BUY\",\"product_id\":\"ETP-20DEC30-CDE\","
                    + "\"status\":\"FILLED\",\"order_type\":\"LIMIT\",\"order_configuration\":{"
                    + "\"limit_limit_fok\":{\"base_size\":\"2\",\"limit_price\":\"2500\"}}}",
                CoinbaseOrderDetail.class);
    FuturesContract configured = new FuturesContract(CurrencyPair.ETH_USD, "CDE");

    assertEquals(configured, CoinbaseAdapters.adaptOrder(cde, configured).getInstrument());
  }

  @Test
  public void testAdvancedLimitConfigurationsFailClosed() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    String[] unsupportedConfigurations = {
      "twap_limit_gtd", "trigger_bracket_gtc", "trigger_bracket_gtd", "scaled_limit_gtc"
    };
    for (String configuration : unsupportedConfigurations) {
      CoinbaseOrderDetail detail =
          mapper.readValue(
              "{\"order_id\":\"" + configuration
                  + "\",\"side\":\"BUY\",\"product_id\":\"ETH-USD\","
                  + "\"status\":\"OPEN\",\"order_type\":\"LIMIT\",\"order_configuration\":{\""
                  + configuration
                  + "\":{\"base_size\":\"2\",\"limit_price\":\"2500\","
                  + "\"min_price\":\"2400\",\"max_price\":\"2600\"}}}",
              CoinbaseOrderDetail.class);

      assertUnavailable(
          () -> CoinbaseAdapters.adaptOpenOrders(Collections.singletonList(detail)));
    }
  }

  @Test
  public void testOpenMarketOrdersRemainVisibleThroughHiddenOrders() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    CoinbaseOrderDetail market =
        mapper.readValue(
            "{\"order_id\":\"market\",\"side\":\"BUY\",\"product_id\":\"ETH-USD\","
                + "\"status\":\"PENDING\",\"order_type\":\"MARKET\","
                + "\"order_configuration\":{\"market_market_ioc\":{\"base_size\":\"1\"}}}",
            CoinbaseOrderDetail.class);
    CoinbaseOrderDetail unknownSide =
        mapper.readValue(
            "{\"order_id\":\"unknown-side\",\"side\":\"UNKNOWN\",\"product_id\":\"ETH-USD\","
                + "\"status\":\"OPEN\",\"order_type\":\"LIMIT\","
                + "\"order_configuration\":{\"limit_limit_gtc\":"
                + "{\"base_size\":\"1\",\"limit_price\":\"2500\"}}}",
            CoinbaseOrderDetail.class);

    assertEquals(
        Collections.singletonList("market"),
        CoinbaseAdapters.adaptOpenOrders(Collections.singletonList(market))
            .getHiddenOrders()
            .stream()
            .map(Order::getId)
            .collect(java.util.stream.Collectors.toList()));
    assertUnavailable(
        () -> CoinbaseAdapters.adaptOpenOrders(Collections.singletonList(unknownSide)));
  }

  @Test
  public void testQueuedOrderStatesRemainVisibleAndUnknownStatusFailsClosed() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    for (String status : Arrays.asList("QUEUED", "CANCEL_QUEUED", "EDIT_QUEUED")) {
      CoinbaseOrderDetail queued =
          mapper.readValue(
              "{\"order_id\":\"" + status + "\",\"side\":\"BUY\","
                  + "\"product_id\":\"ETH-USD\",\"status\":\"" + status + "\","
                  + "\"order_type\":\"LIMIT\",\"order_configuration\":{\"limit_limit_gtc\":"
                  + "{\"base_size\":\"1\",\"limit_price\":\"2500\"}}}",
              CoinbaseOrderDetail.class);
      assertEquals(
          status,
          CoinbaseAdapters.adaptOpenOrders(Collections.singletonList(queued))
              .getOpenOrders()
              .get(0)
              .getId());
    }

    CoinbaseOrderDetail unknown =
        mapper.readValue(
            "{\"order_id\":\"unknown-status\",\"side\":\"BUY\",\"product_id\":\"ETH-USD\","
                + "\"status\":\"AWAITING_PROVIDER\",\"order_type\":\"LIMIT\","
                + "\"order_configuration\":{\"limit_limit_gtc\":"
                + "{\"base_size\":\"1\",\"limit_price\":\"2500\"}}}",
            CoinbaseOrderDetail.class);
    assertUnavailable(
        () -> CoinbaseAdapters.adaptOpenOrders(Collections.singletonList(unknown)));
  }

  @Test
  public void testRejectFillWithoutPositiveQuantityOrPrice() {
    CoinbaseFill invalidPrice =
        new CoinbaseFill(
            "entry", "trade", "order", "2026-02-08T00:00:00Z", "FILL",
            BigDecimal.ZERO, new BigDecimal("100"), BigDecimal.ZERO, "ETH-USD",
            "MAKER", true, "user", "BUY", "portfolio");
    CoinbaseFill invalidQuantity =
        new CoinbaseFill(
            "entry", "trade", "order", "2026-02-08T00:00:00Z", "FILL",
            new BigDecimal("2500"), BigDecimal.ZERO, BigDecimal.ZERO, "ETH-USD",
            "MAKER", false, "user", "BUY", "portfolio");

    assertUnavailable(() -> CoinbaseAdapters.adaptFill(invalidPrice));
    assertUnavailable(() -> CoinbaseAdapters.adaptFill(invalidQuantity));
  }

  @Test
  public void testRejectFillWithMissingRequiredFieldsExplicitly() {
    CoinbaseFill missingEntry =
        new CoinbaseFill(
            null, "trade", "order", "2026-02-08T00:00:00Z", "FILL",
            BigDecimal.ONE, new BigDecimal("2500"), BigDecimal.ZERO, "ETH-USD",
            "MAKER", false, "user", "BUY", "portfolio");
    CoinbaseFill missingTrade =
        new CoinbaseFill(
            "entry", null, "order", "2026-02-08T00:00:00Z", "FILL",
            BigDecimal.ONE, new BigDecimal("2500"), BigDecimal.ZERO, "ETH-USD",
            "MAKER", false, "user", "BUY", "portfolio");
    CoinbaseFill missingOrder =
        new CoinbaseFill(
            "entry", "trade", null, "2026-02-08T00:00:00Z", "FILL",
            BigDecimal.ONE, new BigDecimal("2500"), BigDecimal.ZERO, "ETH-USD",
            "MAKER", false, "user", "BUY", "portfolio");
    CoinbaseFill missingSide =
        new CoinbaseFill(
            "entry", "trade", "order", "2026-02-08T00:00:00Z", "FILL",
            BigDecimal.ONE, new BigDecimal("2500"), BigDecimal.ZERO, "ETH-USD",
            "MAKER", false, "user", null, "portfolio");
    CoinbaseFill missingProduct =
        new CoinbaseFill(
            "entry", "trade", "order", "2026-02-08T00:00:00Z", "FILL",
            BigDecimal.ONE, new BigDecimal("2500"), BigDecimal.ZERO, null,
            "MAKER", false, "user", "BUY", "portfolio");

    assertUnavailable(() -> CoinbaseAdapters.adaptFill(missingEntry));
    assertUnavailable(() -> CoinbaseAdapters.adaptFill(missingTrade));
    assertUnavailable(() -> CoinbaseAdapters.adaptFill(missingOrder));
    assertUnavailable(() -> CoinbaseAdapters.adaptFill(missingSide));
    assertUnavailable(() -> CoinbaseAdapters.adaptFill(missingProduct));
  }

  @Test
  public void testCurrentMarginContractsArePubliclyConstructible() {
    CoinbaseCurrentMarginWindowResponse marginWindow =
        new CoinbaseCurrentMarginWindowResponse(
            new CoinbaseCurrentMarginWindowResponse.MarginWindow(
                "INTRADAY", "2026-12-20T00:00:00Z"),
            true,
            false);
    CoinbaseMarginWindowMeasure measure =
        CoinbaseMarginWindowMeasure.fromCurrentSchema(
            "INTRADAY",
            "BASE",
            BigDecimal.ONE,
            BigDecimal.ONE,
            "0.10",
            BigDecimal.ZERO,
            BigDecimal.TEN);
    assertEquals("INTRADAY", marginWindow.getMarginWindowType());
    assertEquals("2026-12-20T00:00:00Z", marginWindow.getEndTime());
    assertEquals(new BigDecimal("0.10"), measure.getLiquidationBuffer());
  }

  @Test
  public void testLegacyPublicContractsRemainAvailable() throws Exception {
    CoinbaseMarginWindowMeasure margin =
        legacyConstructor(
            CoinbaseMarginWindowMeasure.class,
            new Class<?>[] {
              String.class,
              String.class,
              BigDecimal.class,
              BigDecimal.class,
              BigDecimal.class,
              BigDecimal.class,
              BigDecimal.class
            },
            "INTRADAY",
            "BASE",
            BigDecimal.ONE,
            BigDecimal.ONE,
            new BigDecimal("0.10"),
            BigDecimal.ZERO,
            BigDecimal.TEN);
    CoinbaseMarginWindowMeasure absentLegacyPercentage =
        legacyConstructor(
            CoinbaseMarginWindowMeasure.class,
            new Class<?>[] {
              String.class,
              String.class,
              BigDecimal.class,
              BigDecimal.class,
              BigDecimal.class,
              BigDecimal.class,
              BigDecimal.class
            },
            "INTRADAY",
            "BASE",
            BigDecimal.ONE,
            BigDecimal.ONE,
            null,
            BigDecimal.ZERO,
            BigDecimal.TEN);
    CoinbaseFuturesPosition position =
        legacyFuturesPosition(
            "BTC-USD-PERP",
            "1",
            "LONG",
            BigDecimal.ONE,
            BigDecimal.TEN,
            BigDecimal.TEN,
            BigDecimal.ZERO,
            "2026-12-20T00:00:00Z",
            BigDecimal.ONE,
            BigDecimal.ZERO,
            BigDecimal.TEN);
    CoinbaseFuturesBalanceSummaryResponse balance =
        legacyConstructor(
            CoinbaseFuturesBalanceSummaryResponse.class,
            new Class<?>[] {
              BigDecimal.class,
              BigDecimal.class,
              BigDecimal.class,
              BigDecimal.class,
              BigDecimal.class,
              BigDecimal.class,
              BigDecimal.class,
              BigDecimal.class,
              BigDecimal.class,
              BigDecimal.class,
              BigDecimal.class,
              BigDecimal.class,
              CoinbaseMarginWindowMeasure.class,
              CoinbaseMarginWindowMeasure.class,
              List.class
            },
            BigDecimal.TEN,
            BigDecimal.TEN,
            BigDecimal.ZERO,
            BigDecimal.TEN,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ONE,
            BigDecimal.TEN,
            BigDecimal.ONE,
            BigDecimal.TEN,
            new BigDecimal("0.10"),
            margin,
            margin,
            Collections.singletonList(position));
    CoinbaseFill fill =
        legacyConstructor(
            CoinbaseFill.class,
            new Class<?>[] {
              String.class,
              String.class,
              String.class,
              String.class,
              String.class,
              BigDecimal.class,
              BigDecimal.class,
              BigDecimal.class,
              String.class,
              String.class,
              boolean.class,
              String.class,
              String.class,
              String.class
            },
            "entry",
            "trade",
            "order",
            "2026-02-08T00:00:00Z",
            "FILL",
            BigDecimal.TEN,
            BigDecimal.ONE,
            BigDecimal.ZERO,
            "BTC-USD",
            "MAKER",
            false,
            "user",
            "BUY",
            "portfolio");
    CoinbaseTransactionSummaryResponse transaction =
        legacyConstructor(
            CoinbaseTransactionSummaryResponse.class,
            new Class<?>[] {BigDecimal.class, BigDecimal.class, CoinbaseFeeTier.class},
            BigDecimal.TEN,
            BigDecimal.ONE,
            new CoinbaseFeeTier(BigDecimal.ONE, BigDecimal.ZERO));

    assertEquals(new BigDecimal("0.10"), invokeLegacyMethod(margin, "getLiquidationBufferPercentage"));
    assertNull(invokeLegacyMethod(absentLegacyPercentage, "getLiquidationBufferPercentage"));
    assertEquals(
        "CoinbaseMarginWindowMeasure [marginWindowType=INTRADAY, marginLevel=BASE]",
        margin.toString());
    assertEquals("1", position.getContractSize());
    assertEquals(BigDecimal.TEN, invokeLegacyMethod(balance, "getTotalUsdBalance"));
    assertEquals(
        position, ((List<?>) invokeLegacyMethod(balance, "getExpiringFutures")).get(0));
    assertNull(fill.getSequenceTimestamp());
    assertEquals(BigDecimal.TEN, transaction.getTotalVolume());
    Method listFills =
        CoinbaseAuthenticated.class.getMethod(
            "listFills",
            ParamsDigest.class,
            List.class,
            List.class,
            List.class,
            String.class,
            String.class,
            String.class,
            Integer.class,
            String.class,
            String.class);
    Method bestBidAsk =
        CoinbaseAuthenticated.class.getMethod(
            "getBestBidAsk", ParamsDigest.class, String.class);
    assertEquals(CoinbaseOrdersResponse.class, listFills.getReturnType());
    assertEquals(CoinbaseBestBidAsksResponse.class, bestBidAsk.getReturnType());
    Method cancelOrders =
        CoinbaseAuthenticated.class.getMethod("cancelOrders", ParamsDigest.class, Object.class);
    Method batchCancelOrders =
        CoinbaseAuthenticated.class.getMethod(
            "batchCancelOrders", ParamsDigest.class, Object.class);
    Method editOrder =
        CoinbaseAuthenticated.class.getMethod(
            "editOrder",
            ParamsDigest.class,
            org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseEditOrderRequest.class);
    Method previewOrder =
        CoinbaseAuthenticated.class.getMethod(
            "previewOrder",
            ParamsDigest.class,
            org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseOrderRequest.class);
    Method previewEditOrder =
        CoinbaseAuthenticated.class.getMethod(
            "previewEditOrder",
            ParamsDigest.class,
            org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseEditOrderRequest.class);
    Method rawCancelOrders =
        org.knowm.xchange.coinbase.v3.service.CoinbaseTradeServiceRaw.class.getMethod(
            "cancelOrders", List.class, List.class);
    Method rawEditOrder =
        org.knowm.xchange.coinbase.v3.service.CoinbaseTradeServiceRaw.class.getMethod(
            "editOrder",
            org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseEditOrderRequest.class);
    Method rawPreviewOrder =
        org.knowm.xchange.coinbase.v3.service.CoinbaseTradeServiceRaw.class.getMethod(
            "previewOrder",
            org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseOrderRequest.class);
    Method rawPreviewEditOrder =
        org.knowm.xchange.coinbase.v3.service.CoinbaseTradeServiceRaw.class.getMethod(
            "previewEditOrder",
            org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseEditOrderRequest.class);
    Method rawCancelOrderById =
        org.knowm.xchange.coinbase.v3.service.CoinbaseTradeServiceRaw.class.getMethod(
            "cancelOrderById", String.class);
    assertEquals(CoinbaseOrdersResponse.class, cancelOrders.getReturnType());
    assertEquals(CoinbaseCancelOrdersResponse.class, batchCancelOrders.getReturnType());
    assertEquals(CoinbaseOrdersResponse.class, rawCancelOrders.getReturnType());
    assertEquals(CoinbaseOrdersResponse.class, editOrder.getReturnType());
    assertEquals(CoinbaseOrdersResponse.class, previewOrder.getReturnType());
    assertEquals(CoinbaseOrdersResponse.class, previewEditOrder.getReturnType());
    assertEquals(CoinbaseOrdersResponse.class, rawEditOrder.getReturnType());
    assertEquals(CoinbaseOrdersResponse.class, rawPreviewOrder.getReturnType());
    assertEquals(CoinbaseOrdersResponse.class, rawPreviewEditOrder.getReturnType());
    assertEquals(CoinbaseOrdersResponse.class, rawCancelOrderById.getReturnType());
    assertNotNull(rawCancelOrders.getAnnotation(Deprecated.class));
    assertEquals("orders/batch_cancel", cancelOrders.getAnnotation(Path.class).value());
    assertEquals("orders/batch_cancel", batchCancelOrders.getAnnotation(Path.class).value());
    assertEquals("orders/edit", editOrder.getAnnotation(Path.class).value());
  }

  private static CoinbaseFuturesPosition legacyFuturesPosition(
      String productId,
      String contractSize,
      String side,
      BigDecimal amount,
      BigDecimal avgEntryPrice,
      BigDecimal currentPrice,
      BigDecimal unrealizedPnl,
      String expiryTime,
      BigDecimal numberOfContracts,
      BigDecimal realizedPnl,
      BigDecimal entryPrice)
      throws ReflectiveOperationException {
    return legacyConstructor(
        CoinbaseFuturesPosition.class,
        new Class<?>[] {
          String.class,
          String.class,
          String.class,
          BigDecimal.class,
          BigDecimal.class,
          BigDecimal.class,
          BigDecimal.class,
          String.class,
          BigDecimal.class,
          BigDecimal.class,
          BigDecimal.class
        },
        productId,
        contractSize,
        side,
        amount,
        avgEntryPrice,
        currentPrice,
        unrealizedPnl,
        expiryTime,
        numberOfContracts,
        realizedPnl,
        entryPrice);
  }

  private static <T> T legacyConstructor(
      Class<T> type, Class<?>[] parameterTypes, Object... arguments)
      throws ReflectiveOperationException {
    return type.getConstructor(parameterTypes).newInstance(arguments);
  }

  private static Object invokeLegacyMethod(Object target, String methodName)
      throws ReflectiveOperationException {
    return target.getClass().getMethod(methodName).invoke(target);
  }
  private static void assertUnavailable(Runnable action) {
    try {
      action.run();
      fail("Expected opaque or unadaptable Coinbase state to fail explicitly");
    } catch (RuntimeException expected) {
      if (!(expected instanceof NotAvailableFromExchangeException)
          && !(expected instanceof ExchangeException)) {
        throw expected;
      }
    }
  }

  private static void assertAmount(CoinbaseAmount amount, String expectedValue) {
    assertEquals("USD", amount.getCurrency());
    assertEquals(new BigDecimal(expectedValue), amount.getValue());
  }
}
