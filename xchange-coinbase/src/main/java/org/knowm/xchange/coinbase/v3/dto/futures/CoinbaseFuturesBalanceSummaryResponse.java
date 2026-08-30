package org.knowm.xchange.coinbase.v3.dto.futures;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

/**
 * Response envelope for Coinbase's CFM balance-summary endpoint.
 *
 * <p>The API places all monetary values below {@code balance_summary}; each nested amount retains
 * the response currency. A missing summary is not converted into a synthetic zero balance.
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class CoinbaseFuturesBalanceSummaryResponse {

  private final CoinbaseFuturesBalanceSummary balanceSummary;

  @JsonCreator
  public CoinbaseFuturesBalanceSummaryResponse(
      @JsonProperty("balance_summary") CoinbaseFuturesBalanceSummary balanceSummary) {
    this.balanceSummary = balanceSummary;
  }

  @Override
  public String toString() {
    return "CoinbaseFuturesBalanceSummaryResponse [balanceSummary=" + balanceSummary + "]";
  }
}
