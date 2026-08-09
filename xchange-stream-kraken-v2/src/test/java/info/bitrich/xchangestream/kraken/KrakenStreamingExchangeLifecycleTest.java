package info.bitrich.xchangestream.kraken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Completable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.ExchangeSpecification;

/** Exchange-level lifecycle: conditional private socket, dual disconnect, aggregate isAlive. */
class KrakenStreamingExchangeLifecycleTest {

  KrakenStreamingService publicService;
  KrakenPrivateStreamingService privateService;
  KrakenStreamingExchange exchange;

  @BeforeEach
  void setUp() {
    publicService = mock(KrakenStreamingService.class);
    privateService = mock(KrakenPrivateStreamingService.class);
    when(publicService.connect()).thenReturn(Completable.complete());
    when(privateService.connect()).thenReturn(Completable.complete());
    when(publicService.disconnect()).thenReturn(Completable.complete());
    when(privateService.disconnect()).thenReturn(Completable.complete());
    when(publicService.isSocketOpen()).thenReturn(true);
    when(privateService.isSocketOpen()).thenReturn(true);

    exchange =
        new KrakenStreamingExchange() {
          @Override
          protected KrakenStreamingService createPublicService() {
            return publicService;
          }

          @Override
          protected KrakenPrivateStreamingService createPrivateService() {
            return privateService;
          }
        };
  }

  private void applySpec(boolean withCredentials) {
    ExchangeSpecification spec = exchange.getDefaultExchangeSpecification();
    if (withCredentials) {
      spec.setApiKey("key");
      spec.setSecretKey("c2VjcmV0");
    }
    exchange.applySpecification(spec);
  }

  @Test
  void public_only_connect_without_credentials() {
    applySpec(false);

    exchange.connect().blockingAwait();

    assertThat(exchange.privateSocketRequired()).isFalse();
    assertThat(exchange.getKrakenPrivateStreamingService()).isNull();
    assertThat(exchange.getStreamingTradeService()).isNull();
    assertThat(exchange.getStreamingAccountService()).isNull();
    verify(privateService, never()).connect();
    assertThat(exchange.getPublicGeneration()).isEqualTo(1);
    assertThat(exchange.getPrivateGeneration()).isZero();
    assertThat(exchange.isAlive()).isTrue();
  }

  @Test
  void combined_connect_connects_both_sockets() {
    applySpec(true);

    exchange.connect().blockingAwait();

    verify(publicService).connect();
    verify(privateService).connect();
    assertThat(exchange.getStreamingTradeService()).isNotNull();
    assertThat(exchange.getStreamingAccountService()).isNotNull();
    assertThat(exchange.getPublicGeneration()).isEqualTo(1);
    assertThat(exchange.getPrivateGeneration()).isEqualTo(1);
    assertThat(exchange.isAlive()).isTrue();
  }

  @Test
  void private_socket_drop_breaks_combined_alive() {
    applySpec(true);

    exchange.connect().blockingAwait();
    when(privateService.isSocketOpen()).thenReturn(false);

    assertThat(exchange.isAlive()).isFalse();
  }

  @Test
  void disconnect_closes_both_sockets() {
    applySpec(true);

    exchange.connect().blockingAwait();
    exchange.disconnect().blockingAwait();

    verify(publicService).disconnect();
    verify(privateService).disconnect();
    assertThat(exchange.getKrakenStreamingService()).isNull();
    assertThat(exchange.getKrakenPrivateStreamingService()).isNull();
    assertThat(exchange.isAlive()).isFalse();
  }

  @Test
  void reconnect_disconnects_stale_generation_sockets() {
    applySpec(true);

    exchange.connect().blockingAwait();

    KrakenStreamingService stalePublic = publicService;
    KrakenPrivateStreamingService stalePrivate = privateService;

    publicService = mock(KrakenStreamingService.class);
    privateService = mock(KrakenPrivateStreamingService.class);
    when(publicService.connect()).thenReturn(Completable.complete());
    when(privateService.connect()).thenReturn(Completable.complete());
    when(publicService.disconnect()).thenReturn(Completable.complete());
    when(privateService.disconnect()).thenReturn(Completable.complete());
    when(publicService.isSocketOpen()).thenReturn(true);
    when(privateService.isSocketOpen()).thenReturn(true);

    exchange.connect().blockingAwait();

    // the stale generation sockets must be dropped and must not deliver events
    verify(stalePublic).disconnect();
    verify(stalePrivate).disconnect();
    assertThat(exchange.getPublicGeneration()).isEqualTo(2);
    assertThat(exchange.getPrivateGeneration()).isEqualTo(2);
    assertThat(exchange.isAlive()).isTrue();
  }
}
