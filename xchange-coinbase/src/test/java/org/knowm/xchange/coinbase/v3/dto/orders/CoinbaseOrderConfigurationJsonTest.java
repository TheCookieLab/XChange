package org.knowm.xchange.coinbase.v3.dto.orders;

import static org.junit.Assert.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Collections;
import org.junit.Test;

public class CoinbaseOrderConfigurationJsonTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  public void testMarketMarketIocSerialization() {
    CoinbaseMarketMarketIoc config = new CoinbaseMarketMarketIoc(
        new BigDecimal("10"),
        new BigDecimal("0.5"));
    JsonNode node = mapper.valueToTree(config);

    assertEquals("10", node.get("quote_size").asText());
    assertEquals("0.5", node.get("base_size").asText());
  }

  @Test
  public void testMarketMarketFokSerialization() {
    CoinbaseMarketMarketFok config = new CoinbaseMarketMarketFok(
        new BigDecimal("20"),
        new BigDecimal("1.1"));
    JsonNode node = mapper.valueToTree(config);

    assertEquals("20", node.get("quote_size").asText());
    assertEquals("1.1", node.get("base_size").asText());
  }

  @Test
  public void testSorLimitIocSerialization() {
    CoinbaseSorLimitIoc config = new CoinbaseSorLimitIoc(
        new BigDecimal("5"),
        new BigDecimal("0.25"),
        new BigDecimal("30000"));
    JsonNode node = mapper.valueToTree(config);

    assertEquals("5", node.get("quote_size").asText());
    assertEquals("0.25", node.get("base_size").asText());
    assertEquals("30000", node.get("limit_price").asText());
  }

  @Test
  public void testLimitLimitGtcSerialization() {
    CoinbaseLimitLimitGtc config = new CoinbaseLimitLimitGtc(
        new BigDecimal("100"),
        new BigDecimal("1.25"),
        new BigDecimal("25000"),
        Boolean.TRUE);
    JsonNode node = mapper.valueToTree(config);

    assertEquals("100", node.get("quote_size").asText());
    assertEquals("1.25", node.get("base_size").asText());
    assertEquals("25000", node.get("limit_price").asText());
    assertEquals(true, node.get("post_only").asBoolean());
  }

  @Test
  public void testLimitLimitGtdSerialization() {
    CoinbaseLimitLimitGtd config = new CoinbaseLimitLimitGtd(
        new BigDecimal("75"),
        new BigDecimal("0.5"),
        new BigDecimal("28000"),
        "2024-01-01T00:00:00Z",
        Boolean.FALSE);
    JsonNode node = mapper.valueToTree(config);

    assertEquals("75", node.get("quote_size").asText());
    assertEquals("0.5", node.get("base_size").asText());
    assertEquals("28000", node.get("limit_price").asText());
    assertEquals("2024-01-01T00:00:00Z", node.get("end_time").asText());
    assertEquals(false, node.get("post_only").asBoolean());
  }

  @Test
  public void testLimitLimitFokSerialization() {
    CoinbaseLimitLimitFok config = new CoinbaseLimitLimitFok(
        new BigDecimal("60"),
        new BigDecimal("0.3"),
        new BigDecimal("26000"));
    JsonNode node = mapper.valueToTree(config);

    assertEquals("60", node.get("quote_size").asText());
    assertEquals("0.3", node.get("base_size").asText());
    assertEquals("26000", node.get("limit_price").asText());
  }

  @Test
  public void testTwapLimitGtdSerialization() {
    CoinbaseTwapLimitGtd config = new CoinbaseTwapLimitGtd(
        new BigDecimal("200"),
        new BigDecimal("2"),
        "2024-01-01T00:00:00Z",
        "2024-01-01T00:10:00Z",
        new BigDecimal("24000"),
        "5",
        new BigDecimal("0.4"),
        "120");
    JsonNode node = mapper.valueToTree(config);

    assertEquals("200", node.get("quote_size").asText());
    assertEquals("2", node.get("base_size").asText());
    assertEquals("2024-01-01T00:00:00Z", node.get("start_time").asText());
    assertEquals("2024-01-01T00:10:00Z", node.get("end_time").asText());
    assertEquals("24000", node.get("limit_price").asText());
    assertEquals("5", node.get("number_buckets").asText());
    assertEquals("0.4", node.get("bucket_size").asText());
    assertEquals("120", node.get("bucket_duration").asText());
  }

  @Test
  public void testStopLimitStopLimitGtcSerialization() {
    CoinbaseStopLimitStopLimitGtc config = new CoinbaseStopLimitStopLimitGtc(
        new BigDecimal("0.8"),
        new BigDecimal("31000"),
        new BigDecimal("31500"),
        CoinbaseStopPriceDirection.STOP_DIRECTION_STOP_UP);
    JsonNode node = mapper.valueToTree(config);

    assertEquals("0.8", node.get("base_size").asText());
    assertEquals("31000", node.get("limit_price").asText());
    assertEquals("31500", node.get("stop_price").asText());
    assertEquals("STOP_DIRECTION_STOP_UP", node.get("stop_direction").asText());
  }

  @Test
  public void testStopLimitStopLimitGtdSerialization() {
    CoinbaseStopLimitStopLimitGtd config = new CoinbaseStopLimitStopLimitGtd(
        new BigDecimal("0.9"),
        new BigDecimal("32000"),
        new BigDecimal("32500"),
        "2024-01-02T00:00:00Z",
        CoinbaseStopPriceDirection.STOP_DIRECTION_STOP_DOWN);
    JsonNode node = mapper.valueToTree(config);

    assertEquals("0.9", node.get("base_size").asText());
    assertEquals("32000", node.get("limit_price").asText());
    assertEquals("32500", node.get("stop_price").asText());
    assertEquals("2024-01-02T00:00:00Z", node.get("end_time").asText());
    assertEquals("STOP_DIRECTION_STOP_DOWN", node.get("stop_direction").asText());
  }

  @Test
  public void testTriggerBracketGtcSerialization() {
    CoinbaseTriggerBracketGtc config = new CoinbaseTriggerBracketGtc(
        new BigDecimal("0.4"),
        new BigDecimal("33000"),
        new BigDecimal("34000"));
    JsonNode node = mapper.valueToTree(config);

    assertEquals("0.4", node.get("base_size").asText());
    assertEquals("33000", node.get("limit_price").asText());
    assertEquals("34000", node.get("stop_trigger_price").asText());
  }

  @Test
  public void testTriggerBracketGtdSerialization() {
    CoinbaseTriggerBracketGtd config = new CoinbaseTriggerBracketGtd(
        new BigDecimal("0.6"),
        new BigDecimal("33500"),
        new BigDecimal("34500"),
        "2024-01-03T00:00:00Z");
    JsonNode node = mapper.valueToTree(config);

    assertEquals("0.6", node.get("base_size").asText());
    assertEquals("33500", node.get("limit_price").asText());
    assertEquals("34500", node.get("stop_trigger_price").asText());
    assertEquals("2024-01-03T00:00:00Z", node.get("end_time").asText());
  }

  @Test
  public void testScaledLimitGtcSerialization() {
    CoinbaseLimitLimitGtc childOrder = new CoinbaseLimitLimitGtc(
        null,
        new BigDecimal("0.1"),
        new BigDecimal("35000"),
        Boolean.TRUE);
    CoinbaseScaledLimitGtc config = new CoinbaseScaledLimitGtc(
        Collections.singletonList(childOrder),
        new BigDecimal("500"),
        new BigDecimal("1"),
        3,
        new BigDecimal("30000"),
        new BigDecimal("36000"),
        CoinbaseScaledPriceDistribution.LINEAR_INCREASING,
        CoinbaseScaledSizeDistribution.EVENLY_SPLIT,
        new BigDecimal("0.02"),
        new BigDecimal("1.05"));
    JsonNode node = mapper.valueToTree(config);

    assertEquals("500", node.get("quote_size").asText());
    assertEquals("1", node.get("base_size").asText());
    assertEquals(3, node.get("num_orders").asInt());
    assertEquals("30000", node.get("min_price").asText());
    assertEquals("36000", node.get("max_price").asText());
    assertEquals("LINEAR_INCREASING", node.get("price_distribution").asText());
    assertEquals("EVENLY_SPLIT", node.get("size_distribution").asText());
    assertEquals("0.02", node.get("size_diff").asText());
    assertEquals("1.05", node.get("size_ratio").asText());
    assertEquals("35000", node.get("orders").get(0).get("limit_price").asText());
  }
}
