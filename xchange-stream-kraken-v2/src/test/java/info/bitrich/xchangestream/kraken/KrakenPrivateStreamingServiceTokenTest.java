package info.bitrich.xchangestream.kraken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import info.bitrich.xchangestream.kraken.dto.response.KrakenResult;
import info.bitrich.xchangestream.kraken.dto.response.KrakenWebsocketToken;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.currency.CurrencyPair;
import si.mazi.rescu.ParamsDigest;

/** Single-flight websocket token refresh and generation-scoped reauth. */
class KrakenPrivateStreamingServiceTokenTest {

  private KrakenAuthenticated krakenAuthenticated;
  private KrakenStreamingExchange exchange;
  private KrakenPrivateStreamingService service;

  private void initService() {
    krakenAuthenticated = mock(KrakenAuthenticated.class);
    exchange = mock(KrakenStreamingExchange.class);
    ExchangeSpecification spec = new ExchangeSpecification(KrakenStreamingExchange.class);
    spec.setApiKey("api-key");
    when(exchange.getExchangeSpecification()).thenReturn(spec);

    service =
        new KrakenPrivateStreamingService(
            "wss://ws-auth.kraken.com/v2", exchange, krakenAuthenticated, mock(ParamsDigest.class));
  }

  @SuppressWarnings("unchecked")
  private void stubToken(String token) throws Exception {
    KrakenResult<KrakenWebsocketToken> result =
        KrakenResult.<KrakenWebsocketToken>builder()
            .result(KrakenWebsocketToken.builder().token(token).build())
            .error(new String[0])
            .build();
    when(krakenAuthenticated.getWebsocketToken(any(), any(), any())).thenReturn(result);
  }

  @Test
  void concurrent_subscribes_share_one_token_fetch() throws Exception {
    initService();
    stubToken("tok-1");

    int threads = 4;
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(threads);
    AtomicReference<Throwable> failure = new AtomicReference<>();

    for (int i = 0; i < threads; i++) {
      pool.submit(
          () -> {
            try {
              start.await();
              String message =
                  service.getSubscribeMessage("balances", (Object) CurrencyPair.BTC_USD);
              if (!message.contains("tok-1")) {
                failure.set(new AssertionError("token missing from subscribe message: " + message));
              }
            } catch (Throwable t) {
              failure.set(t);
            } finally {
              done.countDown();
            }
          });
    }
    start.countDown();
    assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
    pool.shutdownNow();

    assertThat(failure.get()).isNull();
    // single-flight: only one REST call for all concurrent subscribers
    verify(krakenAuthenticated).getWebsocketToken(any(), any(), any());
  }

  @Test
  void public_channels_never_fetch_a_token() throws Exception {
    initService();
    stubToken("tok-1");

    service.getSubscribeMessage("ticker", (Object) CurrencyPair.BTC_USD);
    service.getUnsubscribeMessage("ticker-BTC/USD");

    verify(krakenAuthenticated, never()).getWebsocketToken(any(), any(), any());
  }

  @Test
  void token_is_refreshed_after_ttl() throws Exception {
    initService();
    stubToken("tok-1");
    service.getSubscribeMessage("balances", (Object) CurrencyPair.BTC_USD);

    // simulate TTL expiry without waiting
    service.setTokenFetchedAtMillis(
        System.currentTimeMillis() - KrakenPrivateStreamingService.TOKEN_TTL_MILLIS - 1);
    stubToken("tok-2");
    service.getSubscribeMessage("balances", (Object) CurrencyPair.BTC_USD);

    verify(krakenAuthenticated, org.mockito.Mockito.times(2))
        .getWebsocketToken(any(), any(), any());
  }
}
