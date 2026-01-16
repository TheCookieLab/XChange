package org.knowm.xchange.coinbase.v3.dto.portfolios;

import static org.junit.Assert.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.Test;

public class CoinbasePortfolioRequestJsonTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  public void testPortfolioRequestSerialization() {
    CoinbasePortfolioRequest request = new CoinbasePortfolioRequest("Growth");
    JsonNode node = mapper.valueToTree(request);

    assertEquals("Growth", node.get("name").asText());
  }

  @Test
  public void testMovePortfolioFundsRequestSerialization() {
    CoinbasePortfolioAmount amount = new CoinbasePortfolioAmount(
        new BigDecimal("1000"),
        "USD");
    CoinbaseMovePortfolioFundsRequest request = new CoinbaseMovePortfolioFundsRequest(
        amount,
        "source-uuid",
        "target-uuid");
    JsonNode node = mapper.valueToTree(request);

    assertEquals("1000", node.get("funds").get("value").asText());
    assertEquals("USD", node.get("funds").get("currency").asText());
    assertEquals("source-uuid", node.get("source_portfolio_uuid").asText());
    assertEquals("target-uuid", node.get("target_portfolio_uuid").asText());
  }
}
