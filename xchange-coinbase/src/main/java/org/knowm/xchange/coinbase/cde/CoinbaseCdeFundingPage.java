package org.knowm.xchange.coinbase.cde;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Raw observations from Coinbase's public US CDE website MDA source.
 *
 * <p>The source identifies the venue in its URL and each instrument in its rows. It supplies no
 * precision, finality, revision or settlement-boundary guarantee. Keeping a decimal string retains
 * the delivered precision; it does not establish that upstream values are unrounded.
 *
 * @param pagination provider's pagination echo, without a total or continuation guarantee
 * @param results observations in provider order, observed newest first; missing hours are not zero
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CoinbaseCdeFundingPage(
    @JsonProperty("pagination") Pagination pagination,
    @JsonProperty("results") List<Observation> results) {

  /**
   * Pagination metadata echoed by the website; no total result count is supplied.
   *
   * @param resultLimit echoed page size
   * @param resultOffset echoed zero-based offset
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Pagination(
      @JsonProperty("result_limit") Integer resultLimit,
      @JsonProperty("result_offset") Integer resultOffset) {}

  /**
   * A published rate, with unknown finality and unknown relationship to the clearing boundary.
   *
   * @param symbol CDE instrument symbol, e.g. BIPZ30 (not BTC-PERP or an Advanced Trade product id)
   * @param fundingRate decimal rate text before the website's percent formatting and CSV rounding;
   *     for example 0.000022 is 0.0022%, not an annualized rate
   * @param eventTime provider event timestamp, observed as ISO-8601 UTC; not verified settlement time
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Observation(
      @JsonProperty("symbol") String symbol,
      @JsonProperty("funding_rate") String fundingRate,
      @JsonProperty("event_time") String eventTime) {

    /**
     * Parses the published decimal text without binary floating point or percent conversion.
     *
     * @return source decimal with delivered scale preserved, without an accuracy guarantee
     * @throws NumberFormatException for invalid rate text
     */
    public BigDecimal fundingRateDecimal() {
      return new BigDecimal(fundingRate);
    }

    /**
     * Parses the provider timestamp without shifting it to an assumed interval boundary.
     *
     * @return event instant, without settlement or finality inference
     * @throws java.time.format.DateTimeParseException for invalid timestamp text
     */
    public Instant eventTimeInstant() {
      return Instant.parse(eventTime);
    }
  }
}
