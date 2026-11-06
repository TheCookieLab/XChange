package info.bitrich.xchangestream.hitbtc.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import info.bitrich.xchangestream.hitbtc.HitbtcStreamingService;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.junit.jupiter.api.Test;

/** Created by Pavel Chertalev on 15.03.2018. */
public class HitbtcStreamingServiceTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final HitbtcStreamingService streamingService = new HitbtcStreamingService("testUrl");

  @Test
  void getChannelNameFromMessageTest() throws Exception {

    Method method =
        MethodUtils.getMatchingMethod(
            HitbtcStreamingService.class, "getChannelNameFromMessage", JsonNode.class);
    method.setAccessible(true);

    String json = "{\"method\":\"aaa\"}";
    assertThat(method.invoke(streamingService, objectMapper.readTree(json))).isEqualTo("aaa");

    json = "{ \"method\": \"updateOrderbook\", \"params\": { \"symbol\": \"ETHBTC\" } }";
    assertThat(method.invoke(streamingService, objectMapper.readTree(json)))
        .isEqualTo("orderbook-ETHBTC");

    json = "{ \"method\": \"snapshotOrderbook\", \"params\": { \"symbol\": \"ETHBTC\" } }";
    assertThat(method.invoke(streamingService, objectMapper.readTree(json)))
        .isEqualTo("orderbook-ETHBTC");

    json = "{ \"method\": \"test\", \"params\": { \"symbol\": \"ETHBTC\" } }";
    assertThat(method.invoke(streamingService, objectMapper.readTree(json)))
        .isEqualTo("test-ETHBTC");

    JsonNode noMethod = objectMapper.readTree("{ \"noMethod\": \"updateOrderbook\" } }");

    assertThatExceptionOfType(InvocationTargetException.class)
        .isThrownBy(() -> method.invoke(streamingService, noMethod));
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
    assertThat(channelNameMethod.invoke(streamingService, objectMapper.readTree("{\"id\":7}")))
        .isEqualTo("ticker-ETHBTC");

    Method handleMessageMethod =
        MethodUtils.getMatchingMethod(HitbtcStreamingService.class, "handleMessage", JsonNode.class);
    handleMessageMethod.setAccessible(true);
    handleMessageMethod.invoke(streamingService, objectMapper.readTree("{\"id\":7,\"result\":true}"));

    assertThat(requests).doesNotContainKey(7);
  }
}
