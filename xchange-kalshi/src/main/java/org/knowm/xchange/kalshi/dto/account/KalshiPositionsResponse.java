package org.knowm.xchange.kalshi.dto.account;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Paginated positions response; only market-level positions are modeled. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KalshiPositionsResponse(
    @JsonProperty("market_positions") List<KalshiMarketPosition> marketPositions,
    @JsonProperty("cursor") String cursor) {

  /**
   * Market position. {@code position} is the signed YES contract count (negative means net NO
   * exposure); {@code marketExposure} is integer cents.
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record KalshiMarketPosition(
      @JsonProperty("ticker") String ticker,
      @JsonProperty("position") Long position,
      @JsonProperty("market_exposure") Long marketExposure) {}
}
