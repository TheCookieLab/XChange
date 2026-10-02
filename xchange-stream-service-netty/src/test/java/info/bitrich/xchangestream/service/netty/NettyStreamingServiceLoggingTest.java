package info.bitrich.xchangestream.service.netty;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import java.lang.reflect.Field;
import org.junit.Test;
import org.slf4j.LoggerFactory;

public class NettyStreamingServiceLoggingTest {

  @Test
  public void deribitClientCredentialsAreSentWithoutLogging() throws Exception {
    assertPayloadSafe(
        "{\"jsonrpc\":\"2.0\",\"method\":\"public/auth\",\"params\":{"
            + "\"grant_type\":\"client_credentials\",\"client_id\":\"synthetic-key\","
            + "\"client_secret\":\"synthetic-secret\"}}");
  }

  @Test
  public void bitgetLoginCredentialsAreSentWithoutLogging() throws Exception {
    assertPayloadSafe(
        "{\"op\":\"login\",\"args\":[{\"apiKey\":\"synthetic-key\","
            + "\"passphrase\":\"synthetic-passphrase\",\"timestamp\":\"123\","
            + "\"sign\":\"synthetic-signature\"}]}");
  }

  @Test
  public void cryptoComSignedRequestsAreSentWithoutLogging() throws Exception {
    assertPayloadSafe(
        "{\"id\":1,\"method\":\"public/auth\",\"api_key\":\"synthetic-key\","
            + "\"sig\":\"synthetic-signature\",\"nonce\":123}");
    assertPayloadSafe(
        "{\"id\":2,\"method\":\"private/get-account-summary\","
            + "\"api_key\":\"synthetic-key\",\"sig\":\"synthetic-signature\",\"nonce\":124}");
  }

  @Test
  public void binanceSessionCredentialsAreSentWithoutLogging() throws Exception {
    assertPayloadSafe(
        "{\"id\":1,\"method\":\"session.logon\",\"params\":{"
            + "\"apiKey\":\"synthetic-key\",\"signature\":\"synthetic-signature\","
            + "\"timestamp\":123}}");
  }

  @Test
  public void gateioSignedSubscriptionIsSentWithoutLogging() throws Exception {
    assertPayloadSafe(
        "{\"time\":123,\"channel\":\"spot.orders\",\"event\":\"subscribe\","
            + "\"payload\":[\"!all\"],\"auth\":{\"method\":\"api_key\","
            + "\"key\":\"synthetic-key\",\"sign\":\"synthetic-signature\"}}");
  }

  @Test
  public void gateioSignedLoginIsSentWithoutLogging() throws Exception {
    assertPayloadSafe(
        "{\"time\":123,\"channel\":\"spot.login\",\"event\":\"api\",\"payload\":{"
            + "\"api_key\":\"synthetic-key\",\"signature\":\"synthetic-signature\","
            + "\"timestamp\":\"123\",\"req_id\":\"1\"}}");
  }

  @Test
  public void opaqueAndMalformedMessagesAreSentWithoutLogging() throws Exception {
    assertPayloadSafe("synthetic-secret");
    assertPayloadSafe("{\"unrecognized\":\"synthetic-secret\"");
  }

  @Test
  public void rejectedMessagesAreNotLogged() throws Exception {
    assertPayloadSafe("synthetic-secret", false, true);
    assertPayloadSafe("synthetic-secret", true, false);
  }

  @Test
  public void nullMessagesDoNotCreateFrames() throws Exception {
    assertPayloadSafe(null);
  }

  private void assertPayloadSafe(String payload) throws Exception {
    assertPayloadSafe(payload, true, true);
  }

  private void assertPayloadSafe(String payload, boolean open, boolean writable) throws Exception {
    TestStreamingService service = new TestStreamingService();
    EmbeddedChannel channel = new EmbeddedChannel();
    Field channelField = NettyStreamingService.class.getDeclaredField("webSocketChannel");
    channelField.setAccessible(true);
    channelField.set(service, channel);
    if (!open) {
      channel.close();
    } else if (!writable) {
      channel.unsafe().outboundBuffer().setUserDefinedWritability(1, false);
    }
    Logger logger = (Logger) LoggerFactory.getLogger(TestStreamingService.class);
    Level originalLevel = logger.getLevel();
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    logger.setLevel(Level.DEBUG);
    try {
      service.sendMessage(payload);
      TextWebSocketFrame frame = channel.readOutbound();
      if (payload != null && open && writable) {
        try {
          assertThat(frame.text()).isEqualTo(payload);
        } finally {
          frame.release();
        }
      } else {
        assertThat(frame).isNull();
      }
      assertThat(appender.list).isNotEmpty();
      for (ILoggingEvent event : appender.list) {
        assertThat(event.getFormattedMessage()).doesNotContain("synthetic-");
        if (payload != null) {
          assertThat(event.getFormattedMessage()).doesNotContain(payload);
        }
        assertThat(event.getArgumentArray()).isNullOrEmpty();
      }
    } finally {
      logger.detachAppender(appender);
      logger.setLevel(originalLevel);
      appender.stop();
      channel.finishAndReleaseAll();
    }
  }

  private static class TestStreamingService extends NettyStreamingService<String> {
    TestStreamingService() {
      super("wss://example.invalid");
    }

    @Override
    public void messageHandler(String message) {}

    @Override
    protected String getChannelNameFromMessage(String message) {
      return "test";
    }

    @Override
    public String getSubscribeMessage(String channelName, Object... args) {
      return "subscribe";
    }

    @Override
    public String getUnsubscribeMessage(String channelName, Object... args) {
      return "unsubscribe";
    }
  }
}
