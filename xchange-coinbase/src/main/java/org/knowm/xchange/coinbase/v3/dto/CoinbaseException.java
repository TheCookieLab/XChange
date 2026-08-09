package org.knowm.xchange.coinbase.v3.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.List;
import si.mazi.rescu.HttpStatusExceptionSupport;

/**
 * Exception thrown when the Coinbase Advanced Trade API returns an error response.
 *
 * <p>This exception is automatically deserialized from Coinbase API error responses that contain
 * an "errors" array. The exception message is set to the first error's message, or "Unknown
 * Coinbase error" if no errors are provided.
 *
 * <p>Extends {@link HttpStatusExceptionSupport} to provide HTTP status code information along with
 * the error details.
 *
 * @see CoinbaseError
 */
public class CoinbaseException extends HttpStatusExceptionSupport {

  private final List<CoinbaseError> errors;
  private final RetryClassification retryClassification;

  /**
   * Constructs a new CoinbaseException from the API error response.
   *
   * <p>The exception message is set to the first error's message if the errors list is non-null and
   * non-empty. Otherwise, it defaults to "Unknown Coinbase error".
   *
   * @param errors List of error objects from the Coinbase API response. May be null or empty.
   */
  public CoinbaseException(@JsonProperty("errors") List<CoinbaseError> errors) {
    this(errors, null);
  }

  /**
   * Constructs a new CoinbaseException from the API error response with an explicit retry
   * classification.
   *
   * @param errors List of error objects from the Coinbase API response. May be null or empty.
   * @param retryClassification explicit classification; null derives from the HTTP status code
   */
  @com.fasterxml.jackson.annotation.JsonCreator
  public CoinbaseException(
      @JsonProperty("errors") List<CoinbaseError> errors,
      @JsonProperty("retry_classification") RetryClassification retryClassification) {
    super(errors != null && !errors.isEmpty() ? errors.get(0).message : "Unknown Coinbase error");
    this.errors = errors == null ? Collections.emptyList() : Collections.unmodifiableList(errors);
    this.retryClassification = retryClassification;
  }

  /** The provider error objects from the API response, in wire order. */
  public List<CoinbaseError> getErrors() {
    return errors;
  }

  /**
   * Returns the retry classification, deriving it from the HTTP status code when not explicitly
   * provided: 401/403 authenticate, 429 rate credits, 5xx transient, everything else permanent.
   */
  public RetryClassification getRetryClassification() {
    if (retryClassification != null) {
      return retryClassification;
    }
    return classify(getHttpStatusCode());
  }

  /** Derives a default retry classification from an HTTP status code. */
  public static RetryClassification classify(int httpStatusCode) {
    if (httpStatusCode == 401 || httpStatusCode == 403) {
      return RetryClassification.AUTHENTICATION;
    }
    if (httpStatusCode == 429) {
      return RetryClassification.RATE_CREDIT;
    }
    if (httpStatusCode >= 500 && httpStatusCode < 600) {
      return RetryClassification.TRANSIENT;
    }
    return RetryClassification.PERMANENT;
  }

  /**
   * Represents a single error returned by the Coinbase Advanced Trade API.
   *
   * <p>Each error contains an identifier and a human-readable message describing what went wrong.
   */
  public static class CoinbaseError {

    /** The error identifier, typically a unique code or string used for error categorization. */
    @JsonProperty public final String id;

    /** A human-readable error message describing the issue. */
    @JsonProperty public final String message;

    /**
     * Constructs a new CoinbaseError.
     *
     * @param id The error identifier
     * @param message The error message
     */
    public CoinbaseError(@JsonProperty("id") String id, @JsonProperty("message") String message) {
      this.id = id;
      this.message = message;
    }

    /**
     * Returns a string representation of this error.
     *
     * @return A string in the format "CoinbaseError [id=&lt;id&gt;, message=&lt;message&gt;]"
     */
    @Override
    public String toString() {
      return "CoinbaseError [id=" + id + ", message=" + message + "]";
    }
  }
}

