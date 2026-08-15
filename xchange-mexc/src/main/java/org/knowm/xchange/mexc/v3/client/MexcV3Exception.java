package org.knowm.xchange.mexc.v3.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import org.knowm.xchange.exceptions.ExchangeException;
import si.mazi.rescu.HttpResponseAware;
import si.mazi.rescu.HttpStatusExceptionSupport;

/**
 * Structured MEXC Spot v3 failure.
 *
 * <p>rescu deserializes the provider error envelope {@code {"code":..., "msg":...}} directly into
 * this type for every declared interface. The exposed message is sanitized through {@link
 * MexcV3Redactor} because provider payloads may echo request parameters.
 */
public class MexcV3Exception extends HttpStatusExceptionSupport implements HttpResponseAware {

  private final int code;
  private final String msg;
  private RetryClassification classification;
  private Map<String, List<String>> headers;

  public MexcV3Exception(@JsonProperty("code") int code, @JsonProperty("msg") String msg) {
    super(MexcV3Redactor.sanitize(msg));
    this.code = code;
    this.msg = MexcV3Redactor.sanitize(msg);
  }

  /** Provider error code, or {@code 0} when the payload carried none. */
  public int getCode() {
    return code;
  }

  /** Provider error message (sanitized). */
  public String getMsg() {
    return msg;
  }

  /** HTTP status of the failed response, when known. */
  public int getHttpStatus() {
    return getHttpStatusCode();
  }

  /**
   * Classifies the failure for caller retry decisions.
   *
   * <p>HTTP semantics follow the documented mapping: 4XX request-side, 401/403 authentication or
   * permission, 418/429 rate limiting, 5XX provider-side. Provider codes refine the classes.
   */
  public RetryClassification getRetryClassification() {
    if (classification != null) {
      return classification;
    }
    int httpStatus = getHttpStatus();
    if (httpStatus == 418 || httpStatus == 429) {
      return RetryClassification.RATE_LIMITED;
    }
    if (httpStatus == 401 || httpStatus == 403 || code == 602) {
      return RetryClassification.AUTHENTICATION;
    }
    if (httpStatus >= 500) {
      return RetryClassification.TRANSPORT;
    }
    switch (code) {
      case 700002: // signature invalid
      case 700003: // timestamp outside recvWindow
      case 700005: // recvWindow out of range
        return RetryClassification.AUTHENTICATION;
      default:
        return RetryClassification.PERMANENT;
    }
  }

  /** Retry-After seconds from 418/429 responses, or {@code null} when absent. */
  public Integer getRetryAfterSeconds() {
    if (headers == null) {
      return null;
    }
    List<String> values = headers.get("Retry-After");
    if (values == null || values.isEmpty()) {
      return null;
    }
    try {
      return Integer.valueOf(values.get(0).trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  @Override
  public void setResponseHeaders(Map<String, List<String>> headers) {
    this.headers = headers;
  }

  @Override
  public Map<String, List<String>> getResponseHeaders() {
    return headers;
  }

  @Override
  public String toString() {
    return "MexcV3Exception{code=" + code + ", httpStatus=" + getHttpStatus() + ", msg='"
        + getMsg() + "'}";
  }

  /** Convenience constructor for programmatic failures without a provider payload. */
  public static MexcV3Exception of(int httpStatus, String message) {
    MexcV3Exception exception = new MexcV3Exception(0, message);
    exception.setHttpStatusCode(httpStatus);
    return exception;
  }

  /**
   * Marks a placement whose transport outcome is unknown (timeout, connection reset, or a
   * transport-classified 5xx provider response).
   *
   * <p>This is caller-side policy applied by the {@code ReplaySafety.PLACEMENT} execution wrapper:
   * the exchange may have accepted the order even though the placement round-trip failed. Callers
   * must reconcile the outcome by client/exchange order id instead of blindly re-issuing the
   * placement.
   */
  public static MexcV3Exception ambiguous(String detail) {
    MexcV3Exception exception = new MexcV3Exception(0, detail);
    exception.classification = RetryClassification.AMBIGUOUS;
    return exception;
  }

  /**
   * Adapts this provider failure to the XChange exception hierarchy for high-level services.
   *
   * @return the mapped exception, never {@code null}.
   */
  public ExchangeException adapt() {
    return MexcV3ErrorAdapter.adapt(this);
  }
}
