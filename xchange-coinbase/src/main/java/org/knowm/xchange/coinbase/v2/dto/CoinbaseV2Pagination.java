package org.knowm.xchange.coinbase.v2.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

/**
 * Coinbase API v2 pagination object.
 *
 * <p>Used by v2 list endpoints (for example accounts and transactions). Coinbase uses cursor-like
 * pagination where {@code starting_after}/{@code ending_before} refer to object ids.</p>
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class CoinbaseV2Pagination {

  private final String endingBefore;
  private final String startingAfter;
  private final Integer limit;
  private final String order;
  private final String previousUri;
  private final String nextUri;

  @JsonCreator
  public CoinbaseV2Pagination(
      @JsonProperty("ending_before") String endingBefore,
      @JsonProperty("starting_after") String startingAfter,
      @JsonProperty("limit") Integer limit,
      @JsonProperty("order") String order,
      @JsonProperty("previous_uri") String previousUri,
      @JsonProperty("next_uri") String nextUri) {
    this.endingBefore = endingBefore;
    this.startingAfter = startingAfter;
    this.limit = limit;
    this.order = order;
    this.previousUri = previousUri;
    this.nextUri = nextUri;
  }

  @Override
  public String toString() {
    return "CoinbaseV2Pagination [endingBefore=" + endingBefore + ", startingAfter=" + startingAfter
        + ", limit=" + limit + ", order=" + order + ", previousUri=" + previousUri + ", nextUri="
        + nextUri + "]";
  }
}

