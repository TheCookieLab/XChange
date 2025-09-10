package info.bitrich.xchangestream.kraken;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import info.bitrich.xchangestream.kraken.config.Config;
import info.bitrich.xchangestream.kraken.dto.response.KrakenMessage;
import info.bitrich.xchangestream.service.netty.NettyStreamingService;
import info.bitrich.xchangestream.service.netty.WebSocketClientCompressionAllowClientNoContextHandler;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketClientExtensionHandler;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.knowm.xchange.currency.CurrencyPair;

@Slf4j
public class KrakenStreamingService extends NettyStreamingService<KrakenMessage> {

  protected final ObjectMapper objectMapper = Config.getInstance().getObjectMapper();

  public KrakenStreamingService(String apiUri) {
    super(apiUri, Integer.MAX_VALUE);
  }

  @Override
  protected String getChannelNameFromMessage(KrakenMessage message) throws IOException {
    return message.getChannelId();
  }

  @Override
  public String getSubscribeMessage(String channelName, Object... args) throws IOException {
    var message = KrakenStreamingAdapters.toSubscribeMessage(channelName, (CurrencyPair) args[0]);
    return objectMapper.writeValueAsString(message);
  }

  @Override
  protected WebSocketClientExtensionHandler getWebSocketClientExtensionHandler() {
    return WebSocketClientCompressionAllowClientNoContextHandler.INSTANCE;
  }


  @Override
  public String getSubscriptionUniqueId(String channelName, Object... args) {
    return KrakenStreamingAdapters.toSubscriptionUniqueId(channelName, (CurrencyPair) args[0]);
  }

  @Override
  public String getUnsubscribeMessage(String subscriptionUniqueId, Object... args) throws IOException {
    var message = KrakenStreamingAdapters.toUnsubscribeMessage(subscriptionUniqueId);
    return objectMapper.writeValueAsString(message);
  }

  @Override
  public void messageHandler(String message) {
    log.debug("Received message: {}", message);

    try {
      KrakenMessage krakenMessage = objectMapper.readValue(message, KrakenMessage.class);

      handleMessage(krakenMessage);

    } catch (JsonProcessingException e) {
      log.error("Error parsing incoming message to JSON: {}", message);
      log.error(e.getMessage(), e);
    }

  }

}
