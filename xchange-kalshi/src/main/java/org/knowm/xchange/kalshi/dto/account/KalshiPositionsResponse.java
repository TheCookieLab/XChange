package org.knowm.xchange.kalshi.dto.account;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Paginated positions response ({@code GET /portfolio/positions}); only market-level positions
 * are modeled.
 *
 * @see <a href="https://docs.kalshi.com/api-reference/portfolio/get-positions">Kalshi Get
 *     Positions</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KalshiPositionsResponse(
    @JsonProperty("market_positions") List<KalshiMarketPosition> marketPositions,
    @JsonProperty("cursor") String cursor) {

  /**
   * Market position. {@code positionFp} is the signed YES contract count as a fixed-point count
   * string (negative means net NO exposure, positive means net YES exposure);
   * {@code marketExposureDollars} is the cost of the aggregate market position in dollars.
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record KalshiMarketPosition(
      @JsonProperty("ticker") String ticker,
      @JsonProperty("position_fp") String positionFp,
      @JsonProperty("market_exposure_dollars") String marketExposureDollars) {}
}
