package info.bitrich.xchangestream.coinbase;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * WebSocket transport for Coinbase Advanced Trade private (user) channels.
 *
 * <p>Rides {@link CoinbaseStreamingExchange#USER_ORDER_DATA_WS_URI} and only carries
 * authenticated channels; it is created and connected only when the exchange has usable
 * credentials, so public-only usage never opens this socket. All subscription, generation,
 * reauthentication, and resubscribe behavior is inherited from {@link CoinbaseStreamingService}.
 */
public class CoinbaseUserStreamingService extends CoinbaseStreamingService {

  CoinbaseUserStreamingService(
      String apiUrl,
      Supplier<String> jwtSupplier,
      Duration connectionTimeout,
      Duration retryDuration,
      int idleTimeoutSeconds,
      int maxFramePayloadLength,
      int unauthenticatedPerSecond,
      int authenticatedPerSecond) {
    super(
        apiUrl,
        jwtSupplier,
        connectionTimeout,
        retryDuration,
        idleTimeoutSeconds,
        maxFramePayloadLength,
        unauthenticatedPerSecond,
        authenticatedPerSecond);
  }

  CoinbaseUserStreamingService(
      String apiUrl, Supplier<String> jwtSupplier, int unauthenticatedPerSecond, int authenticatedPerSecond) {
    super(apiUrl, jwtSupplier, unauthenticatedPerSecond, authenticatedPerSecond);
  }
}
