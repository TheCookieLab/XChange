package org.knowm.xchange.kraken.dto.trade.results;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.knowm.xchange.kraken.dto.KrakenResult;
import org.knowm.xchange.kraken.dto.trade.KrakenAmendOrderResponse;

public class KrakenAmendOrderResult extends KrakenResult<KrakenAmendOrderResponse> {

  /**
   * Constructor
   *
   * @param result
   * @param error
   */
  public KrakenAmendOrderResult(
      @JsonProperty("result") KrakenAmendOrderResponse result, @JsonProperty("error") String[] error) {

    super(result, error);
  }
}
