package info.bitrich.xchangestream.coinbase;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Enumeration of Coinbase Advanced Trade WebSocket channels supported by the streaming module.
 */
public enum CoinbaseChannel {
  TICKER("ticker"),
  TICKER_BATCH("ticker_batch"),
  MARKET_TRADES("market_trades"),
  CANDLES("candles"),
  LEVEL2("level2"),
  LEVEL2_BATCH("level2_batch"),
  L2_DATA("l2_data"), // Alternative channel name to try
  STATUS("status"),
  HEARTBEATS("heartbeats"),
  USER("user"),
  FUTURES_BALANCE_SUMMARY("futures_balance_summary");

  private static final Set<String> AUTHENTICATED_CHANNEL_NAMES =
      Collections.unmodifiableSet(
          new HashSet<>(Arrays.asList(USER.channelName, FUTURES_BALANCE_SUMMARY.channelName)));

  private final String channelName;

  CoinbaseChannel(String channelName) {
    this.channelName = channelName;
  }

  public String channelName() {
    return channelName;
  }

  public boolean requiresAuthentication() {
    return AUTHENTICATED_CHANNEL_NAMES.contains(channelName);
  }

  public static CoinbaseChannel fromName(String name) {
    for (CoinbaseChannel channel : values()) {
      if (channel.channelName.equalsIgnoreCase(name)) {
        return channel;
      }
    }
    throw new IllegalArgumentException("Unknown Coinbase channel: " + name);
  }
}

