package info.bitrich.xchangestream.coinbase;

import com.fasterxml.jackson.databind.JsonNode;
import io.reactivex.rxjava3.core.Observable;

/**
 * Shared test utilities for Coinbase streaming tests.
 */
public final class CoinbaseStreamingTestUtils {

  private CoinbaseStreamingTestUtils() {
    // Utility class, prevent instantiation
  }

  /**
   * A stub implementation of {@link CoinbaseStreamingService} that returns predefined responses
   * and captures subscription requests for testing.
   */
  public static final class StubStreamingService extends CoinbaseStreamingService {
    private final Observable<JsonNode> response;
    private CoinbaseSubscriptionRequest lastRequest;

    public StubStreamingService(Observable<JsonNode> response) {
      super("wss://example.com", () -> null, 8, 750);
      this.response = response;
    }

    @Override
    protected Observable<JsonNode> observeChannel(CoinbaseSubscriptionRequest request) {
      this.lastRequest = request;
      return response;
    }

    public CoinbaseSubscriptionRequest lastRequest() {
      return lastRequest;
    }
  }
}
