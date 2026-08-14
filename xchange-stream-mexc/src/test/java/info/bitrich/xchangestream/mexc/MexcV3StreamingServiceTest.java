package info.bitrich.xchangestream.mexc;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

/** Message routing, keepalive, subscription-cap, and binary-frame delivery tests. */
class MexcV3StreamingServiceTest {

  private static final String CHANNEL = "spot@public.aggre.deals.v3.api.pb@100ms@BTCUSDT";

  /** Service variant that records sent messages and can register subscriptions without a socket. */
  static final class CapturingService extends MexcV3StreamingService {

    final List<String> sent = new ArrayList<>();

    CapturingService() {
      super("wss://wbs-api.mexc.com/ws");
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
    service.messageHandler("{\"id\":1,\"code\":200,\"msg\":\"" + CHANNEL + "\"}");
    assertTrue(service.sent.isEmpty());
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

  @Test
  void subscriptionCapRejectsThirtyFirstChannel() {
    CapturingService service = new CapturingService();
    for (int i = 0; i < MexcV3StreamingService.MAX_SUBSCRIPTIONS_PER_CONNECTION; i++) {
      service.subscribeChannel("channel-" + i).subscribe();
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
  }
}
