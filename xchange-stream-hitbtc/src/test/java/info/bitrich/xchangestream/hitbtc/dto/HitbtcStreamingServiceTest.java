package info.bitrich.xchangestream.hitbtc.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import info.bitrich.xchangestream.hitbtc.HitbtcStreamingService;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

/** Created by Pavel Chertalev on 15.03.2018. */
public class HitbtcStreamingServiceTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final HitbtcStreamingService streamingService = new HitbtcStreamingService("testUrl");

  @Rule public ExpectedException thrown;

  public HitbtcStreamingServiceTest() {
    thrown = ExpectedException.none();
  }

  @Test
  public void getChannelNameFromMessageTest()
      throws IOException, InvocationTargetException, IllegalAccessException {

    Method method =
        MethodUtils.getMatchingMethod(
            HitbtcStreamingService.class, "getChannelNameFromMessage", JsonNode.class);
    method.setAccessible(true);

    String json = "{\"method\":\"aaa\"}";
    Assert.assertEquals("aaa", method.invoke(streamingService, objectMapper.readTree(json)));

    json = "{ \"method\": \"updateOrderbook\", \"params\": { \"symbol\": \"ETHBTC\" } }";
    Assert.assertEquals(
        "orderbook-ETHBTC", method.invoke(streamingService, objectMapper.readTree(json)));

    json = "{ \"method\": \"snapshotOrderbook\", \"params\": { \"symbol\": \"ETHBTC\" } }";
    Assert.assertEquals(
        "orderbook-ETHBTC", method.invoke(streamingService, objectMapper.readTree(json)));

    json = "{ \"method\": \"test\", \"params\": { \"symbol\": \"ETHBTC\" } }";
    Assert.assertEquals(
        "test-ETHBTC", method.invoke(streamingService, objectMapper.readTree(json)));

    json = "{ \"noMethod\": \"updateOrderbook\" } }";

    thrown.expect(InvocationTargetException.class);
    method.invoke(streamingService, objectMapper.readTree(json));
  }

  @Test
  @SuppressWarnings("unchecked")
  public void requestLookupUsesStoredEntryAndRemovesItAfterHandling() throws Exception {
    Field requestsField = HitbtcStreamingService.class.getDeclaredField("requests");
    requestsField.setAccessible(true);
    Map<Integer, Pair<String, String>> requests =
        (Map<Integer, Pair<String, String>>) requestsField.get(streamingService);
    requests.put(7, ImmutablePair.of("ticker-ETHBTC", "subscribeTicker"));

    Method channelNameMethod =
        MethodUtils.getMatchingMethod(
            HitbtcStreamingService.class, "getChannelNameFromMessage", JsonNode.class);
    channelNameMethod.setAccessible(true);
    Assert.assertEquals(
        "ticker-ETHBTC",
        channelNameMethod.invoke(streamingService, objectMapper.readTree("{\"id\":7}")));

    Method handleMessageMethod =
        MethodUtils.getMatchingMethod(HitbtcStreamingService.class, "handleMessage", JsonNode.class);
    handleMessageMethod.setAccessible(true);
    handleMessageMethod.invoke(streamingService, objectMapper.readTree("{\"id\":7,\"result\":true}"));

    Assert.assertFalse(requests.containsKey(7));
  }
}
