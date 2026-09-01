package org.knowm.xchange.coinbase.v3.dto.orders;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.List;

/**
 * Current-schema response from Coinbase order edit and edit-preview endpoints.
 *
 * <p>The HTTP request can complete with status 200 while the operation itself is rejected. Callers
 * must inspect {@link #isSuccess()} and must not treat a false result as an accepted edit.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class CoinbaseEditOrderResponse {

  private final Boolean success;
  private final List<EditOrderError> errors;

  /**
   * Creates a response from the provider payload.
   *
   * @param success whether Coinbase accepted the edit; null means the required field was absent
   * @param errors provider edit or preview failure details, possibly absent
   */
  @JsonCreator
  public CoinbaseEditOrderResponse(
      @JsonProperty("success") Boolean success,
      @JsonProperty("errors") List<EditOrderError> errors) {
    this.success = success;
    this.errors = errors == null ? Collections.emptyList() : List.copyOf(errors);
  }

  /** Returns the nullable provider success field, preserving an absent field as null. */
  public Boolean getSuccess() {
    return success;
  }

  /** Returns whether the provider explicitly accepted the edit. */
  public boolean isSuccess() {
    return Boolean.TRUE.equals(success);
  }

  /** Returns immutable provider failure details. */
  public List<EditOrderError> getErrors() {
    return errors;
  }

  /**
   * One provider failure detail from the edit-order response.
   *
   * <p>Exactly one of the two reason fields is normally populated, depending on whether the
   * endpoint was edit or edit-preview.
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class EditOrderError {

    private final String editFailureReason;
    private final String previewFailureReason;

    /** Creates an edit failure detail from its provider reason fields. */
    @JsonCreator
    public EditOrderError(
        @JsonProperty("edit_failure_reason") String editFailureReason,
        @JsonProperty("preview_failure_reason") String previewFailureReason) {
      this.editFailureReason = editFailureReason;
      this.previewFailureReason = previewFailureReason;
    }

    /** Returns the edit endpoint failure reason, if present. */
    public String getEditFailureReason() {
      return editFailureReason;
    }

    /** Returns the preview endpoint failure reason, if present. */
    public String getPreviewFailureReason() {
      return previewFailureReason;
    }

    @Override
    public String toString() {
      return "EditOrderError[editFailureReason="
          + editFailureReason
          + ", previewFailureReason="
          + previewFailureReason
          + "]";
    }
  }
}
