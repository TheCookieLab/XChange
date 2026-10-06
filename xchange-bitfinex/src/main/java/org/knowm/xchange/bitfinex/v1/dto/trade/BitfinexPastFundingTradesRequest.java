package org.knowm.xchange.bitfinex.v1.dto.trade;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Date;
import org.knowm.xchange.bitfinex.v1.dto.BitfinexAuthenticatedRequest;

public class BitfinexPastFundingTradesRequest extends BitfinexAuthenticatedRequest {

  @JsonProperty("request")
  protected String request;

  @JsonProperty("symbol")
  protected String symbol;

  /** Trades made after this timestamp won’t be returned. */
  @JsonProperty("until")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  protected Date until;

  @JsonProperty("limit_trades")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  protected Integer limitTrades;

  public BitfinexPastFundingTradesRequest(String symbol, Date until, Integer limitTrades) {

    this.request = "/v1/mytrades_funding";
    this.symbol = symbol;
    this.until = until;
    this.limitTrades = limitTrades;
  }
}
