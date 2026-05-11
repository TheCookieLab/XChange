package info.bitrich.xchangestream.deribit;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

class DeribitStreamingServiceTest {

  private final DeribitStreamingService service = new DeribitStreamingService("wss://localhost");

  @Test
  void ignoresErrorResponsesWithoutChannelParams() {
    assertThatCode(
            () ->
                service.messageHandler(
                    """
                    {"jsonrpc":"2.0","id":1,"error":{"code":10000,"message":"authorization failed"}}
                    """))
        .doesNotThrowAnyException();
  }

  @Test
  void ignoresSubscriptionAcknowledgementsWithoutChannelParams() {
    assertThatCode(
            () ->
                service.messageHandler(
                    """
                    {"jsonrpc":"2.0","id":1,"result":["ticker.BTC-PERPETUAL.100ms"]}
                    """))
        .doesNotThrowAnyException();
  }
}
