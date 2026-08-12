package info.bitrich.xchangestream.bitget.uta.v3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import info.bitrich.xchangestream.bitget.uta.v3.dto.BitgetUtaV3Channel;
import info.bitrich.xchangestream.bitget.uta.v3.dto.BitgetUtaV3EventNotification;
import info.bitrich.xchangestream.bitget.uta.v3.dto.BitgetUtaV3EventNotification.Event;
import info.bitrich.xchangestream.bitget.uta.v3.dto.BitgetUtaV3InstType;
import info.bitrich.xchangestream.bitget.uta.v3.dto.BitgetUtaV3WsNotification;
import info.bitrich.xchangestream.service.netty.NettyStreamingService;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.observers.TestObserver;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.exceptions.ExchangeException;

/**
 * A channel rejected by an error acknowledgement must be fully deregistered — from the channel
 * registry and from the shared-observable cache — so a later retry builds a fresh subscription
 * instead of reusing the terminated stream.
 *
 * <p>{@link BitgetUtaV3StreamingService#sharedChannel} caches one shared observable per
 * subscription id; leaving the dead entry in that cache would make a retry return the terminated
 * stream, whose underlying emitter was already removed, so the caller would hang on a channel that
 * can never push. The error path removes both entries before terminating the emitter, and {@link
 * BitgetUtaV3StreamingService#failAllChannels} clears the shared cache too so a reconnect's
 * resubscription rebuilds it.
 */
class BitgetUtaV3StreamingDeadChannelRetryTest {

  private static final BitgetUtaV3Channel BTC_TRADE =
      BitgetUtaV3Channel.builder()
          .instType(BitgetUtaV3InstType.SPOT)
          .topic("trade")
          .symbol("BTCUSDT")
          .build();

  private static final BitgetUtaV3Channel ETH_TRADE =
      BitgetUtaV3Channel.builder()
          .instType(BitgetUtaV3InstType.SPOT)
          .topic("trade")
          .symbol("ETHUSDT")
          .build();

  /** Subclass recording outbound frames and exposing registry state without a live socket. */
  private static class TestableStreamingService extends BitgetUtaV3StreamingService {

    private final List<String> frames = new ArrayList<>();

    TestableStreamingService() {
      super("ws://127.0.0.1:9");
      injectOpenChannel(this);
    }

    List<String> frames() {
      return frames;
    }

    boolean containsChannel(String subscriptionId) {
      return channels.containsKey(subscriptionId);
    }

    Map<String, Observable<BitgetUtaV3WsNotification>> sharedChannels() throws Exception {
      Field field = BitgetUtaV3StreamingService.class.getDeclaredField("sharedChannels");
      field.setAccessible(true);
      @SuppressWarnings("unchecked")
      Map<String, Observable<BitgetUtaV3WsNotification>> map =
          (Map<String, Observable<BitgetUtaV3WsNotification>>) field.get(this);
      return map;
    }

    @Override
    public void sendMessage(String message) {
      frames.add(message);
    }
  }

  private static void injectOpenChannel(BitgetUtaV3StreamingService service) {
    io.netty.channel.Channel channel = mock(io.netty.channel.Channel.class);
    when(channel.isOpen()).thenReturn(true);
    when(channel.isWritable()).thenReturn(true);
    when(channel.writeAndFlush(any())).thenReturn(mock(io.netty.channel.ChannelFuture.class));
    try {
      Field field = NettyStreamingService.class.getDeclaredField("webSocketChannel");
      field.setAccessible(true);
      field.set(service, channel);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new IllegalStateException("Cannot inject WebSocket channel", e);
    }
  }

  private static BitgetUtaV3EventNotification errorAck(BitgetUtaV3Channel channel) {
    return BitgetUtaV3EventNotification.builder()
        .event(Event.ERROR)
        .code("30001")
        .message("invalid symbol")
        .channel(channel)
        .build();
  }

  @Test
  void errorAckRemovesDeadChannelFromRegistryAndSharedCacheSoRetrySucceeds() throws Exception {
    TestableStreamingService service = new TestableStreamingService();
    Observable<BitgetUtaV3WsNotification> shared = service.sharedChannel(BTC_TRADE);
    TestObserver<BitgetUtaV3WsNotification> first = shared.test();
    assertThat(service.frames()).hasSize(1);
    assertThat(service.containsChannel(BTC_TRADE.toSubscriptionId())).isTrue();

    service.handleEventNotification(errorAck(BTC_TRADE));

    // the affected stream terminates with the rejection ...
    first.assertError(ExchangeException.class);
    // ... and the dead entry is gone from both registries
    assertThat(service.containsChannel(BTC_TRADE.toSubscriptionId())).isFalse();
    assertThat(service.sharedChannels()).doesNotContainKey(BTC_TRADE.toSubscriptionId());

    // a retry must rebuild a live subscription instead of reusing the terminated stream
    Observable<BitgetUtaV3WsNotification> retried = service.sharedChannel(BTC_TRADE);
    TestObserver<BitgetUtaV3WsNotification> second = retried.test();
    assertThat(service.frames()).hasSize(2);
    assertThat(service.containsChannel(BTC_TRADE.toSubscriptionId())).isTrue();
    second.assertNoErrors();
    second.assertNotComplete();
  }

  @Test
  void errorAckForOneChannelLeavesOtherChannelsAlive() throws Exception {
    TestableStreamingService service = new TestableStreamingService();
    service.sharedChannel(BTC_TRADE).test();
    TestObserver<BitgetUtaV3WsNotification> eth = service.sharedChannel(ETH_TRADE).test();

    service.handleEventNotification(errorAck(BTC_TRADE));

    eth.assertNoErrors();
    eth.assertNotComplete();
    assertThat(service.containsChannel(ETH_TRADE.toSubscriptionId())).isTrue();
    assertThat(service.sharedChannels()).containsKey(ETH_TRADE.toSubscriptionId());
  }

  @Test
  void failAllChannelsClearsTheSharedObservableCache() throws Exception {
    TestableStreamingService service = new TestableStreamingService();
    service.sharedChannel(BTC_TRADE).test();
    service.sharedChannel(ETH_TRADE).test();
    assertThat(service.sharedChannels()).hasSize(2);

    service.failAllChannels(new ExchangeException("connection failed"));

    assertThat(service.sharedChannels()).isEmpty();
  }
}
