package info.bitrich.xchangestream.bitget.uta.v3;

import static org.assertj.core.api.Assertions.assertThat;

import info.bitrich.xchangestream.bitget.uta.v3.dto.BitgetUtaV3Channel;
import info.bitrich.xchangestream.bitget.uta.v3.dto.BitgetUtaV3EventNotification;
import info.bitrich.xchangestream.bitget.uta.v3.dto.BitgetUtaV3EventNotification.Event;
import info.bitrich.xchangestream.bitget.uta.v3.dto.BitgetUtaV3InstType;
import info.bitrich.xchangestream.bitget.uta.v3.dto.BitgetUtaV3WsNotification;
import info.bitrich.xchangestream.service.exception.NotConnectedException;
import io.reactivex.rxjava3.core.Observable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Private channel frames must wait for the {@code login} acknowledgement of the current
 * connection.
 *
 * <p>{@link BitgetUtaV3PrivateStreamingService#subscribeChannel} registers the subscription
 * immediately but defers the subscribe frame: the server rejects any channel frame sent before
 * login completes, and {@code connect()} completes on the socket handshake rather than the login
 * ack, so a caller subscribing right after connect would otherwise get an error acknowledgement
 * that kills the stream. The frame is flushed when the ack arrives, and subscriptions made after
 * the ack send their frame immediately. A closed socket errors with {@link NotConnectedException}
 * instead of registering a channel that can never be served.
 */
class BitgetUtaV3PrivateStreamingLoginGateTest {

  private static final BitgetUtaV3Channel BTC_TRADE =
      BitgetUtaV3Channel.builder()
          .instType(BitgetUtaV3InstType.UTA)
          .topic("order")
          .symbol("BTCUSDT")
          .build();

  /** Subclass recording outbound frames without a live socket. */
  private static class TestablePrivateStreamingService extends BitgetUtaV3PrivateStreamingService {

    private final List<String> frames = new ArrayList<>();
    private boolean socketOpen;

    TestablePrivateStreamingService(boolean socketOpen) {
      super("ws://127.0.0.1:9", "api-key", "api-secret", "api-passphrase");
      this.socketOpen = socketOpen;
    }

    List<String> frames() {
      return frames;
    }

    boolean containsChannel(String subscriptionId) {
      return channels.containsKey(subscriptionId);
    }

    @Override
    public boolean isSocketOpen() {
      return socketOpen;
    }

    @Override
    public void sendMessage(String message) {
      frames.add(message);
    }
  }

  private static final class StreamOutcome {
    final AtomicReference<Throwable> error = new AtomicReference<>();
    final AtomicBoolean completed = new AtomicBoolean();

    boolean terminated() {
      return error.get() != null || completed.get();
    }
  }

  private static StreamOutcome subscribedStream(Observable<BitgetUtaV3WsNotification> stream) {
    StreamOutcome outcome = new StreamOutcome();
    stream.subscribe(item -> {}, outcome.error::set, () -> outcome.completed.set(true));
    return outcome;
  }

  private static BitgetUtaV3EventNotification loginAck() {
    return BitgetUtaV3EventNotification.builder().event(Event.LOGIN).code("0").build();
  }

  @Test
  void subscriptionBeforeLoginAckDefersFrameUntilAckFlushesIt() {
    TestablePrivateStreamingService service = new TestablePrivateStreamingService(true);
    StreamOutcome outcome = subscribedStream(service.subscribeChannel(null, BTC_TRADE));

    // registered immediately, but the subscribe frame waits for the login ack
    assertThat(service.frames()).isEmpty();
    assertThat(service.containsChannel(BTC_TRADE.toSubscriptionId())).isTrue();

    service.handleEventNotification(loginAck());

    assertThat(service.frames()).hasSize(1);
    assertThat(outcome.terminated()).isFalse();
  }

  @Test
  void subscriptionAfterLoginAckSendsFrameImmediately() {
    TestablePrivateStreamingService service = new TestablePrivateStreamingService(true);
    service.handleEventNotification(loginAck());

    StreamOutcome outcome = subscribedStream(service.subscribeChannel(null, BTC_TRADE));

    assertThat(service.frames()).hasSize(1);
    assertThat(outcome.terminated()).isFalse();
  }

  @Test
  void subscriptionWithoutOpenSocketErrorsAndDoesNotRegister() {
    TestablePrivateStreamingService service = new TestablePrivateStreamingService(false);
    StreamOutcome outcome = subscribedStream(service.subscribeChannel(null, BTC_TRADE));

    assertThat(outcome.error.get()).isInstanceOf(NotConnectedException.class);
    assertThat(service.containsChannel(BTC_TRADE.toSubscriptionId())).isFalse();
    assertThat(service.frames()).isEmpty();
  }
}
