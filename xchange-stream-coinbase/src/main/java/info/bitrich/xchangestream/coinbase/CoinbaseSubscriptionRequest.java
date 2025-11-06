package info.bitrich.xchangestream.coinbase;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable description of a Coinbase websocket subscription request.
 */
class CoinbaseSubscriptionRequest {

  private final CoinbaseChannel channel;
  private final List<String> productIds;
  private final Map<String, Object> channelArgs;

  CoinbaseSubscriptionRequest(
      CoinbaseChannel channel, List<String> productIds, Map<String, Object> channelArgs) {
    this.channel = Objects.requireNonNull(channel, "channel");
    if (productIds == null || productIds.isEmpty()) {
      this.productIds = Collections.emptyList();
    } else {
      this.productIds =
          Collections.unmodifiableList(new java.util.ArrayList<>(productIds));
    }
    if (channelArgs == null || channelArgs.isEmpty()) {
      this.channelArgs = Collections.emptyMap();
    } else {
      this.channelArgs = Collections.unmodifiableMap(new LinkedHashMap<>(channelArgs));
    }
  }

  CoinbaseChannel getChannel() {
    return channel;
  }

  List<String> getProductIds() {
    return productIds;
  }

  Map<String, Object> getChannelArgs() {
    return channelArgs;
  }
}
