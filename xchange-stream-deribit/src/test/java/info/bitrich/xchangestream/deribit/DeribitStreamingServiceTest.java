package info.bitrich.xchangestream.deribit;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import info.bitrich.xchangestream.deribit.dto.response.DeribitWsNotification;
import org.junit.jupiter.api.Test;

class DeribitStreamingServiceTest {

  private final DeribitStreamingService service = new DeribitStreamingService("wss://localhost");

  @Test
  void ignoresErrorResponsesWithoutChannelParams() {
    DeribitStreamingService serviceSpy = spy(service);

    assertThatCode(
            () ->
                serviceSpy.messageHandler(
                    """
                    {"jsonrpc":"2.0","id":1,"error":{"code":10000,"message":"authorization failed"}}
                    """))
        .doesNotThrowAnyException();

    verify(serviceSpy, never()).handleMessage(any(DeribitWsNotification.class));
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
