package info.bitrich.xchangestream.service.netty;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.logging.LogLevel;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.slf4j.LoggerFactory;

public class PayloadSafeLoggingHandlerTest {

  @Test
  public void wireLoggingDoesNotRenderPayloadsOrExceptionCauses() {
    Logger logger = (Logger) LoggerFactory.getLogger(PayloadSafeLoggingHandler.class);
    Level originalLevel = logger.getLevel();
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    logger.setLevel(Level.DEBUG);
    AtomicReference<Throwable> propagated = new AtomicReference<>();
    PayloadSafeLoggingHandler handler = new PayloadSafeLoggingHandler(LogLevel.DEBUG);
    EmbeddedChannel channel =
        new EmbeddedChannel(
            handler,
            new ChannelInboundHandlerAdapter() {
              @Override
              public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                propagated.set(cause);
              }
            });
    try {
      String payload = "synthetic-wire-secret";
      TextWebSocketFrame frame = new TextWebSocketFrame(payload);
      channel.writeOutbound(frame);
      assertThat((Object) channel.readOutbound()).isSameAs(frame);
      assertThat(frame.text()).isEqualTo(payload);
      frame.release();

      ByteBuf bytes = Unpooled.copiedBuffer(payload, StandardCharsets.UTF_8);
      channel.writeInbound(bytes);
      assertThat((Object) channel.readInbound()).isSameAs(bytes);
      assertThat(bytes.toString(StandardCharsets.UTF_8)).isEqualTo(payload);
      bytes.release();

      DefaultFullHttpRequest request =
          new DefaultFullHttpRequest(
              HttpVersion.HTTP_1_1, HttpMethod.GET, "/ws?token=" + payload);
      request.headers().set("Authorization", payload);
      channel.writeOutbound(request);
      assertThat((Object) channel.readOutbound()).isSameAs(request);
      request.release();

      Object opaque =
          new Object() {
            @Override
            public String toString() {
              throw new AssertionError("Wire events must not be rendered");
            }
          };
      assertThat(handler.format(channel.pipeline().context(handler), "CONNECT", opaque, opaque))
          .contains("CONNECT");

      channel.pipeline().fireUserEventTriggered(opaque);
      RuntimeException failure = new RuntimeException(payload);
      channel.pipeline().fireExceptionCaught(failure);
      assertThat(propagated.get()).isSameAs(failure);
      logger.setLevel(Level.OFF);
      RuntimeException quietFailure = new RuntimeException(payload);
      channel.pipeline().fireExceptionCaught(quietFailure);
      assertThat(propagated.get()).isSameAs(quietFailure);
      logger.setLevel(Level.DEBUG);
      assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
          .anyMatch(message -> message.contains("WRITE"))
          .anyMatch(message -> message.contains("READ"))
          .anyMatch(message -> message.contains("EXCEPTION"));
      for (ILoggingEvent event : appender.list) {
        assertThat(event.getFormattedMessage()).doesNotContain(payload, "Authorization", "token=");
        assertThat(event.getArgumentArray()).isNullOrEmpty();
        assertThat(event.getThrowableProxy()).isNull();
      }
    } finally {
      logger.detachAppender(appender);
      logger.setLevel(originalLevel);
      appender.stop();
      channel.finishAndReleaseAll();
    }
  }
}
