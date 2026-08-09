package org.knowm.xchange.kraken.dto.trade.results;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.knowm.xchange.kraken.dto.KrakenResult;
import org.knowm.xchange.kraken.dto.trade.KrakenCancelAllOrdersAfterResponse;

public class KrakenCancelAllOrdersAfterResult extends KrakenResult<KrakenCancelAllOrdersAfterResponse> {

  /**
   * Constructor
   *
   * @param result
   * @param error
   */
  public KrakenCancelAllOrdersAfterResult(
      @JsonProperty("result") KrakenCancelAllOrdersAfterResponse result,
      @JsonProperty("error") String[] error) {

    super(result, error);
  }
}
