package org.knowm.xchange.kraken.dto.trade.results;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.knowm.xchange.kraken.dto.KrakenResult;
import org.knowm.xchange.kraken.dto.trade.KrakenAddOrderBatchResponse;

public class KrakenAddOrderBatchResult extends KrakenResult<KrakenAddOrderBatchResponse> {

  /**
   * Constructor
   *
   * @param result
   * @param error
   */
  public KrakenAddOrderBatchResult(
      @JsonProperty("result") KrakenAddOrderBatchResponse result, @JsonProperty("error") String[] error) {

    super(result, error);
  }
}
