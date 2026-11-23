package info.bitrich.xchangestream.btcmarkets;

import com.fasterxml.jackson.databind.JsonNode;
import info.bitrich.xchangestream.btcmarkets.dto.BTCMarketsWebSocketSubscriptionMessage;
import info.bitrich.xchangestream.service.netty.JsonNettyStreamingService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class BTCMarketsStreamingService extends JsonNettyStreamingService {
  static final String CHANNEL_ORDERBOOK = "orderbook";
  static final String CHANNEL_HEARTBEAT = "heartbeat";
  static final String CHANNEL_TICKER = "tick";
  static final String CHANNEL_TRADE = "trade";

  private static final Logger LOG = LoggerFactory.getLogger(BTCMarketsStreamingService.class);

  public BTCMarketsStreamingService(String apiUrl) {
    super(apiUrl);
  }

  /*
   * Implementation renamed from BTCMarketsWebSocketSubscribeMessage to BTCMarketsWebSocketSubscriptionMessage to look after
   * more than just the OrderBook subscription. This new implementation also incorporates the use of adding subscriptions to an existing one
   * instead of having to resubscribing with all channels and all {@code marketIds} every time the data services calls the subscribe methods.
   */
  private BTCMarketsWebSocketSubscriptionMessage buildSubscribeMessage(
      String channelName, Set<String> marketIds) {

    // Create the first subscription message
    if (!hasActiveSubscriptions()) {
      return BTCMarketsWebSocketSubscriptionMessage.getFirstSubscriptionMessage(
          new ArrayList<String>(marketIds),
          Arrays.asList(channelName, CHANNEL_HEARTBEAT),
          null,
          null,
          null);
    } else {
      return BTCMarketsWebSocketSubscriptionMessage.getAddSubscriptionMessage(
          new ArrayList<String>(marketIds), Arrays.asList(channelName), null, null, null);
    }
  }

  private BTCMarketsWebSocketSubscriptionMessage buildRemoveSubscriptionMessage(
      String channelName, Set<String> marketIds) {

    return BTCMarketsWebSocketSubscriptionMessage.getRemoveSubcriptionMessage(
        new ArrayList<String>(marketIds),
        Arrays.asList(channelName),
        null,
        null,
        null);
  }

  @Override
  protected String getChannelNameFromMessage(JsonNode message) {
    final String messageType = message.get("messageType").asText();
    if (messageType.startsWith(CHANNEL_ORDERBOOK)
        || messageType.startsWith(CHANNEL_TICKER)
        || messageType.startsWith(CHANNEL_TRADE)) {
      return messageType + ":" + message.get("marketId").asText();
    }
    return messageType;
  }

  @Override
  public String getSubscribeMessage(String channelName, Object... args) throws IOException {

    if (CHANNEL_ORDERBOOK.equals(channelName)
        || CHANNEL_TICKER.equals(channelName)
        || CHANNEL_TRADE.equals(channelName)) {

      LOG.debug("Now subscribing to {}:{}", channelName, args);
      Set<String> newMarketIds = new HashSet<String>();
      if (args != null) {
        for (Object marketId : args) {
          newMarketIds.add(marketId.toString());
        }
      }
      LOG.debug("getSubscribeMessage: channel {} / marketIds {}", channelName, newMarketIds);
      return objectMapper.writeValueAsString(buildSubscribeMessage(channelName, newMarketIds));
    } else {
      throw new IllegalArgumentException(
          "Can't create subscribe messsage for channel " + channelName);
    }
  }

  @Override
  public String getSubscriptionUniqueId(String channelName, Object... args) {
    LOG.debug("Returning unique id {}", channelName + ":" + args[0].toString());
    return channelName + ":" + args[0].toString();
  }

  @Override
  public String getUnsubscribeMessage(String subscriptionUniqueId, Object... args)
      throws IOException {
    int separator = subscriptionUniqueId.indexOf(':');
    if (separator < 0) {
      return null;
    }
    String channelName = subscriptionUniqueId.substring(0, separator);
    if (!CHANNEL_ORDERBOOK.equals(channelName)
        && !CHANNEL_TICKER.equals(channelName)
        && !CHANNEL_TRADE.equals(channelName)) {
      return null;
    }
    Set<String> marketIds = new HashSet<>();
    if (args == null || args.length == 0) {
      marketIds.add(subscriptionUniqueId.substring(separator + 1));
    } else {
      for (Object marketId : args) {
        marketIds.add(marketId.toString());
      }
    }
    return objectMapper.writeValueAsString(buildRemoveSubscriptionMessage(channelName, marketIds));
  }

  private Boolean hasActiveSubscriptions() {
    return !channels.isEmpty();
  }
}
