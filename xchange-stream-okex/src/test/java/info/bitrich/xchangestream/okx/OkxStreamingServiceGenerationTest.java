package info.bitrich.xchangestream.okx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import org.junit.Before;
import org.junit.Test;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.okx.OkxExchange;

/**
 * Offline tests for the per-service connection generation and the generation-guarded message
 * boundary introduced for the unified three-socket lifecycle.
 */
public class OkxStreamingServiceGenerationTest {

  private ExchangeSpecification spec;

  @Before
  public void setUp() {
    spec = mock(ExchangeSpecification.class);
  }

  @Test
  public void generationStartsAtZeroBeforeAnyConnection() {
    OkxStreamingService service = new OkxStreamingService("wss://localhost/ws", spec);

    assertThat(service.getGeneration()).isZero();
  }

  @Test
  public void staleGenerationMessageIsDroppedOnPublicService() {
    OkxStreamingService service = spy(new OkxStreamingService("wss://localhost/ws", spec));
    doReturn(42L).when(service).getGeneration();

    boolean accepted =
        service.handleMessageWithGeneration(41L, "{\"arg\":{\"channel\":\"tickers\"}}");

    assertThat(accepted).isFalse();
    verify(service, never()).messageHandler(anyString());
  }

  @Test
  public void currentGenerationMessageIsHandledOnPublicService() {
    OkxStreamingService service = spy(new OkxStreamingService("wss://localhost/ws", spec));
    doReturn(42L).when(service).getGeneration();

    boolean accepted = service.handleMessageWithGeneration(42L, "{\"event\":\"subscribe\"}");

    assertThat(accepted).isTrue();
    verify(service).messageHandler("{\"event\":\"subscribe\"}");
  }

  @Test
  public void staleGenerationMessageIsDroppedOnPrivateService() {
    OkxPrivateStreamingService service =
        spy(new OkxPrivateStreamingService("wss://localhost/ws", spec, mock(OkxExchange.class)));
    doReturn(7L).when(service).getGeneration();

    boolean accepted =
        service.handleMessageWithGeneration(6L, "{\"arg\":{\"channel\":\"orders\"}}");

    assertThat(accepted).isFalse();
    verify(service, never()).messageHandler(anyString());
  }

  @Test
  public void currentGenerationMessageIsHandledOnPrivateService() {
    OkxPrivateStreamingService service =
        spy(new OkxPrivateStreamingService("wss://localhost/ws", spec, mock(OkxExchange.class)));
    doReturn(7L).when(service).getGeneration();

    boolean accepted = service.handleMessageWithGeneration(7L, "{\"event\":\"subscribe\"}");

    assertThat(accepted).isTrue();
    verify(service).messageHandler("{\"event\":\"subscribe\"}");
  }

  @Test
  public void failedLoginEventClearsPrivateAuthorization() {
    OkxPrivateStreamingService service =
        new OkxPrivateStreamingService("wss://localhost/ws", spec, mock(OkxExchange.class));

    service.messageHandler("{\"event\":\"login\",\"code\":\"0\",\"msg\":\"\"}");
    assertThat(service.isLoginDone()).isTrue();

    service.messageHandler(
        "{\"event\":\"login\",\"code\":\"60009\",\"msg\":\"Login failed\"}");
    assertThat(service.isLoginDone()).isFalse();
  }

  @Test
  public void staleGenerationMessageIsDroppedOnBusinessService() {
    OkxBusinessStreamingService service =
        spy(new OkxBusinessStreamingService("wss://localhost/ws", spec));
    doReturn(3L).when(service).getGeneration();

    boolean accepted =
        service.handleMessageWithGeneration(2L, "{\"arg\":{\"channel\":\"candle\"}}");

    assertThat(accepted).isFalse();
    verify(service, never()).messageHandler(anyString());
  }

  @Test
  public void currentGenerationMessageIsHandledOnBusinessService() {
    OkxBusinessStreamingService service =
        spy(new OkxBusinessStreamingService("wss://localhost/ws", spec));
    doReturn(3L).when(service).getGeneration();

    boolean accepted = service.handleMessageWithGeneration(3L, "{\"event\":\"subscribe\"}");

    assertThat(accepted).isTrue();
    verify(service).messageHandler("{\"event\":\"subscribe\"}");
  }

  @Test
  public void privateServiceTracksActiveChannels() {
    OkxPrivateStreamingService service =
        new OkxPrivateStreamingService("wss://localhost/ws", spec, mock(OkxExchange.class));

    assertThat(service.hasActiveChannels()).isFalse();
    service.subscribeChannel("orders-BTC-USDT").test();
    assertThat(service.hasActiveChannels()).isTrue();
  }

  @Test
  public void businessServiceTracksActiveChannels() {
    OkxBusinessStreamingService service =
        new OkxBusinessStreamingService("wss://localhost/ws", spec);

    assertThat(service.hasActiveChannels()).isFalse();
    service.subscribeChannel("candle-BTC-USDT").test();
    assertThat(service.hasActiveChannels()).isTrue();
  }
}
