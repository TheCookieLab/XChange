package info.bitrich.xchangestream.kucoin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/** Deterministic protocol fixtures: subscribe payloads, unique-id conventions, frame routing. */
class UtaStreamingProtocolTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static UtaStreamingService newService() {
    return new UtaStreamingService("wss://x-push-spot.kucoin.com", false, null);
  }

  @Test
  void subscribeMessageCarriesActionChannelAndTradeType() throws Exception {
    String message = newService().getSubscribeMessage("obu", "SPOT", "BTC-USDT", "increment@10ms");
    JsonNode node = MAPPER.readTree(message);
    assertEquals("SUBSCRIBE", node.get("action").asText());
    assertEquals("obu", node.get("channel").asText());
    assertEquals("SPOT", node.get("tradeType").asText());
    assertEquals("BTC-USDT", node.get("symbol").asText());
    assertEquals("increment@10ms", node.get("depth").asText());
    assertTrue(node.get("id").asLong() > 0);
  }

  @Test
  void unsubscribeMessageUsesUnsubscribeAction() throws Exception {
    String message = newService().getUnsubscribeMessage("ticker", "SPOT", "BTC-USDT");
    assertEquals("UNSUBSCRIBE", MAPPER.readTree(message).get("action").asText());
  }

  @Test
  void uniqueIdsAreAmbiguityFree() {
    UtaStreamingService service = newService();
    assertEquals(
        "obu|SPOT|BTC-USDT|increment@10ms",
        service.getSubscriptionUniqueId("obu", "SPOT", "BTC-USDT", "increment@10ms"));
    assertEquals(
        "ticker|FUTURES|XBTUSDTM",
        service.getSubscriptionUniqueId("ticker", "FUTURES", "XBTUSDTM"));
    assertEquals("order|UNIFIED|XRP-USDT", service.getSubscriptionUniqueId("order", "UNIFIED", "XRP-USDT"));
    assertEquals("balance|UNIFIED", service.getSubscriptionUniqueId("balance", "UNIFIED"));
  }

  @Test
  void orderBookFrameRoutesToSymbolAndDepthScopedChannel() {
    UtaStreamingService service = newService();
    String frame =
        "{\"T\":\"obu.SPOT\",\"dp\":\"increment@10ms\",\"t\":\"delta\",\"P\":1768217909684719896,"
            + "\"d\":{\"C\":25984544840,\"O\":25984544839,\"b\":[[\"1\",\"12996.24994153\"]],"
            + "\"a\":[],\"s\":\"BTC-USDT\"}}";
    assertEquals(
        "obu|SPOT|BTC-USDT|increment@10ms",
        service.getChannelNameFromMessage(read(frame)));
  }

  @Test
  void tickerFrameRoutesToSymbolScopedChannel() {
    UtaStreamingService service = newService();
    String frame =
        "{\"T\":\"ticker.FUTURES\",\"P\":1768218267869446269,"
            + "\"d\":{\"a\":\"90580.5\",\"A\":\"36\",\"s\":\"XBTUSDTM\",\"S\":\"buy\"}}";
    assertEquals("ticker|FUTURES|XBTUSDTM", service.getChannelNameFromMessage(read(frame)));
  }

  @Test
  void symbolLessChannelsRouteByTopicOnly() {
    UtaStreamingService service = newService();
    assertEquals(
        "balance|UNIFIED",
        service.getChannelNameFromMessage(
            read("{\"T\":\"balance.UNIFIED\",\"P\":1,\"d\":{\"c\":\"BTC\"}}")));
    assertEquals(
        "orderAll|UNIFIED",
        service.getChannelNameFromMessage(
            read("{\"T\":\"orderAll.UNIFIED\",\"P\":1,\"d\":{\"oi\":\"x\"}}")));
  }

  @Test
  void controlFramesHaveNoChannel() {
    UtaStreamingService service = newService();
    assertEquals(null, service.getChannelNameFromMessage(read("{\"id\":1,\"type\":\"pong\"}")));
    assertEquals(
        null,
        service.getChannelNameFromMessage(read("{\"sessionId\":\"s\",\"message\":\"welcome\"}")));
  }

  private static JsonNode read(String json) {
    try {
      return MAPPER.readTree(json);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
