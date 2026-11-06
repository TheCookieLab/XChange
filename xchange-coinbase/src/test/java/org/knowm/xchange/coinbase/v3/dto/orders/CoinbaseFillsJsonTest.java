package org.knowm.xchange.coinbase.v3.dto.orders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;
import org.knowm.xchange.currency.Currency;
import org.junit.jupiter.api.Test;

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

    assertNotNull(response, "Response should not be null");
    assertNotNull(response.getFills(), "Fills should not be null");
    assertEquals(2, response.getFills().size(), "Should have 2 fills");
    assertEquals("next-cursor-abc", response.getCursor(), "Cursor should match");
    
    // Verify first fill (BTC buy)
    CoinbaseFill btcFill = response.getFills().get(0);
    assertEquals("fill-entry-123", btcFill.getEntryId(), "Entry ID should match");
    assertEquals("trade-456", btcFill.getTradeId(), "Trade ID should match");
    assertEquals("order-789", btcFill.getOrderId(), "Order ID should match");
    assertEquals("BTC-USD", btcFill.getProductId(), "Product ID should match");
    assertEquals(new BigDecimal("50000.00"), btcFill.getPrice(), "Price should match");
    assertEquals(new BigDecimal("0.1"), btcFill.getSize(), "Size should match");
    assertEquals(new BigDecimal("2.5"), btcFill.getCommission(), "Commission should match");
    assertEquals("BUY", btcFill.getSide(), "Side should match");
    assertEquals(Instant.parse("2025-10-13T00:00:00Z"), btcFill.getSequenceTimestamp().toInstant(), "Sequence timestamp should match");
    assertEquals("TAKER", btcFill.getLiquidityIndicator(), "Liquidity indicator should match");
    
    // Verify second fill (ETH sell)
    CoinbaseFill ethFill = response.getFills().get(1);
    assertEquals("fill-entry-124", ethFill.getEntryId(), "Entry ID should match");
    assertEquals("trade-457", ethFill.getTradeId(), "Trade ID should match");
    assertEquals("order-790", ethFill.getOrderId(), "Order ID should match");
    assertEquals("ETH-USD", ethFill.getProductId(), "Product ID should match");
    assertEquals(new BigDecimal("3000.00"), ethFill.getPrice(), "Price should match");
    assertEquals(new BigDecimal("1.0"), ethFill.getSize(), "Size should match");
    assertEquals(new BigDecimal("1.5"), ethFill.getCommission(), "Commission should match");
    assertEquals("SELL", ethFill.getSide(), "Side should match");
    assertEquals("MAKER", ethFill.getLiquidityIndicator(), "Liquidity indicator should match");
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

    assertNotNull(fill, "Fill should not be null");
    assertEquals("test-entry", fill.getEntryId(), "Entry ID should match");
    assertEquals("test-trade", fill.getTradeId(), "Trade ID should match");
    assertEquals("test-order", fill.getOrderId(), "Order ID should match");
    assertEquals("BTC-USD", fill.getProductId(), "Product ID should match");
    assertEquals("BUY", fill.getSide(), "Side should match");
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

    assertNotNull(fill, "Fill should not be null");
    assertEquals(new BigDecimal("50000.123456789"), fill.getPrice(), "Price precision should be preserved");
    assertEquals(new BigDecimal("0.000000001"), fill.getSize(), "Size precision should be preserved");
    assertEquals(new BigDecimal("0.00000025"), fill.getCommission(), "Commission precision should be preserved");
  }

  @Test
  public void testDatedCfmfFillUsesUsdFeeCurrency() {
    CoinbaseFill fill =
        new CoinbaseFill(
            "entry",
            "trade",
            "order",
            null,
            null,
            null,
            null,
            null,
            "BTC-28MAR25-CFMF",
            null,
            null,
            false,
            null,
            null,
            null);

    assertEquals(Currency.USD, fill.getFeeCurrency(), "CFMF futures fees settle in USD rather than the expiry token");
  }

}

