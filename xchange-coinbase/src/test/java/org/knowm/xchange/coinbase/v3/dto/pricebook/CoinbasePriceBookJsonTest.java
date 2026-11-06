package org.knowm.xchange.coinbase.v3.dto.pricebook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for CoinbaseBestBidAsksResponse and CoinbasePriceBook JSON parsing.
 * Verifies correct deserialization of order book data from Coinbase API responses.
 */
public class CoinbasePriceBookJsonTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  public void testDeserializeBestBidAsksResponse() throws IOException {
    InputStream is = CoinbasePriceBookJsonTest.class.getResourceAsStream(
        "/org/knowm/xchange/coinbase/dto/v3/pricebook/example-best-bid-asks-response.json");
    
    CoinbaseBestBidAsksResponse response = mapper.readValue(is, 
        CoinbaseBestBidAsksResponse.class);

    assertNotNull(response, "Response should not be null");
    assertNotNull(response.getPriceBooks(), "Price books should not be null");
    assertEquals(1, response.getPriceBooks().size(), "Should have 1 price book");
    
    CoinbasePriceBook priceBook = response.getPriceBooks().get(0);
    assertEquals("BTC-USD", priceBook.getProductId(), "Product ID should match");
    assertNotNull(priceBook.getBids(), "Bids should not be null");
    assertNotNull(priceBook.getAsks(), "Asks should not be null");
    assertEquals(2, priceBook.getBids().size(), "Should have 2 bids");
    assertEquals(2, priceBook.getAsks().size(), "Should have 2 asks");
    
    // Verify best bid
    CoinbasePriceBookEntry bestBid = priceBook.getBids().get(0);
    assertEquals(new BigDecimal("50000.00"), bestBid.getPrice(), "Best bid price should match");
    assertEquals(new BigDecimal("1.5"), bestBid.getSize(), "Best bid size should match");
    
    // Verify second bid
    CoinbasePriceBookEntry secondBid = priceBook.getBids().get(1);
    assertEquals(new BigDecimal("49999.00"), secondBid.getPrice(), "Second bid price should match");
    assertEquals(new BigDecimal("2.0"), secondBid.getSize(), "Second bid size should match");
    
    // Verify best ask
    CoinbasePriceBookEntry bestAsk = priceBook.getAsks().get(0);
    assertEquals(new BigDecimal("50001.00"), bestAsk.getPrice(), "Best ask price should match");
    assertEquals(new BigDecimal("1.0"), bestAsk.getSize(), "Best ask size should match");
    
    // Verify second ask
    CoinbasePriceBookEntry secondAsk = priceBook.getAsks().get(1);
    assertEquals(new BigDecimal("50002.00"), secondAsk.getPrice(), "Second ask price should match");
    assertEquals(new BigDecimal("1.5"), secondAsk.getSize(), "Second ask size should match");
  }

  @Test
  public void testDeserializePriceBookEntry() throws Exception {
    String json = "{\n" +
        "  \"price\": \"50000.123\",\n" +
        "  \"size\": \"1.5\"\n" +
        "}";

    CoinbasePriceBookEntry entry = mapper.readValue(json, CoinbasePriceBookEntry.class);

    assertNotNull(entry, "Entry should not be null");
    assertEquals(new BigDecimal("50000.123"), entry.getPrice(), "Price should match");
    assertEquals(new BigDecimal("1.5"), entry.getSize(), "Size should match");
  }

  @Test
  public void testDeserializeProductPriceBookResponse() throws Exception {
    String json = "{\n" +
        "  \"pricebook\": {\n" +
        "    \"product_id\": \"BTC-USD\",\n" +
        "    \"bids\": [\n" +
        "      { \"price\": \"50000\", \"size\": \"1.0\" }\n" +
        "    ],\n" +
        "    \"asks\": [\n" +
        "      { \"price\": \"50001\", \"size\": \"0.5\" }\n" +
        "    ],\n" +
        "    \"time\": \"2024-01-01T00:00:00Z\"\n" +
        "  },\n" +
        "  \"spread\": \"1.0\",\n" +
        "  \"mid_price\": \"50000.5\",\n" +
        "  \"spread_bps\": \"20\",\n" +
        "  \"last\": \"50000.25\"\n" +
        "}";

    CoinbaseProductPriceBookResponse response = mapper.readValue(json, 
        CoinbaseProductPriceBookResponse.class);

    assertNotNull(response, "Response should not be null");
    assertNotNull(response.getPriceBook(), "Price book should not be null");
    
    CoinbasePriceBook book = response.getPriceBook();
    assertEquals("BTC-USD", book.getProductId(), "Product ID should match");
    assertEquals(1, book.getBids().size(), "Should have 1 bid");
    assertEquals(1, book.getAsks().size(), "Should have 1 ask");
  }

  @Test
  public void testDeserializePriceBookWithEmptyLevels() throws Exception {
    String json = "{\n" +
        "  \"product_id\": \"ETH-USD\",\n" +
        "  \"bids\": [],\n" +
        "  \"asks\": [],\n" +
        "  \"time\": \"2024-01-01T00:00:00Z\"\n" +
        "}";

    CoinbasePriceBook priceBook = mapper.readValue(json, CoinbasePriceBook.class);

    assertNotNull(priceBook, "Price book should not be null");
    assertEquals("ETH-USD", priceBook.getProductId(), "Product ID should match");
    assertNotNull(priceBook.getBids(), "Bids should not be null");
    assertNotNull(priceBook.getAsks(), "Asks should not be null");
    assertEquals(0, priceBook.getBids().size(), "Bids should be empty");
    assertEquals(0, priceBook.getAsks().size(), "Asks should be empty");
  }

  @Test
  public void testDeserializePriceBookEntryWithHighPrecision() throws Exception {
    String json = "{\n" +
        "  \"price\": \"0.000000123456\",\n" +
        "  \"size\": \"1000000.123456789\"\n" +
        "}";

    CoinbasePriceBookEntry entry = mapper.readValue(json, CoinbasePriceBookEntry.class);

    assertNotNull(entry, "Entry should not be null");
    assertEquals(new BigDecimal("0.000000123456"), entry.getPrice(), "Price precision should be preserved");
    assertEquals(new BigDecimal("1000000.123456789"), entry.getSize(), "Size precision should be preserved");
  }
}

