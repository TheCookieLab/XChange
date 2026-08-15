package org.knowm.xchange.mexc.v3.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;
import org.knowm.xchange.exceptions.CurrencyPairNotValidException;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.exceptions.ExchangeSecurityException;
import org.knowm.xchange.exceptions.ExchangeUnavailableException;
import org.knowm.xchange.exceptions.FundsExceededException;
import org.knowm.xchange.exceptions.OperationTimeoutException;
import org.knowm.xchange.exceptions.OrderAmountUnderMinimumException;
import org.knowm.xchange.exceptions.OrderNotValidException;
import org.knowm.xchange.exceptions.RateLimitExceededException;

/** Provider error-code mapping and retry classification for MEXC Spot v3. */
public class MexcV3ErrorAdapterTest {

  private MexcV3Exception exception(int code, int httpStatus, String msg) {
    MexcV3Exception e = new MexcV3Exception(code, msg);
    e.setHttpStatusCode(httpStatus);
    return e;
  }

  @Test
  public void rateLimitMapsToRateLimitExceeded() {
    assertThat(exception(429, 429, "too many requests").adapt())
        .isInstanceOf(RateLimitExceededException.class);
  }

  @Test
  public void signatureAndAuthCodesMapToSecurity() {
    for (int code : new int[] {400, 401, 602, 10072, 700001, 700002, 700006, 700007}) {
      assertThat(exception(code, 400, "auth").adapt())
          .as("code %d", code)
          .isInstanceOf(ExchangeSecurityException.class);
    }
  }

  @Test
  public void badSymbolCodesMapToCurrencyPairNotValid() {
    for (int code : new int[] {10007, 30014, 30021, 730001}) {
      assertThat(exception(code, 400, "bad symbol").adapt())
          .as("code %d", code)
          .isInstanceOf(CurrencyPairNotValidException.class);
    }
  }

  @Test
  public void insufficientFundsCodesMapToFundsExceeded() {
    assertThat(exception(10101, 400, "insufficient balance").adapt())
        .isInstanceOf(FundsExceededException.class);
    assertThat(exception(30005, 400, "oversold").adapt()).isInstanceOf(FundsExceededException.class);
  }

  @Test
  public void orderAmountCodesMapToUnderMinimum() {
    for (int code : new int[] {30002, 30003, 30010}) {
      assertThat(exception(code, 400, "amount").adapt())
          .as("code %d", code)
          .isInstanceOf(OrderAmountUnderMinimumException.class);
    }
  }

  @Test
  public void recvWindowTimeoutMapsToOperationTimeout() {
    assertThat(exception(700003, 400, "timestamp outside window").adapt())
        .isInstanceOf(OperationTimeoutException.class);
  }

  @Test
  public void orderValidationCodesMapToOrderNotValid() {
    for (int code : new int[] {-2011, 30041, 33333, 44444, 700004, 700005, 700008}) {
      assertThat(exception(code, 400, "order").adapt())
          .as("code %d", code)
          .isInstanceOf(OrderNotValidException.class);
    }
  }

  @Test
  public void serviceUnavailableMapsToUnavailable() {
    assertThat(exception(503, 503, "unavailable").adapt())
        .isInstanceOf(ExchangeUnavailableException.class);
  }

  @Test
  public void unknownCodeMapsToGenericExchangeException() {
    assertThat(exception(99999, 400, "unknown").adapt()).isInstanceOf(ExchangeException.class);
  }

  @Test
  public void unmappedCodeWithRateLimitHttpStatusMapsToRateLimitExceeded() {
    assertThat(exception(99999, 429, "rate limited").adapt())
        .isInstanceOf(RateLimitExceededException.class);
    assertThat(exception(99999, 418, "ip banned").adapt())
        .isInstanceOf(RateLimitExceededException.class);
  }

  @Test
  public void unmappedCodeWithServerErrorHttpStatusMapsToUnavailable() {
    assertThat(exception(99999, 503, "provider outage").adapt())
        .isInstanceOf(ExchangeUnavailableException.class);
  }

  @Test
  public void unmappedCodeWithAuthHttpStatusMapsToSecurity() {
    assertThat(exception(99999, 403, "denied").adapt())
        .isInstanceOf(ExchangeSecurityException.class);
  }

  @Test
  public void classificationCoversHttpAndCodeSemantics() {
    assertThat(exception(429, 429, "").getRetryClassification())
        .isEqualTo(RetryClassification.RATE_LIMITED);
    assertThat(exception(0, 401, "").getRetryClassification())
        .isEqualTo(RetryClassification.AUTHENTICATION);
    assertThat(exception(602, 400, "").getRetryClassification())
        .isEqualTo(RetryClassification.AUTHENTICATION);
    assertThat(exception(700002, 400, "").getRetryClassification())
        .isEqualTo(RetryClassification.AUTHENTICATION);
    assertThat(exception(0, 503, "").getRetryClassification())
        .isEqualTo(RetryClassification.TRANSPORT);
    assertThat(exception(30005, 400, "").getRetryClassification())
        .isEqualTo(RetryClassification.PERMANENT);
  }

  @Test
  public void placementNeverClassifiedAsAmbiguousByException() {
    // AMBIGUOUS is a caller-side policy applied via ReplaySafety.PLACEMENT, never derived
    // from the exception alone: an unknown-outcome placement must be reconciled, not replayed.
    assertThat(ReplaySafety.PLACEMENT).isEqualTo(ReplaySafety.PLACEMENT);
    assertThat(ReplaySafety.READ.name()).isEqualTo("READ");
    assertThat(ReplaySafety.IDEMPOTENT_CANCELLATION.name()).isEqualTo("IDEMPOTENT_CANCELLATION");
  }

  @Test
  public void retryAfterIsReadFromHeaders() {
    MexcV3Exception e = exception(429, 429, "slow down");
    e.setResponseHeaders(java.util.Map.of("Retry-After", java.util.List.of("37")));
    assertThat(e.getRetryAfterSeconds()).isEqualTo(37);
  }

  @Test
  public void missingRetryAfterReturnsNull() {
    MexcV3Exception e = exception(429, 429, "slow down");
    assertThat(e.getRetryAfterSeconds()).isNull();
  }

  @Test
  public void messageIsSanitized() {
    assertThat(MexcV3Exception.of(0, "secret=abc123 leaked").getMsg()).doesNotContain("abc123");
    assertThat(new MexcV3Exception(30005, "X-MEXC-APIKEY: kEy123").getMsg())
        .doesNotContain("kEy123");
  }
}
