package org.knowm.xchange.coinbase.v3.dto.orders;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.List;

/** Response returned by Coinbase Advanced Trade batch cancellation. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CoinbaseCancelOrdersResponse {

  private final List<CoinbaseCancelOrderResult> results;

  /**
   * Creates a batch-cancellation response.
   *
   * @param results per-order cancellation results in provider response order
   */
  @JsonCreator
  public CoinbaseCancelOrdersResponse(
      @JsonProperty("results") List<CoinbaseCancelOrderResult> results) {
    this.results = results == null ? Collections.emptyList() : List.copyOf(results);
  }

  /** Returns immutable per-order cancellation results. */
  public List<CoinbaseCancelOrderResult> getResults() {
    return results;
  }
}
