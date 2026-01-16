package org.knowm.xchange.coinbase.v3.dto.perpetuals;

import static org.junit.Assert.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.Test;

public class CoinbasePerpetualsRequestJsonTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  public void testMultiAssetCollateralRequestSerialization() {
    CoinbaseMultiAssetCollateralRequest request =
        new CoinbaseMultiAssetCollateralRequest("portfolio-uuid", Boolean.TRUE);
    JsonNode node = mapper.valueToTree(request);

    assertEquals("portfolio-uuid", node.get("portfolio_uuid").asText());
    assertEquals(true, node.get("multi_asset_collateral_enabled").asBoolean());
  }

  @Test
  public void testAllocatePortfolioRequestSerialization() {
    CoinbaseAllocatePortfolioRequest request =
        new CoinbaseAllocatePortfolioRequest(
            "portfolio-uuid",
            "BTC-PERP",
            new BigDecimal("1.5"),
            "USD");
    JsonNode node = mapper.valueToTree(request);

    assertEquals("portfolio-uuid", node.get("portfolio_uuid").asText());
    assertEquals("BTC-PERP", node.get("symbol").asText());
    assertEquals("1.5", node.get("amount").asText());
    assertEquals("USD", node.get("currency").asText());
  }
}
