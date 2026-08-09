package org.knowm.xchange.kraken.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.Test;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.exceptions.ExchangeUnavailableException;
import org.knowm.xchange.exceptions.FrequencyLimitExceededException;
import org.knowm.xchange.exceptions.FundsExceededException;
import org.knowm.xchange.exceptions.NonceException;
import org.knowm.xchange.exceptions.RateLimitExceededException;
import org.knowm.xchange.kraken.KrakenExchange;
import org.knowm.xchange.kraken.dto.KrakenResult;
import org.knowm.xchange.kraken.service.KrakenException.RetryClass;

public class KrakenErrorHandlingTest {

  private final KrakenBaseService service;

  public KrakenErrorHandlingTest() {
    ExchangeSpecification specification = new ExchangeSpecification(KrakenExchange.class);
    specification.setShouldLoadRemoteMetaData(false);
    Exchange exchange = ExchangeFactory.INSTANCE.createExchange(specification);
    service = new KrakenBaseService(exchange);
  }

  private static KrakenResult<Ticker> failingResult(String... errors) {
    return new KrakenResult<>(null, errors);
  }

  @Test
  void redacts_sensitive_fields() {
    String raw =
        "EOrder:Insufficient funds api_key=K1234 secret=abc123 nonce=1786289000 "
            + "otp=123456 address=0xdeadbeef token=ws-secret Authorization: Bearer abc.def.ghi "
            + "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.signature";
    String sanitized = KrakenRedactor.sanitize(raw);

    assertThat(sanitized)
        .doesNotContain("K1234", "abc123", "1786289000", "123456", "0xdeadbeef", "ws-secret")
        .doesNotContain("abc.def.ghi")
        .doesNotContain("eyJhbGciOiJIUzI1NiJ9")
        .doesNotContain("signature")
        .contains("<redacted>");
    // non-sensitive prose survives
    assertThat(sanitized).contains("EOrder:Insufficient funds", "Bearer");
  }

  @Test
  void classifies_retry_classes() {
    assertThat(KrakenException.classify("EOrder:Rate limit exceeded"))
        .isEqualTo(RetryClass.RETRYABLE_RATE_LIMIT);
    assertThat(KrakenException.classify("EGeneral:Too many requests"))
        .isEqualTo(RetryClass.RETRYABLE_RATE_LIMIT);
    assertThat(KrakenException.classify("EGeneral:Temporary lockout"))
        .isEqualTo(RetryClass.RETRYABLE_RATE_LIMIT);
    assertThat(KrakenException.classify("EService:Unavailable"))
        .isEqualTo(RetryClass.RETRYABLE_TRANSIENT);
    assertThat(KrakenException.classify("EService:Busy"))
        .isEqualTo(RetryClass.RETRYABLE_TRANSIENT);
    assertThat(KrakenException.classify("EOrder:Unknown order"))
        .isEqualTo(RetryClass.NON_RETRYABLE);
    assertThat(KrakenException.classify("EAPI:Invalid key")).isEqualTo(RetryClass.NON_RETRYABLE);
    assertThat(KrakenException.classify("EGeneral:Invalid arguments"))
        .isEqualTo(RetryClass.NON_RETRYABLE);
    assertThat(KrakenException.classify("EUnknown:Weird")).isEqualTo(RetryClass.UNKNOWN);
    assertThat(KrakenException.classify(null)).isEqualTo(RetryClass.UNKNOWN);
  }

  @Test
  void checkResult_maps_known_codes_to_typed_exceptions() {
    assertThatExceptionOfType(NonceException.class)
        .isThrownBy(() -> service.checkResult(failingResult("EAPI:Invalid nonce")));
    assertThatExceptionOfType(FrequencyLimitExceededException.class)
        .isThrownBy(() -> service.checkResult(failingResult("EGeneral:Temporary lockout")));
    assertThatExceptionOfType(FundsExceededException.class)
        .isThrownBy(() -> service.checkResult(failingResult("EOrder:Insufficient funds")));
    assertThatExceptionOfType(RateLimitExceededException.class)
        .isThrownBy(() -> service.checkResult(failingResult("EGeneral:Too many requests")));
    assertThatExceptionOfType(RateLimitExceededException.class)
        .isThrownBy(() -> service.checkResult(failingResult("EOrder:Rate limit exceeded")));
    assertThatExceptionOfType(ExchangeUnavailableException.class)
        .isThrownBy(() -> service.checkResult(failingResult("EService:Busy")));
  }

  @Test
  void checkResult_falls_back_to_structured_exception() {
    KrakenException exception =
        org.assertj.core.api.Assertions.catchThrowableOfType(
            () -> service.checkResult(failingResult("EOrder:Unknown order", "detail two"), "placeOrder"),
            KrakenException.class);

    assertThat(exception).isNotNull();
    assertThat(exception.getDomain()).isEqualTo("spot");
    assertThat(exception.getOperation()).isEqualTo("placeOrder");
    assertThat(exception.getRetryClass()).isEqualTo(RetryClass.NON_RETRYABLE);
    assertThat(exception.getErrors()).containsExactly("EOrder:Unknown order", "detail two");
    assertThat(exception.getMessage()).contains("placeOrder", "EOrder:Unknown order");
  }

  @Test
  void structured_exception_redacts_credentials_in_errors() {
    KrakenException exception =
        org.assertj.core.api.Assertions.catchThrowableOfType(
            () -> service.checkResult(failingResult("EOrder:auth api_key=K1234"), "placeOrder"),
            KrakenException.class);

    assertThat(exception).isNotNull();
    assertThat(exception.getMessage()).doesNotContain("K1234");
    assertThat(exception.getErrors()[0]).doesNotContain("K1234");
  }
}
