package org.knowm.xchange.coinbase.v3.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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

  /**
   * Constructs a new CoinbaseException from the API error response.
   *
   * <p>The exception message is set to the first error's message if the errors list is non-null and
   * non-empty. Otherwise, it defaults to "Unknown Coinbase error".
   *
   * @param errors List of error objects from the Coinbase API response. May be null or empty.
   */
  public CoinbaseException(@JsonProperty("errors") List<CoinbaseError> errors) {
    super(errors != null && !errors.isEmpty() ? errors.get(0).message : "Unknown Coinbase error");
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

