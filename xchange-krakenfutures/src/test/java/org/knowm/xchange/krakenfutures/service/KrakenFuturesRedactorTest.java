package org.knowm.xchange.krakenfutures.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.knowm.xchange.krakenfutures.service.KrakenFuturesException.RetryClass;

public class KrakenFuturesRedactorTest {

  @Test
  void redacts_sensitive_fields() {
    String raw =
        "insufficient funds api_key=KF1 secret=s3cr3t nonce=42 address=bc1qxyz "
            + "Authorization: Bearer tok123 eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.sig";
    String sanitized = KrakenFuturesRedactor.sanitize(raw);

    assertThat(sanitized)
        .doesNotContain("KF1", "s3cr3t", "42", "bc1qxyz", "tok123")
        .doesNotContain("eyJhbGciOiJIUzI1NiJ9")
        .contains("<redacted>");
    assertThat(sanitized).contains("insufficient funds");
  }

  @Test
  void classifies_retry_classes() {
    assertThat(KrakenFuturesException.classify("Rate limit exceeded"))
        .isEqualTo(RetryClass.RETRYABLE_RATE_LIMIT);
    assertThat(KrakenFuturesException.classify("service unavailable"))
        .isEqualTo(RetryClass.RETRYABLE_TRANSIENT);
    assertThat(KrakenFuturesException.classify("order rejected"))
        .isEqualTo(RetryClass.NON_RETRYABLE);
    assertThat(KrakenFuturesException.classify("insufficient funds"))
        .isEqualTo(RetryClass.NON_RETRYABLE);
    assertThat(KrakenFuturesException.classify("something else")).isEqualTo(RetryClass.UNKNOWN);
    assertThat(KrakenFuturesException.classify(null)).isEqualTo(RetryClass.UNKNOWN);
  }

  @Test
  void structured_exception_carries_domain_operation_and_redacted_details() {
    KrakenFuturesException exception =
        new KrakenFuturesException(
            "futures",
            "placeKrakenFuturesLimitOrder",
            RetryClass.NON_RETRYABLE,
            new String[] {"rejected api_key=KF1"});

    assertThat(exception.getDomain()).isEqualTo("futures");
    assertThat(exception.getOperation()).isEqualTo("placeKrakenFuturesLimitOrder");
    assertThat(exception.getRetryClass()).isEqualTo(RetryClass.NON_RETRYABLE);
    assertThat(exception.getErrors()[0]).doesNotContain("KF1");
    assertThat(exception.getMessage()).contains("placeKrakenFuturesLimitOrder");
  }
}
