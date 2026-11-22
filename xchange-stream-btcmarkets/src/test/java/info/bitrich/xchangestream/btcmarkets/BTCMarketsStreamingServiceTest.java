package info.bitrich.xchangestream.btcmarkets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BTCMarketsStreamingServiceTest {
  private final ObjectMapper mapper = new ObjectMapper();
  private final BTCMarketsStreamingService service =
      new BTCMarketsStreamingService("wss://example.invalid");

  @Test
  void unsubscribingOneMarketUsesTheWireChannelAndLeavesOthersIndependent() throws Exception {
    for (String channel : new String[] {"orderbook", "tick", "trade"}) {
      service.getSubscribeMessage(channel, "BTC-AUD");
      service.getSubscribeMessage(channel, "ETH-AUD");
      String btcId = service.getSubscriptionUniqueId(channel, "BTC-AUD");
      String ethId = service.getSubscriptionUniqueId(channel, "ETH-AUD");

      JsonNode btc = mapper.readTree(service.getUnsubscribeMessage(btcId, "BTC-AUD"));
      assertEquals("removeSubscription", btc.get("messageType").asText());
      assertEquals(1, btc.get("channels").size());
      assertEquals(channel, btc.get("channels").get(0).asText());
      assertEquals(1, btc.get("marketIds").size());
      assertEquals("BTC-AUD", btc.get("marketIds").get(0).asText());

      JsonNode eth = mapper.readTree(service.getUnsubscribeMessage(ethId, "ETH-AUD"));
      assertEquals(channel, eth.get("channels").get(0).asText());
      assertEquals("ETH-AUD", eth.get("marketIds").get(0).asText());
    }
  }

  @Test
  void unsubscribeUsesAllOriginalMarketsAndCanRecoverMarketFromUniqueId() throws Exception {
    JsonNode multiple =
        mapper.readTree(service.getUnsubscribeMessage("tick:BTC-AUD", "BTC-AUD", "ETH-AUD"));
    Set<String> markets = new HashSet<>();
    multiple.get("marketIds").forEach(node -> markets.add(node.asText()));
    assertEquals(Set.of("BTC-AUD", "ETH-AUD"), markets);

    JsonNode fromId = mapper.readTree(service.getUnsubscribeMessage("trade:BTC-AUD"));
    assertEquals("trade", fromId.get("channels").get(0).asText());
    assertEquals("BTC-AUD", fromId.get("marketIds").get(0).asText());
    assertNull(service.getUnsubscribeMessage("tickbogus:BTC-AUD"));
  }
}
