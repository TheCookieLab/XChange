package org.knowm.xchange.cryptocom;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.knowm.xchange.cryptocom.dto.CryptoComException;
import org.knowm.xchange.cryptocom.dto.CryptoComRequestException;
import org.knowm.xchange.cryptocom.dto.CryptoComResponse;
import org.knowm.xchange.cryptocom.dto.CryptoComRetryClass;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.exceptions.FundsExceededException;
import org.knowm.xchange.exceptions.NonceException;
import org.knowm.xchange.exceptions.RateLimitExceededException;

public class CryptoComErrorAdapterTest {

  @Test
  public void testInsufficientAvailableBalance() {
    ExchangeException adapted = adaptError(CryptoComErrorAdapter.INSUFFICIENT_AVAILABLE_BALANCE);
    assertThat(adapted).isInstanceOf(FundsExceededException.class);
  }

  @Test
  public void testExceedMaxTradableAmount() {
    ExchangeException adapted = adaptError(CryptoComErrorAdapter.EXCEED_MAX_TRADABLE_AMOUNT);
    assertThat(adapted).isInstanceOf(FundsExceededException.class);
  }

  @Test
  public void testTooManyRequests() {
    ExchangeException adapted = adaptError(CryptoComErrorAdapter.TOO_MANY_REQUESTS);
    assertThat(adapted).isInstanceOf(RateLimitExceededException.class);
  }

  @Test
  public void testInvalidNonce() {
    ExchangeException adapted = adaptError(CryptoComErrorAdapter.INVALID_NONCE);
    assertThat(adapted).isInstanceOf(NonceException.class);
  }

  @Test
  public void testUnmappedCodeFallsBackToGenericException() {
    ExchangeException adapted = adaptError(999999);
    assertThat(adapted).isExactlyInstanceOf(ExchangeException.class);
    assertThat(adapted.getMessage()).contains("999999").contains("boom");
  }

  @Test
  public void testHttpStatusException_insufficientBalance_isAlsoMapped() {
    ExchangeException adapted =
        CryptoComErrorAdapter.adaptError(
            new CryptoComException(CryptoComErrorAdapter.INSUFFICIENT_AVAILABLE_BALANCE, "boom"));
    assertThat(adapted).isInstanceOf(FundsExceededException.class);
  }

  @Test
  public void testHttpStatusException_unmappedCode_wrapsCauseWithoutDoublePrefixing() {
    CryptoComException exception = new CryptoComException(999999, "boom");

    ExchangeException adapted = CryptoComErrorAdapter.adaptError(exception);

    assertThat(adapted).isExactlyInstanceOf(ExchangeException.class);
    assertThat(adapted).hasCause(exception);
    assertThat(adapted.getMessage()).isEqualTo(exception.getMessage());
  }

  @Test
  public void unmappedEnvelopeError_withContext_isStructuredRequestException() {
    CryptoComResponse response = new CryptoComResponse();
    response.setId(99);
    response.setCode(12345);
    response.setMessage("boom");

    ExchangeException adapted =
        CryptoComErrorAdapter.adaptError(response, 99, "private/create-order");

    assertThat(adapted).isInstanceOf(CryptoComRequestException.class);
    CryptoComRequestException ex = (CryptoComRequestException) adapted;
    assertThat(ex.getRequestId()).isEqualTo(99);
    assertThat(ex.getMethod()).isEqualTo("private/create-order");
    assertThat(ex.getProviderCode()).isEqualTo(12345);
    assertThat(ex.getProviderMessage()).isEqualTo("boom");
    assertThat(ex.getRetryClass()).isEqualTo(CryptoComRetryClass.NONE);
  }

  @Test
  public void rateLimitWithContext_isRetryClassRateLimit() {
    CryptoComResponse response = new CryptoComResponse();
    response.setCode(CryptoComErrorAdapter.TOO_MANY_REQUESTS);
    response.setMessage("slow down");

    CryptoComRequestException ex =
        (CryptoComRequestException)
            CryptoComErrorAdapter.adaptError(response, 5, "private/get-accounts");

    assertThat(ex.getRetryClass()).isEqualTo(CryptoComRetryClass.RATE_LIMIT);
  }

  @Test
  public void invalidNonceWithContext_isRetryClassAuth() {
    CryptoComException exception = new CryptoComException(CryptoComErrorAdapter.INVALID_NONCE, "n");

    CryptoComRequestException ex =
        (CryptoComRequestException)
            CryptoComErrorAdapter.adaptError(exception, 5, "private/user-balance");

    assertThat(ex.getRetryClass()).isEqualTo(CryptoComRetryClass.AUTH);
    assertThat(ex.getHttpStatus()).isNull();
  }

  private static ExchangeException adaptError(int code) {
    CryptoComResponse response = new CryptoComResponse();
    response.setCode(code);
    response.setMessage("boom");
    return CryptoComErrorAdapter.adaptError(response);
  }
}
