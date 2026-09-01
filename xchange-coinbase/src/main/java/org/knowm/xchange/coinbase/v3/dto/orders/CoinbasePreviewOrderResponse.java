package org.knowm.xchange.coinbase.v3.dto.orders;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Current-schema response from Coinbase's order-preview endpoint.
 *
 * <p>The provider requires the {@code errs} array even when the preview is accepted. Its null
 * value is deliberately preserved so a missing or explicit-null field cannot be mistaken for an
 * accepted preview. An empty array is the only successful preview result. The optional preview id
 * is preserved for callers that correlate a preview with a subsequent order submission.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class CoinbasePreviewOrderResponse {

  private final List<String> errs;
  private final String previewId;

  /**
   * Creates a preview response from the provider payload.
   *
   * @param errs required provider failure-reason array; null means absent or explicitly null
   * @param previewId optional provider preview identifier
   */
  @JsonCreator
  public CoinbasePreviewOrderResponse(
      @JsonProperty("errs") List<String> errs, @JsonProperty("preview_id") String previewId) {
    this.errs = errs;
    this.previewId = previewId;
  }

  /** Returns the provider failure-reason array, preserving absent/null values. */
  public List<String> getErrs() {
    return errs;
  }

  /** Returns the optional provider preview identifier. */
  public String getPreviewId() {
    return previewId;
  }

  /** Returns true only when Coinbase explicitly supplied an empty failure-reason array. */
  public boolean isSuccessful() {
    return errs != null && errs.isEmpty();
  }

  @Override
  public String toString() {
    return "CoinbasePreviewOrderResponse[errs=" + errs + ", previewId=" + previewId + "]";
  }
}
