package org.knowm.xchange.coinbase.v3.dto.converts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.Test;

public class CoinbaseConvertRequestJsonTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  public void testConvertQuoteRequestSerialization() {
    CoinbaseTradeIncentiveMetadata metadata =
        new CoinbaseTradeIncentiveMetadata("incentive-1", "CODE");
    CoinbaseConvertQuoteRequest request = new CoinbaseConvertQuoteRequest(
        "from-account",
        "to-account",
        new BigDecimal("12.5"),
        metadata);
    JsonNode node = mapper.valueToTree(request);

    assertEquals("from-account", node.get("from_account").asText());
    assertEquals("to-account", node.get("to_account").asText());
    assertEquals("12.5", node.get("amount").asText());
    assertNotNull(node.get("trade_incentive_metadata"));
    assertEquals("incentive-1",
        node.get("trade_incentive_metadata").get("user_incentive_id").asText());
    assertEquals("CODE", node.get("trade_incentive_metadata").get("code_val").asText());
  }

  @Test
  public void testCommitConvertTradeRequestSerialization() {
    CoinbaseCommitConvertTradeRequest request =
        new CoinbaseCommitConvertTradeRequest("from-account", "to-account");
    JsonNode node = mapper.valueToTree(request);

    assertEquals("from-account", node.get("from_account").asText());
    assertEquals("to-account", node.get("to_account").asText());
  }
}
