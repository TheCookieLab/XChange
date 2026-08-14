package org.knowm.xchange.okx.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Locale;
import java.util.Objects;
import lombok.Getter;
import org.knowm.xchange.okx.OkxRedaction;
import si.mazi.rescu.HttpStatusExceptionSupport;

/**
 * Structured OKX REST error.
 *
 * <p>Besides the provider {@code code} and {@code message}, an exception may carry optional
 * structured context that raw services fill in when constructing errors from {@link OkxResponse}
 * payloads: the API {@link #getDomain()} and {@link #getEndpoint()}, a safe request identity
 * ({@link #getRequestId()}, e.g. an order's {@code clOrdId}/{@code ordId} or the OKX request {@code
 * id}), the {@link #getTransportState()}, and a {@link #getRetryClassification()}.
 *
 * <p>Backward compatibility: the {@link #OkxException(String, int)} constructor (used by the
 * deprecated {@code org.knowm.xchange.okex} shim and by rescu error-body deserialization) is
 * unchanged. Messages are structurally redacted on construction (OKX authentication header values
 * are masked); value-based redaction of known secrets is applied by {@link
 * #fromResponse(OkxResponse, String...)} and {@link #withRedactedMessage(String...)}.
 */
@Getter
public class OkxException extends HttpStatusExceptionSupport {

  /** Retry safety classification for an OKX error. */
  public enum RetryClassification {
    /** The request may be safely retried (rate limit, system error, transport failure). */
    RETRYABLE,
    /** Retrying cannot succeed without a change of inputs (authentication, parameters). */
    NON_RETRYABLE,
    /** No classification could be determined. */
    UNKNOWN
  }

  /** Transport layer at which the error was observed. */
  public enum TransportState {
    /** The OKX API returned a business error payload (non-zero {@code code}). */
    BUSINESS_ERROR,
    /** A non-2xx HTTP status was returned. */
    HTTP_ERROR,
    /** The failure happened before a response was received (network, timeout). */
    CONNECTION_ERROR,
    /** The transport state could not be determined. */
    UNKNOWN
  }

  private final String message;
  private final int code;
  private final String domain;
  private final String endpoint;
  private final String requestId;
  private final TransportState transportState;
  private final RetryClassification retryClassification;

  public OkxException(@JsonProperty("msg") String message, @JsonProperty("code") int code) {
    this(
        OkxRedaction.mask(message),
        code,
        null,
        null,
        null,
        TransportState.UNKNOWN,
        RetryClassification.UNKNOWN);
  }

  private OkxException(
      String message,
      int code,
      String domain,
      String endpoint,
      String requestId,
      TransportState transportState,
      RetryClassification retryClassification) {
    super(message);
    this.message = message;
    this.code = code;
    this.domain = domain;
    this.endpoint = endpoint;
    this.requestId = requestId;
    this.transportState = transportState;
    this.retryClassification = retryClassification;
  }

  /**
   * Creates a structured exception from a failed {@link OkxResponse}. The response message is
   * redacted against the supplied secrets before it is stored, so credentials can never reach
   * {@link #getMessage()} or {@link #toString()}.
   *
   * @param response the failed OKX response (non-success {@code code})
   * @param secrets credential values (API key, secret key, passphrase) to mask from the message
   * @return a structured exception carrying the provider code, a conservative retry classification,
   *     and the OKX request {@code id} when present
   */
  public static OkxException fromResponse(OkxResponse<?> response, String... secrets) {
    String message = OkxRedaction.mask(response.getMsg(), secrets);
    return builder()
        .message(message)
        .code(parseCode(response.getCode()))
        .requestId(response.getId())
        .transportState(TransportState.BUSINESS_ERROR)
        .retryClassification(classify(message, response.getCode()))
        .build();
  }

  /**
   * Parses an OKX error code string defensively, falling back to {@code 0} for absent or
   * non-numeric codes.
   */
  public static int parseCode(String code) {
    if (code == null) {
      return 0;
    }
    try {
      return Integer.parseInt(code.trim());
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  /**
   * Classifies an error conservatively from its message and code.
   *
   * <p>Rate-limit, system, and unavailability messages plus HTTP 429/5xx statuses are {@link
   * RetryClassification#RETRYABLE}; messages about invalid or missing inputs are {@link
   * RetryClassification#NON_RETRYABLE}; everything else is {@link RetryClassification#UNKNOWN}.
   * Services may override the classification via the builder when they know better.
   */
  public static RetryClassification classify(String message, String code) {
    if (message != null) {
      String m = message.toLowerCase(Locale.ROOT);
      if (m.contains("requests too frequent")
          || m.contains("system error")
          || m.contains("unavailable")) {
        return RetryClassification.RETRYABLE;
      }
      if (m.contains("invalid") || m.contains("does not exist") || m.contains("required")) {
        return RetryClassification.NON_RETRYABLE;
      }
    }
    int parsed = parseCode(code);
    if (parsed == 429 || (parsed >= 500 && parsed <= 599)) {
      return RetryClassification.RETRYABLE;
    }
    return RetryClassification.UNKNOWN;
  }

  /**
   * Returns a copy of this exception with the message redacted against the given secrets, or {@code
   * this} when nothing changed. The copy keeps all structured fields.
   *
   * @param secrets credential values (API key, secret key, passphrase) to mask from the message
   */
  public OkxException withRedactedMessage(String... secrets) {
    String redacted = OkxRedaction.mask(message, secrets);
    if (Objects.equals(redacted, message)) {
      return this;
    }
    return new OkxException(
        redacted, code, domain, endpoint, requestId, transportState, retryClassification);
  }

  @Override
  public String getMessage() {
    return message;
  }

  /**
   * Returns the OKX business error code ({@code 0} when unknown or absent), so callers can branch
   * on structured provider errors without parsing the message.
   *
   * @return the provider error code
   */
  public int getCode() {
    return code;
  }

  @Override
  public String toString() {
    return code + ":" + message;
  }

  /** Mutable builder for {@link OkxException}. */
  public static Builder builder() {
    return new Builder();
  }

  /** Mutable builder for {@link OkxException}. */
  public static final class Builder {

    private String message;
    private int code;
    private String domain;
    private String endpoint;
    private String requestId;
    private TransportState transportState = TransportState.UNKNOWN;
    private RetryClassification retryClassification = RetryClassification.UNKNOWN;

    private Builder() {}

    /** Sets the error message (structurally redacted on build). */
    public Builder message(String message) {
      this.message = message;
      return this;
    }

    /** Sets the provider error code. */
    public Builder code(int code) {
      this.code = code;
      return this;
    }

    /** Sets the API domain, for example {@code account} or {@code trade}. */
    public Builder domain(String domain) {
      this.domain = domain;
      return this;
    }

    /** Sets the endpoint path, for example {@code /api/v5/account/balance}. */
    public Builder endpoint(String endpoint) {
      this.endpoint = endpoint;
      return this;
    }

    /** Sets a safe request identity ({@code clOrdId}/{@code ordId} or OKX request {@code id}). */
    public Builder requestId(String requestId) {
      this.requestId = requestId;
      return this;
    }

    /** Sets the transport state at which the error was observed. */
    public Builder transportState(TransportState transportState) {
      this.transportState = transportState;
      return this;
    }

    /** Sets the retry classification. */
    public Builder retryClassification(RetryClassification retryClassification) {
      this.retryClassification = retryClassification;
      return this;
    }

    /** Builds the exception; the message is structurally redacted. */
    public OkxException build() {
      return new OkxException(
          OkxRedaction.mask(message),
          code,
          domain,
          endpoint,
          requestId,
          transportState,
          retryClassification);
    }
  }
}
