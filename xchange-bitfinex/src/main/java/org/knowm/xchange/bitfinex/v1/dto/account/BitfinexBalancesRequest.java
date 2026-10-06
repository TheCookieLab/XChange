package org.knowm.xchange.bitfinex.v1.dto.account;

public class BitfinexBalancesRequest extends BitfinexEmptyRequest {

  /** Constructor */
  public BitfinexBalancesRequest() {
    super("/v1/balances");
  }
}
