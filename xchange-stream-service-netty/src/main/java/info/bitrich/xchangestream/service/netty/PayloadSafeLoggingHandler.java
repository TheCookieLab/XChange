package info.bitrich.xchangestream.service.netty;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

/** Logs transport events without rendering frames, HTTP headers, or exception payloads. */
final class PayloadSafeLoggingHandler extends LoggingHandler {

  PayloadSafeLoggingHandler(LogLevel level) {
    super(LoggingHandler.class, level);
  }

  @Override
  protected String format(ChannelHandlerContext ctx, String eventName, Object arg) {
    return format(ctx, eventName);
  }

  @Override
  protected String format(
      ChannelHandlerContext ctx, String eventName, Object firstArg, Object secondArg) {
    return format(ctx, eventName);
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    if (logger.isEnabled(internalLevel)) {
      logger.log(internalLevel, format(ctx, "EXCEPTION"));
    }
    ctx.fireExceptionCaught(cause);
  }
}
