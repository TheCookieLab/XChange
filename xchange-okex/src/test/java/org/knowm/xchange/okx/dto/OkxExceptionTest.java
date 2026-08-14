package org.knowm.xchange.okx.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;
import org.knowm.xchange.okx.dto.OkxException.RetryClassification;
import org.knowm.xchange.okx.dto.OkxException.TransportState;

/** Offline tests for the structured {@link OkxException}. */
public class OkxExceptionTest {

  @Test
  public void testLegacyConstructorKeepsMessageAndCode() {
    OkxException exception = new OkxException("Order does not exist", 51603);

    assertThat(exception.getMessage()).isEqualTo("Order does not exist");
    assertThat(exception.getCode()).isEqualTo(51603);
    assertThat(exception.toString()).isEqualTo("51603:Order does not exist");
  }

  @Test
  public void testLegacyConstructorStructuredFieldsDefault() {
    OkxException exception = new OkxException("boom", 1);

    assertThat(exception.getDomain()).isNull();
    assertThat(exception.getEndpoint()).isNull();
    assertThat(exception.getRequestId()).isNull();
    assertThat(exception.getTransportState()).isEqualTo(TransportState.UNKNOWN);
    assertThat(exception.getRetryClassification()).isEqualTo(RetryClassification.UNKNOWN);
  }

  @Test
  public void testBuilderFillsStructuredFields() {
    OkxException exception =
        OkxException.builder()
            .message("Order does not exist")
            .code(51603)
            .domain("trade")
            .endpoint("/api/v5/trade/cancel-order")
            .requestId("clOrdId-123")
            .transportState(TransportState.BUSINESS_ERROR)
            .retryClassification(RetryClassification.NON_RETRYABLE)
            .build();

    assertThat(exception.getMessage()).isEqualTo("Order does not exist");
    assertThat(exception.getCode()).isEqualTo(51603);
    assertThat(exception.getDomain()).isEqualTo("trade");
    assertThat(exception.getEndpoint()).isEqualTo("/api/v5/trade/cancel-order");
    assertThat(exception.getRequestId()).isEqualTo("clOrdId-123");
    assertThat(exception.getTransportState()).isEqualTo(TransportState.BUSINESS_ERROR);
    assertThat(exception.getRetryClassification()).isEqualTo(RetryClassification.NON_RETRYABLE);
  }

  @Test
  public void testFromResponsePopulatesStructuredFieldsAndRedactsSecrets() {
    OkxResponse<Object> response =
        new OkxResponse<>("req-42", "50011", "Invalid OK-ACCESS-KEY secret-key-123", null);

    OkxException exception = OkxException.fromResponse(response, "secret-key-123");

    assertThat(exception.getMessage()).isEqualTo("Invalid OK-ACCESS-KEY ***");
    assertThat(exception.getCode()).isEqualTo(50011);
    assertThat(exception.getRequestId()).isEqualTo("req-42");
    assertThat(exception.getTransportState()).isEqualTo(TransportState.BUSINESS_ERROR);
    assertThat(exception.getRetryClassification()).isEqualTo(RetryClassification.NON_RETRYABLE);
    assertThat(exception.getMessage()).doesNotContain("secret-key-123");
    assertThat(exception.toString()).doesNotContain("secret-key-123");
  }

  @Test
  public void testFromResponseClassifiesRateLimitAsRetryable() {
    OkxResponse<Object> response =
        new OkxResponse<>("req-1", "429", "Too Many Requests, requests too frequent", null);

    OkxException exception = OkxException.fromResponse(response);

    assertThat(exception.getRetryClassification()).isEqualTo(RetryClassification.RETRYABLE);
  }

  @Test
  public void testFromResponseClassifiesSystemErrorAsRetryable() {
    OkxResponse<Object> response = new OkxResponse<>("req-2", "500", "System error", null);

    OkxException exception = OkxException.fromResponse(response);

    assertThat(exception.getRetryClassification()).isEqualTo(RetryClassification.RETRYABLE);
  }

  @Test
  public void testParseCodeHandlesAbsentAndNonNumericCodes() {
    assertThat(OkxException.parseCode("50011")).isEqualTo(50011);
    assertThat(OkxException.parseCode(null)).isZero();
    assertThat(OkxException.parseCode("abc")).isZero();
    assertThat(OkxException.parseCode(" 51603 ")).isEqualTo(51603);
  }

  @Test
  public void testWithRedactedMessageKeepsStructuredFields() {
    OkxException original =
        OkxException.builder()
            .message("failed with passphrase-hunter2 and api-key-abcdefgh")
            .code(50111)
            .domain("account")
            .endpoint("/api/v5/account/config")
            .requestId("req-9")
            .transportState(TransportState.BUSINESS_ERROR)
            .retryClassification(RetryClassification.NON_RETRYABLE)
            .build();

    OkxException redacted = original.withRedactedMessage("passphrase-hunter2", "api-key-abcdefgh");

    assertThat(redacted.getMessage()).isEqualTo("failed with *** and ***");
    assertThat(redacted.getCode()).isEqualTo(50111);
    assertThat(redacted.getDomain()).isEqualTo("account");
    assertThat(redacted.getEndpoint()).isEqualTo("/api/v5/account/config");
    assertThat(redacted.getRequestId()).isEqualTo("req-9");
    assertThat(redacted.getTransportState()).isEqualTo(TransportState.BUSINESS_ERROR);
    assertThat(redacted.getRetryClassification()).isEqualTo(RetryClassification.NON_RETRYABLE);
  }

  @Test
  public void testWithRedactedMessageReturnsSameInstanceWhenNothingChanges() {
    OkxException original = OkxException.builder().message("clean message").code(1).build();

    assertThat(original.withRedactedMessage("unrelated-secret")).isSameAs(original);
  }

  @Test
  public void testBuilderStructurallyRedactsOkxAccessHeaders() {
    OkxException exception =
        OkxException.builder()
            .message("auth failed OK-ACCESS-KEY: superSecretKey OK-ACCESS-SIGN=otherSecret")
            .code(1)
            .build();

    assertThat(exception.getMessage())
        .isEqualTo("auth failed OK-ACCESS-KEY: *** OK-ACCESS-SIGN: ***");
    assertThat(exception.getMessage()).doesNotContain("superSecretKey", "otherSecret");
  }
}
