package org.knowm.xchange.coinbase.v3.dto.pricebook;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import org.junit.Test;

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

    assertNotNull("Response should not be null", response);
    assertNotNull("Price books should not be null", response.getPriceBooks());
    assertEquals("Should have 1 price book", 1, response.getPriceBooks().size());
    
    CoinbasePriceBook priceBook = response.getPriceBooks().get(0);
    assertEquals("Product ID should match", "BTC-USD", priceBook.getProductId());
    assertNotNull("Bids should not be null", priceBook.getBids());
    assertNotNull("Asks should not be null", priceBook.getAsks());
    assertEquals("Should have 2 bids", 2, priceBook.getBids().size());
    assertEquals("Should have 2 asks", 2, priceBook.getAsks().size());
    
    // Verify best bid
    CoinbasePriceBookEntry bestBid = priceBook.getBids().get(0);
    assertEquals("Best bid price should match", new BigDecimal("50000.00"), 
        bestBid.getPrice());
    assertEquals("Best bid size should match", new BigDecimal("1.5"), 
        bestBid.getSize());
    
    // Verify second bid
    CoinbasePriceBookEntry secondBid = priceBook.getBids().get(1);
    assertEquals("Second bid price should match", new BigDecimal("49999.00"), 
        secondBid.getPrice());
    assertEquals("Second bid size should match", new BigDecimal("2.0"), 
        secondBid.getSize());
    
    // Verify best ask
    CoinbasePriceBookEntry bestAsk = priceBook.getAsks().get(0);
    assertEquals("Best ask price should match", new BigDecimal("50001.00"), 
        bestAsk.getPrice());
    assertEquals("Best ask size should match", new BigDecimal("1.0"), 
        bestAsk.getSize());
    
    // Verify second ask
    CoinbasePriceBookEntry secondAsk = priceBook.getAsks().get(1);
    assertEquals("Second ask price should match", new BigDecimal("50002.00"), 
        secondAsk.getPrice());
    assertEquals("Second ask size should match", new BigDecimal("1.5"), 
        secondAsk.getSize());
  }

  @Test
  public void testDeserializePriceBookEntry() throws Exception {
    String json = "{\n" +
        "  \"price\": \"50000.123\",\n" +
        "  \"size\": \"1.5\"\n" +
        "}";

    CoinbasePriceBookEntry entry = mapper.readValue(json, CoinbasePriceBookEntry.class);

    assertNotNull("Entry should not be null", entry);
    assertEquals("Price should match", new BigDecimal("50000.123"), entry.getPrice());
    assertEquals("Size should match", new BigDecimal("1.5"), entry.getSize());
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

    assertNotNull("Response should not be null", response);
    assertNotNull("Price book should not be null", response.getPriceBook());
    
    CoinbasePriceBook book = response.getPriceBook();
    assertEquals("Product ID should match", "BTC-USD", book.getProductId());
    assertEquals("Should have 1 bid", 1, book.getBids().size());
    assertEquals("Should have 1 ask", 1, book.getAsks().size());
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

    assertNotNull("Price book should not be null", priceBook);
    assertEquals("Product ID should match", "ETH-USD", priceBook.getProductId());
    assertNotNull("Bids should not be null", priceBook.getBids());
    assertNotNull("Asks should not be null", priceBook.getAsks());
    assertEquals("Bids should be empty", 0, priceBook.getBids().size());
    assertEquals("Asks should be empty", 0, priceBook.getAsks().size());
  }

  @Test
  public void testDeserializePriceBookEntryWithHighPrecision() throws Exception {
    String json = "{\n" +
        "  \"price\": \"0.000000123456\",\n" +
        "  \"size\": \"1000000.123456789\"\n" +
        "}";

    CoinbasePriceBookEntry entry = mapper.readValue(json, CoinbasePriceBookEntry.class);

    assertNotNull("Entry should not be null", entry);
    assertEquals("Price precision should be preserved", 
        new BigDecimal("0.000000123456"), entry.getPrice());
    assertEquals("Size precision should be preserved", 
        new BigDecimal("1000000.123456789"), entry.getSize());
  }
}

