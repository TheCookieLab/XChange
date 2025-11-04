package info.bitrich.xchangestream.coinbase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class CoinbaseStreamingServiceTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  void subscribeMessageIncludesJwtAndProductIds() throws Exception {
    CoinbaseStreamingService service =
        new CoinbaseStreamingService(
            "wss://example.com",
            () -> "jwt-token",
            Duration.ofSeconds(5),
            Duration.ofSeconds(5),
            10,
            1024,
            8,
            750);

    Object channelState = newChannelState(CoinbaseChannel.USER);
    invoke(
        channelState,
        "incrementRefCounts",
        new java.util.ArrayList<String>(java.util.Collections.singletonList("BTC-USD")),
        new java.util.HashMap<String, Object>());

    String json = service.getSubscribeMessage(CoinbaseChannel.USER.channelName(), channelState);
    JsonNode node = MAPPER.readTree(json);
    assertEquals("subscribe", node.path("type").asText());
    assertEquals("jwt-token", node.path("jwt").asText());
    assertEquals("BTC-USD", node.path("product_ids").get(0).asText());
  }

  @Test
  void unsubscribeMessageOmitsEmptyJwtWhenUnavailable() throws Exception {
    CoinbaseStreamingService service =
        new CoinbaseStreamingService(
            "wss://example.com",
            () -> "",
            Duration.ofSeconds(5),
            Duration.ofSeconds(5),
            10,
            1024,
            8,
            750);

    Object channelState = newChannelState(CoinbaseChannel.USER);
    invoke(
        channelState,
        "incrementRefCounts",
        new java.util.ArrayList<String>(java.util.Collections.singletonList("BTC-USD")),
        new java.util.HashMap<String, Object>());

    String json =
        service.getUnsubscribeMessage(CoinbaseChannel.USER.channelName(), channelState);
    JsonNode node = MAPPER.readTree(json);
    assertEquals("unsubscribe", node.path("type").asText());
    assertTrue(node.path("jwt").isMissingNode() || node.path("jwt").isNull());
  }

  private static Object newChannelState(CoinbaseChannel channel)
      throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException,
          InstantiationException, IllegalAccessException {
    Class<?> stateClass =
        Class.forName("info.bitrich.xchangestream.coinbase.CoinbaseStreamingService$ChannelState");
    Constructor<?> ctor = stateClass.getDeclaredConstructor(CoinbaseChannel.class);
    ctor.setAccessible(true);
    return ctor.newInstance(channel);
  }

  private static Object invoke(Object target, String methodName, Object... args) throws Exception {
    Class<?>[] argTypes = new Class<?>[args.length];
    for (int i = 0; i < args.length; i++) {
      if (args[i] instanceof List) {
        argTypes[i] = List.class;
      } else if (args[i] instanceof java.util.Map) {
        argTypes[i] = java.util.Map.class;
      } else {
        argTypes[i] = args[i].getClass();
      }
    }
    Method method =
        target.getClass().getDeclaredMethod(methodName, argTypes);
    method.setAccessible(true);
    return method.invoke(target, args);
  }
}
