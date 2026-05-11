package org.knowm.xchange.deribit.v2.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import si.mazi.rescu.HttpStatusExceptionSupport;

/** Exception payload returned by Deribit JSON-RPC HTTP error responses. */
@Getter
public class DeribitException extends HttpStatusExceptionSupport {

  private static final String FALLBACK_MESSAGE = "Operation failed without any error message";

  private final DeribitError error;

  /**
   * Creates an exception from Deribit's optional JSON-RPC {@code error} object.
   *
   * <p>Deribit gateway failures can return non-JSON-RPC bodies that Jackson maps without an {@code
   * error} payload, so this constructor must preserve the HTTP exception instead of failing during
   * deserialization.
   *
   * @param error Deribit's JSON-RPC error object, or {@code null} when the response body does not
   *     contain one.
   */
  public DeribitException(@JsonProperty("error") DeribitError error) {
    super(formatMessage(error));
    this.error = error;
  }

  private static String formatMessage(DeribitError error) {
    if (error == null) {
      return FALLBACK_MESSAGE;
    }

    Object data = error.getData();
    String message = error.getCode() + ": " + error.getMessage();
    return data == null ? message : message + ", " + data;
  }
}
