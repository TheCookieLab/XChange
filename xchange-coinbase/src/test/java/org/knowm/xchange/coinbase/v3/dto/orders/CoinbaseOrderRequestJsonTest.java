package org.knowm.xchange.coinbase.v3.dto.orders;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.Test;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.dto.trade.StopOrder;

public class CoinbaseOrderRequestJsonTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  public void testMarketOrderRequestBidSerialization() {
    MarketOrder order = new MarketOrder.Builder(Order.OrderType.BID, CurrencyPair.BTC_USD)
        .originalAmount(new BigDecimal("1.23"))
        .userReference("client-1")
        .build();

    CoinbaseOrderRequest request = CoinbaseV3OrderRequests.marketOrderRequest(order);
    JsonNode node = mapper.valueToTree(request);

    assertEquals("client-1", node.get("client_order_id").asText());
    assertEquals("BTC-USD", node.get("product_id").asText());
    assertEquals("BUY", node.get("side").asText());

    JsonNode config = node.get("order_configuration").get("market_market_ioc");
    assertNotNull(config);
    assertEquals("1.23", config.get("quote_size").asText());
    assertFalse(config.has("base_size"));
  }

  @Test
  public void testMarketOrderRequestAskSerialization() {
    MarketOrder order = new MarketOrder.Builder(Order.OrderType.ASK, CurrencyPair.BTC_USD)
        .originalAmount(new BigDecimal("0.75"))
        .userReference("client-2")
        .build();

    CoinbaseOrderRequest request = CoinbaseV3OrderRequests.marketOrderRequest(order);
    JsonNode node = mapper.valueToTree(request);

    assertEquals("SELL", node.get("side").asText());
    JsonNode config = node.get("order_configuration").get("market_market_ioc");
    assertEquals("0.75", config.get("base_size").asText());
    assertFalse(config.has("quote_size"));
  }

  @Test
  public void testPreviewMarketOrderRequestOmitsClientOrderId() {
    MarketOrder order = new MarketOrder.Builder(Order.OrderType.BID, CurrencyPair.BTC_USD)
        .originalAmount(new BigDecimal("2.5"))
        .userReference("client-3")
        .build();

    CoinbaseOrderRequest request = CoinbaseV3OrderRequests.previewMarketOrderRequest(order);
    JsonNode node = mapper.valueToTree(request);

    assertFalse(node.has("client_order_id"));
  }

  @Test
  public void testLimitOrderRequestSerialization() {
    LimitOrder order = new LimitOrder.Builder(Order.OrderType.BID, CurrencyPair.BTC_USD)
        .originalAmount(new BigDecimal("0.25"))
        .limitPrice(new BigDecimal("35000"))
        .userReference("client-4")
        .build();

    CoinbaseOrderRequest request = CoinbaseV3OrderRequests.limitOrderRequest(order);
    JsonNode node = mapper.valueToTree(request);

    assertEquals("BUY", node.get("side").asText());
    JsonNode config = node.get("order_configuration").get("limit_limit_gtc");
    assertEquals("0.25", config.get("base_size").asText());
    assertEquals("35000", config.get("limit_price").asText());
    assertFalse(config.get("post_only").asBoolean());
  }

  @Test
  public void testStopOrderRequestSerialization() {
    StopOrder order = new StopOrder.Builder(Order.OrderType.BID, CurrencyPair.BTC_USD)
        .originalAmount(new BigDecimal("0.5"))
        .limitPrice(new BigDecimal("29500"))
        .stopPrice(new BigDecimal("30000"))
        .userReference("client-5")
        .build();

    CoinbaseOrderRequest request = CoinbaseV3OrderRequests.stopOrderRequest(order);
    JsonNode node = mapper.valueToTree(request);

    JsonNode config = node.get("order_configuration").get("stop_limit_stop_limit_gtc");
    assertEquals("0.5", config.get("base_size").asText());
    assertEquals("29500", config.get("limit_price").asText());
    assertEquals("30000", config.get("stop_price").asText());
    assertFalse(config.has("stop_direction"));
  }

  @Test
  public void testEditLimitOrderRequestSerialization() {
    LimitOrder order = new LimitOrder.Builder(Order.OrderType.BID, CurrencyPair.BTC_USD)
        .originalAmount(new BigDecimal("1.5"))
        .limitPrice(new BigDecimal("31000"))
        .id("order-123")
        .build();

    CoinbaseEditOrderRequest request = CoinbaseV3OrderRequests.editLimitOrderRequest(order);
    JsonNode node = mapper.valueToTree(request);

    assertEquals("order-123", node.get("order_id").asText());
    assertEquals("31000", node.get("price").asText());
    assertEquals("1.5", node.get("size").asText());
  }

  @Test
  public void testOrderRequestOptionalFieldsSerialization() {
    CoinbaseOrderConfiguration primaryConfig = CoinbaseOrderConfiguration.limitLimitGtc(
        new CoinbaseLimitLimitGtc(
            null,
            new BigDecimal("1"),
            new BigDecimal("30000"),
            Boolean.TRUE));
    CoinbaseOrderConfiguration attachedConfig = CoinbaseOrderConfiguration.marketMarketIoc(
        new CoinbaseMarketMarketIoc(new BigDecimal("100"), null));
    CoinbasePredictionRequestMetadata metadata = new CoinbasePredictionRequestMetadata(
        CoinbasePredictionSide.PREDICTION_SIDE_YES);

    CoinbaseOrderRequest request = new CoinbaseOrderRequest(
        "client-opt",
        "ETH-USD",
        CoinbaseOrderSide.BUY,
        primaryConfig,
        new BigDecimal("2"),
        CoinbaseMarginType.CROSS,
        "portfolio-123",
        "preview-789",
        attachedConfig,
        CoinbaseSorPreference.SOR_ENABLED,
        metadata);

    JsonNode node = mapper.valueToTree(request);

    assertEquals("portfolio-123", node.get("retail_portfolio_id").asText());
    assertEquals("preview-789", node.get("preview_id").asText());
    assertEquals("SOR_ENABLED", node.get("sor_preference").asText());
    assertEquals("CROSS", node.get("margin_type").asText());
    assertEquals("2", node.get("leverage").asText());

    JsonNode attached = node.get("attached_order_configuration").get("market_market_ioc");
    assertEquals("100", attached.get("quote_size").asText());
    assertEquals("PREDICTION_SIDE_YES",
        node.get("prediction_metadata").get("prediction_side").asText());
  }

  @Test
  public void testClosePositionRequestSerialization() {
    CoinbaseClosePositionRequest request = new CoinbaseClosePositionRequest(
        "client-close",
        "BTC-PERP",
        new BigDecimal("0.1"));
    JsonNode node = mapper.valueToTree(request);

    assertEquals("client-close", node.get("client_order_id").asText());
    assertEquals("BTC-PERP", node.get("product_id").asText());
    assertEquals("0.1", node.get("size").asText());
  }
}
