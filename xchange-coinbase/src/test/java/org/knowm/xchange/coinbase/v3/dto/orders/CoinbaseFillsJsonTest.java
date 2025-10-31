package org.knowm.xchange.coinbase.v3.dto.orders;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import org.junit.Test;

/**
 * Unit tests for CoinbaseOrdersResponse (fills) JSON parsing.
 * Verifies correct deserialization of fill/trade data from Coinbase API responses.
 */
public class CoinbaseFillsJsonTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  public void testDeserializeFillsResponse() throws IOException {
    InputStream is = CoinbaseFillsJsonTest.class.getResourceAsStream(
        "/org/knowm/xchange/coinbase/dto/v3/orders/example-fills-response.json");
    
    CoinbaseOrdersResponse response = mapper.readValue(is, CoinbaseOrdersResponse.class);

    assertNotNull("Response should not be null", response);
    assertNotNull("Fills should not be null", response.getFills());
    assertEquals("Should have 2 fills", 2, response.getFills().size());
    assertEquals("Cursor should match", "next-cursor-abc", response.getCursor());
    
    // Verify first fill (BTC buy)
    CoinbaseFill btcFill = response.getFills().get(0);
    assertEquals("Entry ID should match", "fill-entry-123", btcFill.getEntryId());
    assertEquals("Trade ID should match", "trade-456", btcFill.getTradeId());
    assertEquals("Order ID should match", "order-789", btcFill.getOrderId());
    assertEquals("Product ID should match", "BTC-USD", btcFill.getProductId());
    assertEquals("Price should match", new BigDecimal("50000.00"), btcFill.getPrice());
    assertEquals("Size should match", new BigDecimal("0.1"), btcFill.getSize());
    assertEquals("Commission should match", new BigDecimal("2.5"), btcFill.getCommission());
    assertEquals("Side should match", "BUY", btcFill.getSide());
    assertEquals("Liquidity indicator should match", "TAKER", btcFill.getLiquidityIndicator());
    
    // Verify second fill (ETH sell)
    CoinbaseFill ethFill = response.getFills().get(1);
    assertEquals("Entry ID should match", "fill-entry-124", ethFill.getEntryId());
    assertEquals("Trade ID should match", "trade-457", ethFill.getTradeId());
    assertEquals("Order ID should match", "order-790", ethFill.getOrderId());
    assertEquals("Product ID should match", "ETH-USD", ethFill.getProductId());
    assertEquals("Price should match", new BigDecimal("3000.00"), ethFill.getPrice());
    assertEquals("Size should match", new BigDecimal("1.0"), ethFill.getSize());
    assertEquals("Commission should match", new BigDecimal("1.5"), ethFill.getCommission());
    assertEquals("Side should match", "SELL", ethFill.getSide());
    assertEquals("Liquidity indicator should match", "MAKER", ethFill.getLiquidityIndicator());
  }

  @Test
  public void testDeserializeSingleFill() throws Exception {
    String json = "{\n" +
        "  \"entry_id\": \"test-entry\",\n" +
        "  \"trade_id\": \"test-trade\",\n" +
        "  \"order_id\": \"test-order\",\n" +
        "  \"product_id\": \"BTC-USD\",\n" +
        "  \"price\": \"50000\",\n" +
        "  \"size\": \"1.0\",\n" +
        "  \"commission\": \"25\",\n" +
        "  \"side\": \"BUY\",\n" +
        "  \"trade_time\": \"2024-01-01T00:00:00Z\",\n" +
        "  \"liquidity_indicator\": \"TAKER\"\n" +
        "}";

    CoinbaseFill fill = mapper.readValue(json, CoinbaseFill.class);

    assertNotNull("Fill should not be null", fill);
    assertEquals("Entry ID should match", "test-entry", fill.getEntryId());
    assertEquals("Trade ID should match", "test-trade", fill.getTradeId());
    assertEquals("Order ID should match", "test-order", fill.getOrderId());
    assertEquals("Product ID should match", "BTC-USD", fill.getProductId());
    assertEquals("Side should match", "BUY", fill.getSide());
  }

  @Test
  public void testDeserializeFillWithHighPrecision() throws Exception {
    String json = "{\n" +
        "  \"entry_id\": \"test\",\n" +
        "  \"trade_id\": \"test\",\n" +
        "  \"order_id\": \"test\",\n" +
        "  \"product_id\": \"BTC-USD\",\n" +
        "  \"price\": \"50000.123456789\",\n" +
        "  \"size\": \"0.000000001\",\n" +
        "  \"commission\": \"0.00000025\",\n" +
        "  \"side\": \"SELL\",\n" +
        "  \"trade_time\": \"2024-01-01T00:00:00Z\"\n" +
        "}";

    CoinbaseFill fill = mapper.readValue(json, CoinbaseFill.class);

    assertNotNull("Fill should not be null", fill);
    assertEquals("Price precision should be preserved", 
        new BigDecimal("50000.123456789"), fill.getPrice());
    assertEquals("Size precision should be preserved", 
        new BigDecimal("0.000000001"), fill.getSize());
    assertEquals("Commission precision should be preserved", 
        new BigDecimal("0.00000025"), fill.getCommission());
  }
}

