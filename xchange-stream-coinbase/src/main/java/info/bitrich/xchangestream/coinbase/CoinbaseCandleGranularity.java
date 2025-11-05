package info.bitrich.xchangestream.coinbase;

import java.time.Duration;

/**
 * Supported candle granularities for the Coinbase Advanced Trade WebSocket candles channel.
 */
public enum CoinbaseCandleGranularity {
  ONE_MINUTE("ONE_MINUTE", Duration.ofMinutes(1)),
  FIVE_MINUTE("FIVE_MINUTE", Duration.ofMinutes(5)),
  FIFTEEN_MINUTE("FIFTEEN_MINUTE", Duration.ofMinutes(15)),
  THIRTY_MINUTE("THIRTY_MINUTE", Duration.ofMinutes(30)),
  ONE_HOUR("ONE_HOUR", Duration.ofHours(1)),
  TWO_HOUR("TWO_HOUR", Duration.ofHours(2)),
  SIX_HOUR("SIX_HOUR", Duration.ofHours(6)),
  ONE_DAY("ONE_DAY", Duration.ofDays(1));

  private final String apiValue;
  private final Duration duration;

  CoinbaseCandleGranularity(String apiValue, Duration duration) {
    this.apiValue = apiValue;
    this.duration = duration;
  }

  public String apiValue() {
    return apiValue;
  }

  public Duration duration() {
    return duration;
  }

  public static CoinbaseCandleGranularity fromDuration(Duration duration) {
    if (duration == null) {
      return null;
    }
    return fromSeconds(duration.getSeconds());
  }

  public static CoinbaseCandleGranularity fromSeconds(long seconds) {
    for (CoinbaseCandleGranularity value : values()) {
      if (value.duration.getSeconds() == seconds) {
        return value;
      }
    }
    return null;
  }
}
