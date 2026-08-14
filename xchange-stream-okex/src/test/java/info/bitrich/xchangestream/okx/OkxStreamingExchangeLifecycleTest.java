package info.bitrich.xchangestream.okx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import info.bitrich.xchangestream.service.netty.ConnectionStateModel.State;
import io.reactivex.rxjava3.core.Completable;
import org.junit.Before;
import org.junit.Test;

/** Offline lifecycle tests for the unified three-socket {@link OkxStreamingExchange}. */
public class OkxStreamingExchangeLifecycleTest {

  private OkxStreamingExchange exchange;
  private OkxStreamingService streamingService;
  private OkxPrivateStreamingService privateStreamingService;
  private OkxBusinessStreamingService businessStreamingService;

  @Before
  public void setUp() {
    exchange = new OkxStreamingExchange();
    streamingService = mock(OkxStreamingService.class);
    privateStreamingService = mock(OkxPrivateStreamingService.class);
    businessStreamingService = mock(OkxBusinessStreamingService.class);
  }

  private void injectAllServices() {
    exchange.setStreamingService(streamingService);
    exchange.setPrivateStreamingService(privateStreamingService);
    exchange.setBusinessStreamingService(businessStreamingService);
  }

  /** All three sockets healthy, with an active private stream (trading) so PRIVATE is required. */
  private void openAllSocketsWithActivePrivateStream() {
    when(streamingService.isSocketOpen()).thenReturn(true);
    when(businessStreamingService.isSocketOpen()).thenReturn(true);
    when(privateStreamingService.isSocketOpen()).thenReturn(true);
    when(privateStreamingService.isLoginDone()).thenReturn(true);
    when(privateStreamingService.hasActiveChannels()).thenReturn(true);
  }

  // --- aggregate liveness ------------------------------------------------------------

  @Test
  public void isAliveIsFalseBeforeConnect() {
    assertThat(exchange.isAlive()).isFalse();
  }

  @Test
  public void isAliveIsFalseWhenBusinessSocketIsClosedWhilePublicAndPrivateAreOpen() {
    injectAllServices();
    openAllSocketsWithActivePrivateStream();
    when(businessStreamingService.isSocketOpen()).thenReturn(false);

    assertThat(exchange.isAlive()).isFalse();

    when(businessStreamingService.isSocketOpen()).thenReturn(true);
    assertThat(exchange.isAlive()).isTrue();
  }

  @Test
  public void isAliveRequiresPrivateLoginWhilePrivateStreamsAreActive() {
    injectAllServices();
    openAllSocketsWithActivePrivateStream();
    when(privateStreamingService.isLoginDone()).thenReturn(false);

    assertThat(exchange.isAlive()).isFalse();

    when(privateStreamingService.isLoginDone()).thenReturn(true);
    assertThat(exchange.isAlive()).isTrue();
  }

  @Test
  public void isAliveDoesNotRequirePrivateSocketWhenPrivateStreamsAreAbsent() {
    exchange.setStreamingService(streamingService);
    exchange.setBusinessStreamingService(businessStreamingService);
    when(streamingService.isSocketOpen()).thenReturn(true);
    when(businessStreamingService.isSocketOpen()).thenReturn(true);

    assertThat(exchange.isAlive()).isTrue();
  }

  // --- required-transport derivation -------------------------------------------------

  @Test
  public void requiredTransportsDefaultToPublicAndBusiness() {
    injectAllServices();
    when(privateStreamingService.hasActiveChannels()).thenReturn(false);

    assertThat(exchange.getRequiredTransports())
        .containsExactlyInAnyOrder(TransportRole.PUBLIC, TransportRole.BUSINESS);
  }

  @Test
  public void privateTransportIsRequiredWhenTradingStreamsAreRequested() {
    injectAllServices();
    when(privateStreamingService.hasActiveChannels()).thenReturn(true);

    assertThat(exchange.getRequiredTransports()).contains(TransportRole.PRIVATE);
  }

  @Test
  public void privateTransportIsNeverRequiredWithoutPrivateService() {
    exchange.setStreamingService(streamingService);
    exchange.setBusinessStreamingService(businessStreamingService);

    assertThat(exchange.getRequiredTransports()).doesNotContain(TransportRole.PRIVATE);
  }

  @Test
  public void explicitConfigurationReplacesDefaultRequiredSet() {
    injectAllServices();
    exchange.setRequiredTransports(TransportRole.PUBLIC);

    assertThat(exchange.getRequiredTransports())
        .containsExactlyInAnyOrder(TransportRole.PUBLIC);
  }

  @Test
  public void explicitPrivateConfigurationKeepsPrivateRequiredEvenWithoutActivity() {
    injectAllServices();
    exchange.setRequiredTransports(TransportRole.PUBLIC, TransportRole.PRIVATE);
    when(privateStreamingService.hasActiveChannels()).thenReturn(false);

    assertThat(exchange.getRequiredTransports())
        .containsExactlyInAnyOrder(TransportRole.PUBLIC, TransportRole.PRIVATE);
  }

  @Test
  public void businessTransportIsRequiredAgainWhenBusinessSubscriptionsAreActive() {
    injectAllServices();
    exchange.setRequiredTransports(TransportRole.PUBLIC);
    when(businessStreamingService.hasActiveChannels()).thenReturn(true);

    assertThat(exchange.getRequiredTransports())
        .containsExactlyInAnyOrder(TransportRole.PUBLIC, TransportRole.BUSINESS);
  }

  // --- resubscribe on every transport -------------------------------------------------

  @Test
  public void resubscribeChannelsRestoresChannelsOnAllTransports() {
    injectAllServices();

    exchange.resubscribeChannels();

    verify(streamingService).resubscribeChannels();
    verify(privateStreamingService).resubscribeChannels();
    verify(businessStreamingService).resubscribeChannels();
  }

  @Test
  public void resubscribeChannelsIsSafeBeforeConnect() {
    // Must not throw when no services exist yet.
    exchange.resubscribeChannels();
  }

  // --- null-safe lifecycle observables ------------------------------------------------

  @Test
  public void lifecycleObservablesAreNullSafeBeforeConnect() {
    assertThat(exchange.connectionStateObservable().blockingFirst()).isEqualTo(State.CLOSED);
    assertThat(exchange.connectionStateObservablePrivateChannel().blockingFirst())
        .isEqualTo(State.CLOSED);
    assertThat(exchange.connectionStateObservableBusinessChannel().blockingFirst())
        .isEqualTo(State.CLOSED);
    exchange.reconnectFailure().test().assertEmpty();
    exchange.connectionIdle().test().assertEmpty();
    exchange.setChannelInactiveHandler(message -> {});
  }

  // --- connect with injected services -------------------------------------------------

  @Test
  public void connectUsesInjectedServicesAndIncrementsConnectionGeneration() {
    exchange.applySpecification(exchange.getDefaultExchangeSpecification());
    injectAllServices();
    exchange.setRequiredTransports(
        TransportRole.PUBLIC, TransportRole.PRIVATE, TransportRole.BUSINESS);
    when(streamingService.connect()).thenReturn(Completable.complete());
    when(privateStreamingService.connect()).thenReturn(Completable.complete());
    when(businessStreamingService.connect()).thenReturn(Completable.complete());

    assertThat(exchange.getConnectionGeneration()).isZero();
    exchange.connect().blockingAwait();

    assertThat(exchange.getConnectionGeneration()).isEqualTo(1L);
    verify(streamingService).connect();
    verify(privateStreamingService).connect();
    verify(businessStreamingService).connect();
  }

  @Test
  public void connectOnlyOpensExplicitlyConfiguredTransports() {
    exchange.applySpecification(exchange.getDefaultExchangeSpecification());
    injectAllServices();
    exchange.setRequiredTransports(TransportRole.PUBLIC);
    when(streamingService.connect()).thenReturn(Completable.complete());
    when(privateStreamingService.connect()).thenReturn(Completable.complete());
    when(businessStreamingService.connect()).thenReturn(Completable.complete());

    exchange.connect().blockingAwait();

    verify(streamingService).connect();
    verify(privateStreamingService, never()).connect();
    verify(businessStreamingService, never()).connect();
  }
}
