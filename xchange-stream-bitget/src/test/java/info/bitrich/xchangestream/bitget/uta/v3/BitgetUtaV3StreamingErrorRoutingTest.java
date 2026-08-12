package info.bitrich.xchangestream.bitget.uta.v3;

import static org.assertj.core.api.Assertions.assertThat;

import info.bitrich.xchangestream.bitget.uta.v3.dto.BitgetUtaV3Channel;
import info.bitrich.xchangestream.bitget.uta.v3.dto.BitgetUtaV3EventNotification;
import info.bitrich.xchangestream.bitget.uta.v3.dto.BitgetUtaV3EventNotification.Event;
import info.bitrich.xchangestream.bitget.uta.v3.dto.BitgetUtaV3InstType;
import info.bitrich.xchangestream.bitget.uta.v3.dto.BitgetUtaV3WsNotification;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.ObservableEmitter;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.knowm.xchange.exceptions.ExchangeException;

/**
 * Error routing of UTA v3 acknowledgement frames: subscription failures must terminate the affected
 * channel stream and a rejected private login must terminate every private channel stream, so
 * callers observe the rejection instead of hanging on acknowledgements that never arrive.
 */
class BitgetUtaV3StreamingErrorRoutingTest {

  private static final BitgetUtaV3Channel BTC_TRADE =
      BitgetUtaV3Channel.builder()
          .instType(BitgetUtaV3InstType.SPOT)
          .topic("trade")
          .symbol("BTCUSDT")
          .build();

  private interface ChannelRegistrar {
    void register(String subscriptionId, ObservableEmitter<BitgetUtaV3WsNotification> emitter);
  }

  /** Subclass exposing channel registration without a live socket. */
  private static class TestableStreamingService extends BitgetUtaV3StreamingService
      implements ChannelRegistrar {

    TestableStreamingService() {
      super("ws://127.0.0.1:9");
    }

    @Override
    public void register(String subscriptionId, ObservableEmitter<BitgetUtaV3WsNotification> emitter) {
      channels.put(subscriptionId, new Subscription(emitter, subscriptionId, new Object[0]));
    }
  }

  private static class TestablePrivateStreamingService extends BitgetUtaV3PrivateStreamingService
      implements ChannelRegistrar {

    TestablePrivateStreamingService() {
      super("ws://127.0.0.1:9", "api-key", "api-secret", "api-passphrase");
    }

    @Override
    public void register(String subscriptionId, ObservableEmitter<BitgetUtaV3WsNotification> emitter) {
      channels.put(subscriptionId, new Subscription(emitter, subscriptionId, new Object[0]));
    }
  }

  private static final class StreamOutcome {
    final AtomicReference<Throwable> error = new AtomicReference<>();
    final AtomicBoolean completed = new AtomicBoolean();

    boolean terminated() {
      return error.get() != null || completed.get();
    }
  }

  private static StreamOutcome subscribedStream(String subscriptionId, ChannelRegistrar registrar) {
    AtomicReference<ObservableEmitter<BitgetUtaV3WsNotification>> emitter = new AtomicReference<>();
    StreamOutcome outcome = new StreamOutcome();
    Observable.<BitgetUtaV3WsNotification>create(emitter::set)
        .subscribe(item -> {}, outcome.error::set, () -> outcome.completed.set(true));
    registrar.register(subscriptionId, emitter.get());
    return outcome;
  }

  @Test
  void subscriptionErrorAckTerminatesAffectedChannelStream() {
    TestableStreamingService service = new TestableStreamingService();
    StreamOutcome outcome =
        subscribedStream(BTC_TRADE.toSubscriptionId(), service::register);

    service.handleEventNotification(
        BitgetUtaV3EventNotification.builder()
            .event(Event.ERROR)
            .code("30001")
            .message("invalid symbol")
            .channel(BTC_TRADE)
            .build());

    assertThat(outcome.error.get())
        .isInstanceOf(ExchangeException.class)
        .hasMessageContaining("invalid symbol");
  }

  @Test
  void subscriptionErrorAckWithoutMatchingChannelLeavesStreamRunning() {
    TestableStreamingService service = new TestableStreamingService();
    StreamOutcome outcome =
        subscribedStream(BTC_TRADE.toSubscriptionId(), service::register);

    service.handleEventNotification(
        BitgetUtaV3EventNotification.builder()
            .event(Event.ERROR)
            .code("30001")
            .message("invalid symbol")
            .channel(
                BitgetUtaV3Channel.builder()
                    .instType(BitgetUtaV3InstType.SPOT)
                    .topic("trade")
                    .symbol("ETHUSDT")
                    .build())
            .build());

    assertThat(outcome.terminated()).isFalse();
  }

  @Test
  void successfulAckLeavesChannelStreamRunning() {
    TestableStreamingService service = new TestableStreamingService();
    StreamOutcome outcome =
        subscribedStream(BTC_TRADE.toSubscriptionId(), service::register);

    service.handleEventNotification(
        BitgetUtaV3EventNotification.builder()
            .event(Event.SUBSCRIBE)
            .code("0")
            .channel(BTC_TRADE)
            .build());

    assertThat(outcome.terminated()).isFalse();
  }

  @Test
  void rejectedLoginTerminatesEveryPrivateChannelStream() {
    TestablePrivateStreamingService service = new TestablePrivateStreamingService();
    StreamOutcome first =
        subscribedStream(BTC_TRADE.toSubscriptionId(), service::register);
    StreamOutcome second =
        subscribedStream(
            BitgetUtaV3Channel.builder()
                .instType(BitgetUtaV3InstType.SPOT)
                .topic("orders")
                .symbol("BTCUSDT")
                .build()
                .toSubscriptionId(),
            service::register);

    service.handleEventNotification(
        BitgetUtaV3EventNotification.builder()
            .event(Event.LOGIN)
            .code("40001")
            .message("invalid signature")
            .build());

    assertThat(first.error.get())
        .isInstanceOf(ExchangeException.class)
        .hasMessageContaining("login rejected");
    assertThat(second.error.get()).isInstanceOf(ExchangeException.class);
  }
}
