package org.knowm.xchange.coinbase.v3.dto.futures;

import static org.junit.Assert.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.Test;

public class CoinbaseFuturesRequestJsonTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  public void testFuturesSweepRequestSerialization() {
    CoinbaseFuturesSweepRequest request = new CoinbaseFuturesSweepRequest(new BigDecimal("250"));
    JsonNode node = mapper.valueToTree(request);

    assertEquals("250", node.get("usd_amount").asText());
  }

  @Test
  public void testIntradayMarginSettingRequestSerialization() {
    CoinbaseIntradayMarginSettingRequest request =
        new CoinbaseIntradayMarginSettingRequest(
            CoinbaseIntradayMarginSetting.INTRADAY_MARGIN_SETTING_STANDARD);
    JsonNode node = mapper.valueToTree(request);

    assertEquals("INTRADAY_MARGIN_SETTING_STANDARD", node.get("setting").asText());
  }
}
