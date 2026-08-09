package info.bitrich.xchangestream.kraken;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import info.bitrich.xchangestream.kraken.config.Config;
import info.bitrich.xchangestream.kraken.dto.response.KrakenDataMessage;
import info.bitrich.xchangestream.kraken.dto.response.KrakenMessage;
import info.bitrich.xchangestream.service.netty.NettyStreamingService;
import info.bitrich.xchangestream.service.netty.WebSocketClientCompressionAllowClientNoContextHandler;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketClientExtensionHandler;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.utils.ArrayUtils;

@Slf4j
public class KrakenStreamingService extends NettyStreamingService<KrakenMessage> {

  /** Base reconnect delay, doubled on each consecutive failure. */
  static final Duration RECONNECT_BASE_DELAY = Duration.ofSeconds(1);

  /** Upper bound for the exponential reconnect backoff. */
  static final Duration RECONNECT_MAX_DELAY = Duration.ofSeconds(30);

  protected final ObjectMapper objectMapper = Config.getInstance().getObjectMapper();

  private final AtomicInteger reconnectAttempts = new AtomicInteger();

  public KrakenStreamingService(String apiUri) {
    super(apiUri, Integer.MAX_VALUE);

    // a successful connection resets the backoff sequence
    subscribeConnectionSuccess().subscribe(e -> reconnectAttempts.set(0));
  }

  /**
   * Schedules a reconnect with bounded exponential backoff: 1s, 2s, 4s, ... capped at 30s. The
   * backoff sequence resets on the first successful connection.
   */
  @Override
  protected void scheduleReconnect() {
    if (!isAutoReconnect()) {
      return;
    }
    long delayMillis = recordReconnectDelay().toMillis();
    log.info("Scheduling reconnection in {} ms", delayMillis);
    Channel channel = getWebSocketChannel();
    if (channel != null && channel.eventLoop() != null) {
      channel
          .eventLoop()
          .schedule(
              () ->
                  connect()
                      .subscribe(
                          () -> log.info("Reconnection complete"),
                          e -> log.error("Reconnection failed: {}", e.getMessage())),
              delayMillis,
              TimeUnit.MILLISECONDS);
    }
  }

  /**
   * Records one reconnect failure and returns the delay for it: the base delay doubled per
   * consecutive failure, bounded at the max. Called once per failed connection attempt.
   */
  Duration recordReconnectDelay() {
    Duration delay = nextReconnectDelay();
    reconnectAttempts.incrementAndGet();
    return delay;
  }

  /**
   * @return the next backoff delay for the current failure count, bounded by {@value
   *     #RECONNECT_MAX_DELAY}
   */
  Duration nextReconnectDelay() {
    int attempts = reconnectAttempts.get();
    long delayMillis = RECONNECT_BASE_DELAY.toMillis() * (1L << Math.min(attempts, 10));
    return Duration.ofMillis(Math.min(delayMillis, RECONNECT_MAX_DELAY.toMillis()));
  }

  /** Test seam: resets the backoff sequence as if the last connection had succeeded. */
  void resetReconnectBackoff() {
    reconnectAttempts.set(0);
  }

  @Override
  protected String getChannelNameFromMessage(KrakenMessage message) throws IOException {
    return message.getChannelId();
  }

  @Override
  public String getSubscribeMessage(String channelName, Object... args) throws IOException {
    CurrencyPair currencyPair = ArrayUtils.getElement(0, args, CurrencyPair.class, null);
    var message = KrakenStreamingAdapters.toSubscribeMessage(channelName, currencyPair);

    return objectMapper.writeValueAsString(message);
  }

  @Override
  protected WebSocketClientExtensionHandler getWebSocketClientExtensionHandler() {
    return WebSocketClientCompressionAllowClientNoContextHandler.INSTANCE;
  }

  @Override
  public String getSubscriptionUniqueId(String channelName, Object... args) {
    CurrencyPair currencyPair = ArrayUtils.getElement(0, args, CurrencyPair.class, null);
    return KrakenStreamingAdapters.toSubscriptionUniqueId(channelName, currencyPair);
  }

  @Override
  public String getUnsubscribeMessage(String subscriptionUniqueId, Object... args)
      throws IOException {
    var message = KrakenStreamingAdapters.toUnsubscribeMessage(subscriptionUniqueId);
    return objectMapper.writeValueAsString(message);
  }

  @Override
  public void messageHandler(String message) {
    log.debug("Received message: {}", message);

    try {
      KrakenMessage krakenMessage = objectMapper.readValue(message, KrakenMessage.class);

      // if there are several data entries split them and process separately
      if (krakenMessage instanceof KrakenDataMessage
          && ((KrakenDataMessage) krakenMessage).getData() != null
          && ((KrakenDataMessage) krakenMessage).getData().size() > 1) {

        KrakenDataMessage krakenDataMessage = (KrakenDataMessage) krakenMessage;

        for (int i = 0; i < krakenDataMessage.getData().size(); i++) {
          var currentDataEntry = krakenDataMessage.getData().get(i);
          var copiedDataMessage =
              krakenDataMessage.toBuilder().data(List.of(currentDataEntry)).build();
          handleMessage(copiedDataMessage);
        }

      } else {
        handleMessage(krakenMessage);
      }

    } catch (JsonProcessingException e) {
      log.error("Error parsing incoming message to JSON: {}", message);
      log.error(e.getMessage(), e);
    }
  }
}
