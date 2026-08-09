package org.knowm.xchange.coinbase.v3.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Collections;
import org.junit.Test;
import org.knowm.xchange.coinbase.v3.CoinbaseAuthenticated;
import org.knowm.xchange.coinbase.v3.CoinbaseExchange;
import org.knowm.xchange.coinbase.v3.dto.CoinbaseException;
import org.knowm.xchange.coinbase.v3.dto.CoinbaseException.CoinbaseError;
import org.knowm.xchange.coinbase.v3.dto.accounts.CoinbaseAccountsResponse;
import si.mazi.rescu.ParamsDigest;

/** Deterministic tests for bounded jittered retry on replay-safe Coinbase reads. */
public class CoinbaseRetryTest {

  @Test
  public void rateCreditFailureThenSuccessCompletesIteration() throws Exception {
    CoinbaseAuthenticated authenticated = mock(CoinbaseAuthenticated.class);
    CoinbaseAccountsResponse success =
        new CoinbaseAccountsResponse(Collections.emptyList(), false, null, null);
    when(authenticated.listAccounts(any(ParamsDigest.class), eq(250), any()))
        .thenThrow(rateLimited())
        .thenReturn(success);

    CoinbaseAccountServiceRaw service =
        new CoinbaseAccountServiceRaw(coinbaseExchange(), authenticated, mock(ParamsDigest.class));

    assertEquals(0, service.getCoinbaseAccounts().size());
    verify(authenticated, times(2)).listAccounts(any(ParamsDigest.class), eq(250), any());
  }

  @Test
  public void persistentRateCreditFailsAfterBoundedAttempts() throws Exception {
    CoinbaseAuthenticated authenticated = mock(CoinbaseAuthenticated.class);
    when(authenticated.listAccounts(any(ParamsDigest.class), eq(250), any()))
        .thenThrow(rateLimited());

    CoinbaseAccountServiceRaw service =
        new CoinbaseAccountServiceRaw(coinbaseExchange(), authenticated, mock(ParamsDigest.class));

    CoinbaseException exception =
        assertThrows(CoinbaseException.class, service::getCoinbaseAccounts);
    assertEquals(429, exception.getHttpStatusCode());
    verify(authenticated, times(CoinbaseRetry.MAX_ATTEMPTS))
        .listAccounts(any(ParamsDigest.class), eq(250), any());
  }

  @Test
  public void permanentFailureIsNeverRetried() throws Exception {
    CoinbaseAuthenticated authenticated = mock(CoinbaseAuthenticated.class);
    when(authenticated.listAccounts(any(ParamsDigest.class), eq(250), any()))
        .thenThrow(rejected());

    CoinbaseAccountServiceRaw service =
        new CoinbaseAccountServiceRaw(coinbaseExchange(), authenticated, mock(ParamsDigest.class));

    CoinbaseException exception =
        assertThrows(CoinbaseException.class, service::getCoinbaseAccounts);
    assertEquals(400, exception.getHttpStatusCode());
    verify(authenticated, times(1)).listAccounts(any(ParamsDigest.class), eq(250), any());
  }

  @Test
  public void interruptedBackoffRestoresInterruptFlag() throws Exception {
    Thread.currentThread().interrupt();
    try {
      IOException failure =
          assertThrows(IOException.class,
              () -> CoinbaseRetry.readWithBackoff(() -> {
                throw rateLimited();
              }));
      assertTrue(failure.getMessage().contains("Interrupted during Coinbase retry backoff"));
    } finally {
      assertTrue(Thread.interrupted());
    }
  }

  private static CoinbaseAccountsResponse accountsResponse() {
    return new CoinbaseAccountsResponse(Collections.emptyList(), false, null, null);
  }

  private static CoinbaseException rateLimited() {
    CoinbaseException failure = new CoinbaseException(
        Collections.singletonList(new CoinbaseError("RATE_LIMIT_REACHED", "slow down")));
    failure.setHttpStatusCode(429);
    return failure;
  }

  private static CoinbaseException rejected() {
    CoinbaseException failure = new CoinbaseException(
        Collections.singletonList(new CoinbaseError("INVALID_ARGUMENT", "nope")));
    failure.setHttpStatusCode(400);
    return failure;
  }

  private static org.knowm.xchange.Exchange coinbaseExchange() {
    CoinbaseExchange exchange = new CoinbaseExchange();
    exchange.applySpecification(exchange.getDefaultExchangeSpecification());
    return exchange;
  }
}
