package info.bitrich.xchangestream.kraken;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Bounded exponential backoff for automatic reconnects. */
class KrakenStreamingServiceReconnectTest {

  private static final class TestService extends KrakenStreamingService {

    TestService() {
      super("wss://ws.kraken.com/v2");
    }

    List<Duration> recordedDelays = new ArrayList<>();

    @Override
    protected void scheduleReconnect() {
      recordedDelays.add(recordReconnectDelay());
    }

    Duration delayAfter(int consecutiveFailures) {
      while (consecutiveFailures-- > 0) {
        scheduleReconnect();
      }
      return recordedDelays.get(recordedDelays.size() - 1);
    }
  }

  @Test
  void backoff_grows_exponentially() {
    TestService service = new TestService();

    assertThat(service.delayAfter(1)).isEqualTo(Duration.ofSeconds(1));
    assertThat(service.delayAfter(1)).isEqualTo(Duration.ofSeconds(2));
    assertThat(service.delayAfter(1)).isEqualTo(Duration.ofSeconds(4));
    assertThat(service.delayAfter(1)).isEqualTo(Duration.ofSeconds(8));
  }

  @Test
  void backoff_is_bounded_at_max_delay() {
    TestService service = new TestService();

    Duration delay = null;
    for (int i = 0; i < 20; i++) {
      delay = service.delayAfter(1);
    }
    assertThat(delay).isEqualTo(KrakenStreamingService.RECONNECT_MAX_DELAY);
  }

  @Test
  void successful_connection_resets_backoff() {
    TestService service = new TestService();
    service.delayAfter(5);
    service.delayAfter(1);
    Duration beforeReset = service.recordedDelays.get(service.recordedDelays.size() - 1);
    assertThat(beforeReset).isGreaterThan(KrakenStreamingService.RECONNECT_BASE_DELAY);

    service.resetReconnectBackoff();

    assertThat(service.delayAfter(1)).isEqualTo(KrakenStreamingService.RECONNECT_BASE_DELAY);
  }
}
