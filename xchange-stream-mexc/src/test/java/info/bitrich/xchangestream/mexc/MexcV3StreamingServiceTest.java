package info.bitrich.xchangestream.mexc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.InvalidProtocolBufferException;
import com.mxc.push.common.protobuf.PublicAggreDealsV3Api;
import com.mxc.push.common.protobuf.PublicAggreDealsV3ApiItem;
import com.mxc.push.common.protobuf.PushDataV3ApiWrapper;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.observers.TestObserver;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.exceptions.ExchangeSecurityException;

/** Message routing, keepalive, subscription-cap, and binary-frame delivery tests. */
class MexcV3StreamingServiceTest {

  private static final String CHANNEL = "spot@public.aggre.deals.v3.api.pb@100ms@BTCUSDT";

  /** Service variant that records sent messages and can register subscriptions without a socket. */
  static final class CapturingService extends MexcV3StreamingService {

    final List<String> sent = new ArrayList<>();

    CapturingService() {
      this("wss://wbs-api.mexc.com/ws");
    }

    CapturingService(String uri) {
      super(uri);
    }

    @Override
    public void sendMessage(String message) {
      sent.add(message);
    }

    /** Registers a subscription backed by the returned observable's emitter (no socket needed). */
    Observable<String> registerSubscription(String channel) {
      return Observable.create(
          e -> channels.put(channel, new Subscription(e, channel, new Object[0])));
    }

    int channelCount() {
      return channels.size();
    }
  }

  private static PushDataV3ApiWrapper dealsWrapper() {
    PublicAggreDealsV3ApiItem item =
        PublicAggreDealsV3ApiItem.newBuilder()
            .setTradeId("42")
            .setPrice("1.23")
            .setQuantity("4.5")
            .setTradeType(1)
            .setTime(1_712_345_678_901L)
            .build();
    PublicAggreDealsV3Api deals = PublicAggreDealsV3Api.newBuilder().addDeals(item).build();
    return PushDataV3ApiWrapper.newBuilder()
        .setChannel(CHANNEL)
        .setSymbol("BTCUSDT")
        .setCreateTime(1_712_345_678_902L)
        .setPublicAggreDeals(deals)
        .build();
  }

  @Test
  void subscribeMessageUsesMexcCommandEnvelope() {
    CapturingService service = new CapturingService();
    assertEquals(
        "{\"method\":\"SUBSCRIPTION\",\"params\":[\"" + CHANNEL + "\"]}",
        service.getSubscribeMessage(CHANNEL));
  }

  @Test
  void unsubscribeMessageUsesMexcCommandEnvelope() {
    CapturingService service = new CapturingService();
    assertEquals(
        "{\"method\":\"UNSUBSCRIPTION\",\"params\":[\"" + CHANNEL + "\"]}",
        service.getUnsubscribeMessage(CHANNEL));
  }

  @Test
  void channelNameComesFromPushEnvelope() throws Exception {
    CapturingService service = new CapturingService();
    assertEquals(CHANNEL, service.getChannelNameFromMessage(MexcV3ProtoCodec.toJson(dealsWrapper())));
    assertEquals("", service.getChannelNameFromMessage("{\"id\":1,\"code\":200}"));
  }

  @Test
  void commandAckIsIgnoredWithoutRoutingOrReply() {
    CapturingService service = new CapturingService();
    // MEXC documents code 0 as command success; HTTP-style 200 is accepted too.
    service.messageHandler("{\"id\":1,\"code\":0,\"msg\":\"" + CHANNEL + "\"}");
    service.messageHandler("{\"id\":2,\"code\":200,\"msg\":\"" + CHANNEL + "\"}");
    assertTrue(service.sent.isEmpty());
  }

  @Test
  void zeroCodeAckDoesNotFailTheChannel() throws Exception {
    CapturingService service = new CapturingService();
    forceOpenChannel(service);
    TestObserver<String> observer = service.subscribeChannel(CHANNEL).test();

    // A code-0 acknowledgement confirms the subscription; it must not error the channel or
    // remove it, otherwise every successful subscription would terminate its observable.
    service.messageHandler("{\"id\":1,\"code\":0,\"msg\":\"" + CHANNEL + "\"}");

    observer.assertNoErrors().assertValueCount(0);
    assertEquals(1, service.channelCount());
    service.handleBinaryPush(dealsWrapper().toByteArray());
    observer.assertValueCount(1);
  }

  @Test
  void logSafeUriMasksListenKeyQueryValue() throws Exception {
    MexcV3StreamingService service =
        new MexcV3StreamingService(
            "wss://wbs-api.mexc.com/ws?listenKey=top-secret-listen-key&extra=1");
    assertEquals(
        new URI("wss://wbs-api.mexc.com/ws?listenKey=REDACTED&extra=1"),
        service.getLogSafeUri(),
        "the listen key must never be logged");
    // A public (keyless) URI is logged as-is.
    MexcV3StreamingService publicService = new MexcV3StreamingService("wss://wbs-api.mexc.com/ws");
    assertEquals(new URI("wss://wbs-api.mexc.com/ws"), publicService.getLogSafeUri());
  }

  @Test
  void serverPingIsAnsweredWithPong() {
    CapturingService service = new CapturingService();
    service.messageHandler("{\"method\":\"PING\"}");
    assertEquals(List.of("{\"method\":\"PONG\"}"), service.sent);
  }

  @Test
  void textPushWithChannelIsRouted() throws InvalidProtocolBufferException {
    CapturingService service = new CapturingService();
    TestObserver<String> observer = service.registerSubscription(CHANNEL).test();
    String json = MexcV3ProtoCodec.toJson(dealsWrapper());
    service.messageHandler(json);
    observer.assertValueCount(1).assertValue(json);
  }

  @Test
  void binaryPushIsRoutedToMatchingChannel() {
    CapturingService service = new CapturingService();
    TestObserver<String> observer = service.registerSubscription(CHANNEL).test();
    service.handleBinaryPush(dealsWrapper().toByteArray());
    observer
        .assertValueCount(1)
        .assertValue(json -> json.contains("\"channel\":\"" + CHANNEL + "\""))
        .assertValue(json -> json.contains("\"publicAggreDeals\""));
  }

  @Test
  void binaryPushWithoutSubscriberIsDroppedSilently() {
    CapturingService service = new CapturingService();
    service.handleBinaryPush(dealsWrapper().toByteArray());
    service.handleBinaryPush(new byte[] {9, 9, 9});
    assertTrue(service.sent.isEmpty());
  }

  @Test
  void binaryFrameOnEmbeddedChannelDeliversCanonicalJson() {
    CapturingService service = new CapturingService();
    TestObserver<String> observer = service.registerSubscription(CHANNEL).test();

    WebSocketClientHandshaker handshaker =
        WebSocketClientHandshakerFactory.newHandshaker(
            URI.create("wss://wbs-api.mexc.com/ws"),
            WebSocketVersion.V13,
            null,
            true,
            new DefaultHttpHeaders(),
            65536);
    EmbeddedChannel channel =
        new EmbeddedChannel(service.getWebSocketClientHandler(handshaker, msg -> {}));
    try {
      channel.writeInbound(new BinaryWebSocketFrame(Unpooled.wrappedBuffer(dealsWrapper().toByteArray())));
      observer
          .assertValueCount(1)
          .assertValue(
              json -> {
                try {
                  return MexcV3ProtoCodec.fromJson(json).getBodyCase()
                      == PushDataV3ApiWrapper.BodyCase.PUBLICAGGREDEALS;
                } catch (InvalidProtocolBufferException e) {
                  return false;
                }
              });
    } finally {
      channel.finishAndReleaseAll();
    }
  }

  /**
   * Fakes an open socket for {@code subscribeChannel}: the base service registers the
   * subscription and emits into it only while {@code webSocketChannel} is open, and the field has
   * no setter, so the test pokes it with a channel that is always open.
   */
  static void forceOpenChannel(CapturingService service) throws Exception {
    java.lang.reflect.Field channelField =
        info.bitrich.xchangestream.service.netty.NettyStreamingService.class.getDeclaredField(
            "webSocketChannel");
    channelField.setAccessible(true);
    channelField.set(service, new EmbeddedChannel());
  }

  @Test
  void repeatedSubscribeChannelSharesOneSubscriptionForTheChannel() throws Exception {
    CapturingService service = new CapturingService();
    forceOpenChannel(service);

    Observable<String> first = service.subscribeChannel(CHANNEL);
    Observable<String> second = service.subscribeChannel(CHANNEL);

    assertSame(first, second);
    first.test();
    second.test();
    assertEquals(1, service.channelCount());
    assertEquals(
        1,
        service.sent.stream().filter(message -> message.contains("SUBSCRIPTION")).count());
  }

  @Test
  void secondConsumerReceivesPushesFromSharedSubscription() throws Exception {
    CapturingService service = new CapturingService();
    forceOpenChannel(service);

    TestObserver<String> first = service.subscribeChannel(CHANNEL).test();
    TestObserver<String> second = service.subscribeChannel(CHANNEL).test();

    service.handleBinaryPush(dealsWrapper().toByteArray());

    first
        .assertValueCount(1)
        .assertValue(json -> json.contains("\"channel\":\"" + CHANNEL + "\""));
    second
        .assertValueCount(1)
        .assertValue(json -> json.contains("\"channel\":\"" + CHANNEL + "\""));
  }

  @Test
  void lastConsumerDisposalClearsCacheAndAllowsCleanResubscribe() throws Exception {
    CapturingService service = new CapturingService();
    forceOpenChannel(service);

    Observable<String> first = service.subscribeChannel(CHANNEL);
    TestObserver<String> observer = first.test();
    assertEquals(1, service.channelCount());

    observer.dispose();
    assertEquals(0, service.channelCount());

    Observable<String> resubscribed = service.subscribeChannel(CHANNEL);
    assertNotSame(first, resubscribed);
    resubscribed.test();
    assertEquals(1, service.channelCount());
  }

  @Test
  void privateChannelWithoutListenKeyFailsImmediatelyWithSecurityException() {
    CapturingService service = new CapturingService();

    service
        .subscribeChannel("spot@private.orders.v3.api.pb")
        .test()
        .assertError(ExchangeSecurityException.class);
    assertEquals(0, service.channelCount());
  }

  @Test
  void privateChannelWithListenKeyIsNotRejected() throws Exception {
    CapturingService service = new CapturingService("wss://wbs-api.mexc.com/ws?listenKey=abc");
    forceOpenChannel(service);

    service.subscribeChannel("spot@private.orders.v3.api.pb").test().assertNoErrors();
    assertEquals(1, service.channelCount());
  }

  @Test
  void subscriptionCapRejectsThirtyFirstChannel() throws Exception {
    CapturingService service = new CapturingService();
    forceOpenChannel(service);
    Observable<String> channelZero = null;
    io.reactivex.rxjava3.observers.TestObserver<String> channelZeroObserver = null;
    for (int i = 0; i < MexcV3StreamingService.MAX_SUBSCRIPTIONS_PER_CONNECTION; i++) {
      Observable<String> channel = service.subscribeChannel("channel-" + i);
      if (i == 0) {
        channelZero = channel;
        channelZeroObserver = channel.test();
      } else {
        channel.subscribe();
      }
    }
    assertEquals(MexcV3StreamingService.MAX_SUBSCRIPTIONS_PER_CONNECTION, service.channelCount());
    service
        .subscribeChannel("channel-overflow")
        .test()
        .assertError(
            t ->
                t instanceof ExchangeException
                    && t.getMessage().contains("at most 30")
                    && t.getMessage().contains("reconnect"));
    assertEquals(MexcV3StreamingService.MAX_SUBSCRIPTIONS_PER_CONNECTION, service.channelCount());

    // The cap governs new wire subscriptions; an additional observer of an already-subscribed
    // channel is still served from the shared cache without registering a new subscription.
    assertSame(channelZero, service.subscribeChannel("channel-0"));
    io.reactivex.rxjava3.observers.TestObserver<String> channelZeroSecond =
        service.subscribeChannel("channel-0").test();
    assertEquals(MexcV3StreamingService.MAX_SUBSCRIPTIONS_PER_CONNECTION, service.channelCount());

    // Releasing the last observers of a channel frees its slot: the overflow channel then
    // subscribes on first observer, because a slot is acquired at subscribe time, not at
    // observable creation.
    channelZeroObserver.dispose();
    channelZeroSecond.dispose();
    assertEquals(MexcV3StreamingService.MAX_SUBSCRIPTIONS_PER_CONNECTION - 1, service.channelCount());
    service.subscribeChannel("channel-overflow").test().assertNoErrors();
    assertEquals(MexcV3StreamingService.MAX_SUBSCRIPTIONS_PER_CONNECTION, service.channelCount());
  }

  @Test
  void unsubscribedObservablesDoNotReserveCapSlots() throws Exception {
    CapturingService service = new CapturingService();
    forceOpenChannel(service);
    for (int i = 0; i < MexcV3StreamingService.MAX_SUBSCRIPTIONS_PER_CONNECTION; i++) {
      // Handing out the observable must not send wire messages or reserve a cap slot; the
      // wire subscription and its slot are acquired only when the first consumer subscribes.
      assertNotNull(service.subscribeChannel("channel-" + i));
    }
    assertTrue(service.sent.isEmpty(), "creating observables must not send wire messages");

    // The 31st distinct channel is accepted and subscribes normally: none of the 30 created
    // observables was ever subscribed, so none holds a slot.
    service.subscribeChannel("channel-overflow").test().assertNoErrors();
    assertEquals(1, service.channelCount());

    // Cache hits still share one observable per channel.
    assertSame(service.subscribeChannel("channel-0"), service.subscribeChannel("channel-0"));
  }

  @Test
  void rejectedSubscriptionAckFailsTheAffectedChannel() throws Exception {
    CapturingService service = new CapturingService();
    forceOpenChannel(service);
    TestObserver<String> observer = service.subscribeChannel(CHANNEL).test();

    service.messageHandler("{\"id\":1,\"code\":400,\"msg\":\"" + CHANNEL + "\"}");

    // The subscriber gets an immediate error signal instead of waiting forever for events that
    // cannot arrive, and the failed channel is dropped from both the wire registry and the
    // shared cache so a retry re-subscribes from scratch.
    observer.assertError(
        t -> t instanceof ExchangeException && t.getMessage().contains("code 400"));
    assertEquals(0, service.channelCount());
    service.subscribeChannel(CHANNEL).test().assertNoErrors();
    assertEquals(1, service.channelCount());
    assertTrue(service.sent.stream().anyMatch(m -> m.contains("SUBSCRIPTION")));
  }
}
