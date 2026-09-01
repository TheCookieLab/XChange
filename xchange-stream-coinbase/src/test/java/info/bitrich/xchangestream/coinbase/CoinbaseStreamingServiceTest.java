package info.bitrich.xchangestream.coinbase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.observers.TestObserver;
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
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.coinbase.v3.CoinbaseUnknownOutcomeException;
import org.knowm.xchange.coinbase.v3.dto.CoinbaseException;
import org.knowm.xchange.coinbase.v3.dto.RetryClassification;

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

  @Test
  void schedulerRemainsUsableAfterDisconnect() throws Exception {
    RecordingScheduler scheduler = new RecordingScheduler();
    CapturingCoinbaseStreamingService service = new CapturingCoinbaseStreamingService(scheduler);

    // Schedule a JWT refresh before disconnect
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
    assertFalse(scheduler.isShutdown());

    // Disconnect - this should NOT shut down the scheduler
    Completable disconnect = service.disconnect();
    disconnect.blockingAwait();

    // Verify scheduler is still usable (not shut down)
    assertFalse(scheduler.isShutdown(), "Scheduler should remain alive after disconnect");
    assertFalse(scheduler.isTerminated(), "Scheduler should not be terminated after disconnect");

    // Verify we can still schedule new tasks after disconnect
    scheduler.reset();
    scheduleJwtRefresh(service, channelState);
    assertTrue(scheduler.wasScheduled(), "Should be able to schedule tasks after disconnect");
    assertNotNull(scheduler.getFuture());
  }

  @Test
  void jwtRefreshWorksAfterDisconnectReconnectCycle() throws Exception {
    RecordingScheduler scheduler = new RecordingScheduler();
    CapturingCoinbaseStreamingService service = new CapturingCoinbaseStreamingService(scheduler);

    // Initial subscription
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
    ScheduledFuture<?> firstFuture = scheduler.getFuture();
    assertNotNull(firstFuture);

    // Disconnect
    Completable disconnect = service.disconnect();
    disconnect.blockingAwait();

    // Verify the first future was cancelled (cleanup)
    assertTrue(firstFuture.isCancelled(), "JWT refresh task should be cancelled on disconnect");

    // Verify scheduler is still alive
    assertFalse(scheduler.isShutdown(), "Scheduler must remain alive after disconnect");

    // Simulate reconnect: create new subscription (as would happen on reconnect)
    scheduler.reset();
    Object newChannelState = newChannelState(CoinbaseChannel.USER);
    invoke(
        newChannelState,
        "incrementRefCounts",
        new java.util.ArrayList<String>(java.util.Collections.singletonList("BTC-USD")),
        new java.util.HashMap<String, Object>());
    channelStates.put(CoinbaseChannel.USER, newChannelState);

    // Schedule JWT refresh again (should work without RejectedExecutionException)
    scheduleJwtRefresh(service, newChannelState);

    // Verify new JWT refresh was scheduled successfully
    assertTrue(scheduler.wasScheduled(), "Should be able to schedule JWT refresh after reconnect");
    ScheduledFuture<?> secondFuture = scheduler.getFuture();
    assertNotNull(secondFuture);
    assertFalse(secondFuture.isCancelled(), "New JWT refresh should be active");

    // Verify the refresh task actually runs
    scheduler.trigger();
    Map<String, Object> payload = service.getLastPayload();
    assertEquals("subscribe", payload.get("type"));
    assertEquals("user", payload.get("channel"));
    assertEquals("jwt-token", payload.get("jwt"));
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

  private static class CapturingCoinbaseStreamingService extends CoinbaseStreamingService {
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

    private CapturingCoinbaseStreamingService(
        int unauthenticatedPerSecond, int authenticatedPerSecond) {
      super("wss://example.com", () -> "jwt-token", unauthenticatedPerSecond, authenticatedPerSecond);
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

  private static final class CountingCapturingService extends CapturingCoinbaseStreamingService {
    private int sentCommands;

    private CountingCapturingService(ScheduledExecutorService scheduler) {
      super(scheduler);
    }

    /** One permit per second per limiter so throttling is observable in tests. */
    private CountingCapturingService() {
      super(1, 1);
    }

    @Override
    protected void sendObjectMessage(Object message) {
      sentCommands++;
      super.sendObjectMessage(message);
    }
  }

  @Test
  void protocolErrorMessagesAreClassifiedAndPublished() {
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

    TestObserver<Throwable> observer = service.protocolErrors().test();

    service.messageHandler("{\"type\":\"error\",\"message\":\"Authentication failed\"}");
    service.messageHandler("{\"type\":\"error\",\"message\":\"Invalid channel\"}");
    service.messageHandler("{\"type\":\"subscriptions\",\"channel\":\"user\"}");

    observer.awaitCount(2);
    observer.assertNoErrors();
    assertEquals(
        RetryClassification.AUTHENTICATION,
        ((CoinbaseException) observer.values().get(0)).getRetryClassification());
    assertEquals(
        RetryClassification.PERMANENT,
        ((CoinbaseException) observer.values().get(1)).getRetryClassification());
    // Non-error messages must not be published as protocol errors.
    assertEquals(2, observer.values().size());
  }

  @Test
  void disconnectIncrementsConnectionGeneration() {
    CountingCapturingService service = new CountingCapturingService(new RecordingScheduler());
    long before = service.currentGeneration();
    service.disconnect().blockingAwait();
    assertEquals(before + 1, service.currentGeneration());
  }

  @Test
  void staleCommandsAreDroppedWhenGenerationChangesDuringThrottling() throws Exception {
    // 1 permit per second: the first command consumes it, the second blocks in the limiter.
    CountingCapturingService service = new CountingCapturingService();

    service.subscribeChannel(
        new CoinbaseSubscriptionRequest(
            CoinbaseChannel.TICKER, Collections.singletonList("BTC-USD"), Collections.emptyMap()));
    assertEquals(1, service.sentCommands);

    AtomicReference<Object> secondResult = new AtomicReference<>();
    Thread second =
        new Thread(
            () ->
                secondResult.set(
                    service.subscribeChannel(
                        new CoinbaseSubscriptionRequest(
                            CoinbaseChannel.TICKER,
                            Collections.singletonList("ETH-USD"),
                            Collections.emptyMap()))));
    second.start();
    Thread.sleep(100);
    // Connection drops while the second command is throttled: bump the generation.
    bumpGeneration(service);
    second.join(5000);
    assertFalse(second.isAlive());

    // The stale command must not be sent on the new generation.
    assertEquals(1, service.sentCommands);
  }

  @Test
  void pendingUserSubscriptionFailsWhenCommandDroppedAsStale() throws Exception {
    CountingCapturingService service = new CountingCapturingService();

    service.subscribeChannel(
        new CoinbaseSubscriptionRequest(
            CoinbaseChannel.USER, Collections.singletonList("BTC-USD"), Collections.emptyMap()));
    assertEquals(1, service.sentCommands);

    AtomicReference<io.reactivex.rxjava3.core.Observable<JsonNode>> second =
        new AtomicReference<>();
    Thread thread =
        new Thread(
            () ->
                second.set(
                    service.subscribeChannel(
                        new CoinbaseSubscriptionRequest(
                            CoinbaseChannel.USER,
                            Collections.singletonList("BTC-USD"),
                            Collections.emptyMap()))));
    thread.start();
    Thread.sleep(100);
    bumpGeneration(service);
    thread.join(5000);
    assertFalse(thread.isAlive());

    TestObserver<JsonNode> observer = second.get().test();
    observer.awaitDone(2, TimeUnit.SECONDS);
    observer.assertError(
        error -> {
          if (!(error instanceof CoinbaseUnknownOutcomeException)) {
            return false;
          }
          CoinbaseUnknownOutcomeException unknown =
              (CoinbaseUnknownOutcomeException) error;
          return unknown.getRetryClassification() == RetryClassification.AMBIGUOUS
              && "channel".equals(unknown.getCorrelationName())
              && CoinbaseChannel.USER.channelName().equals(unknown.getCorrelationId())
              && unknown.getClientOrderIds().isEmpty();
        });
    assertEquals(1, service.sentCommands);
  }

  private static void bumpGeneration(CoinbaseStreamingService service) throws Exception {
    Field field = CoinbaseStreamingService.class.getDeclaredField("connectionGeneration");
    field.setAccessible(true);
    ((AtomicLong) field.get(service)).incrementAndGet();
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

    void reset() {
      scheduledTask = null;
      future = null;
      periodSeconds = 0;
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
