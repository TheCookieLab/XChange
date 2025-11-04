package info.bitrich.xchangestream.coinbase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.Delayed;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
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

  @Test
  void schedulesJwtRefreshForPrivateChannels() throws Exception {
    RecordingScheduler scheduler = new RecordingScheduler();
    CapturingCoinbaseStreamingService service = new CapturingCoinbaseStreamingService(scheduler);

    Object channelState = newChannelState(CoinbaseChannel.USER);
    invoke(
        channelState,
        "incrementRefCounts",
        new java.util.ArrayList<String>(java.util.Collections.singletonList("BTC-USD")),
        new java.util.HashMap<String, Object>());

    scheduleJwtRefresh(service, channelState);

    assertTrue(scheduler.wasScheduled());
    assertEquals(1L, scheduler.getPeriodSeconds());
    assertTrue(scheduler.getFuture() != null);
    assertFalse(scheduler.getFuture().isCancelled());

    scheduler.trigger();

    Map<String, Object> payload = service.getLastPayload();
    assertEquals("subscribe", payload.get("type"));
    assertEquals("user", payload.get("channel"));
    assertEquals("jwt-token", payload.get("jwt"));
    @SuppressWarnings("unchecked")
    List<String> productIds = (List<String>) payload.get("product_ids");
    assertEquals("BTC-USD", productIds.get(0));
  }

  @Test
  void cancelsJwtRefreshWhenLastPrivateSubscriptionRemoved() throws Exception {
    RecordingScheduler scheduler = new RecordingScheduler();
    CapturingCoinbaseStreamingService service = new CapturingCoinbaseStreamingService(scheduler);

    Object channelState = newChannelState(CoinbaseChannel.USER);
    invoke(
        channelState,
        "incrementRefCounts",
        new java.util.ArrayList<String>(java.util.Collections.singletonList("BTC-USD")),
        new java.util.HashMap<String, Object>());

    @SuppressWarnings("unchecked")
    Map<CoinbaseChannel, Object> channelStates = (Map<CoinbaseChannel, Object>) channelStates(service);
    channelStates.put(CoinbaseChannel.USER, channelState);

    scheduleJwtRefresh(service, channelState);
    assertTrue(scheduler.wasScheduled());
    assertTrue(scheduler.getFuture() != null);

    CoinbaseSubscriptionRequest request =
        new CoinbaseSubscriptionRequest(
            CoinbaseChannel.USER,
            java.util.Collections.singletonList("BTC-USD"),
            java.util.Collections.emptyMap());

    service.unsubscribeChannel(request);

    assertTrue(scheduler.getFuture().isCancelled());
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

  private static void scheduleJwtRefresh(
      CoinbaseStreamingService service, Object channelState) throws Exception {
    Method method =
        CoinbaseStreamingService.class.getDeclaredMethod(
            "scheduleJwtRefreshIfActive", channelState.getClass());
    method.setAccessible(true);
    method.invoke(service, channelState);
  }

  private static Object channelStates(CoinbaseStreamingService service) throws Exception {
    Field field =
        CoinbaseStreamingService.class.getDeclaredField("channelStates");
    field.setAccessible(true);
    return field.get(service);
  }

  private static final class CapturingCoinbaseStreamingService extends CoinbaseStreamingService {
    private Map<String, Object> lastPayload = Collections.emptyMap();

    private CapturingCoinbaseStreamingService(ScheduledExecutorService scheduler) {
      super(
          "wss://example.com",
          () -> "jwt-token",
          Duration.ofSeconds(5),
          Duration.ofSeconds(5),
          10,
          1024,
          8,
          750,
          1,
          scheduler);
    }

    @Override
    protected void sendObjectMessage(Object message) {
      if (message instanceof Map<?, ?>) {
        lastPayload = new HashMap<>();
        Map<?, ?> raw = (Map<?, ?>) message;
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
          lastPayload.put(String.valueOf(entry.getKey()), entry.getValue());
        }
      }
    }

    Map<String, Object> getLastPayload() {
      return lastPayload;
    }
  }

  private static final class RecordingScheduler extends AbstractExecutorService
      implements ScheduledExecutorService {
    private boolean shutdown;
    private Runnable scheduledTask;
    private long periodSeconds;
    private TestScheduledFuture future;

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(
        Runnable command, long initialDelay, long period, TimeUnit unit) {
      this.scheduledTask = command;
      this.periodSeconds = unit.toSeconds(period);
      this.future = new TestScheduledFuture();
      return future;
    }

    @Override
    public void shutdown() {
      shutdown = true;
    }

    @Override
    public List<Runnable> shutdownNow() {
      shutdown = true;
      return Collections.emptyList();
    }

    @Override
    public boolean isShutdown() {
      return shutdown;
    }

    @Override
    public boolean isTerminated() {
      return shutdown;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) {
      return shutdown;
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
      throw new UnsupportedOperationException("schedule not supported");
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
      throw new UnsupportedOperationException("schedule not supported");
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(
        Runnable command, long initialDelay, long delay, TimeUnit unit) {
      throw new UnsupportedOperationException("scheduleWithFixedDelay not supported");
    }

    @Override
    public void execute(Runnable command) {
      command.run();
    }

    boolean wasScheduled() {
      return scheduledTask != null;
    }

    long getPeriodSeconds() {
      return periodSeconds;
    }

    TestScheduledFuture getFuture() {
      return future;
    }

    void trigger() {
      if (scheduledTask != null) {
        scheduledTask.run();
      }
    }
  }

  private static final class TestScheduledFuture implements ScheduledFuture<Object> {
    private volatile boolean cancelled;

    @Override
    public long getDelay(TimeUnit unit) {
      return 0;
    }

    @Override
    public int compareTo(Delayed o) {
      return 0;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
      cancelled = true;
      return true;
    }

    @Override
    public boolean isCancelled() {
      return cancelled;
    }

    @Override
    public boolean isDone() {
      return cancelled;
    }

    @Override
    public Object get() {
      throw new UnsupportedOperationException("get not supported");
    }

    @Override
    public Object get(long timeout, TimeUnit unit) {
      throw new UnsupportedOperationException("get not supported");
    }
  }
}
