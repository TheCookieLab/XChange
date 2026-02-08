package org.knowm.xchange.coinbase.v2.dto.transactions;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.List;
import org.knowm.xchange.coinbase.v2.dto.CoinbaseV2Pagination;

/**
 * Coinbase API v2 response wrapper for {@code GET /v2/accounts/{account_id}/transactions}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CoinbaseV2TransactionsResponse {

  private final CoinbaseV2Pagination pagination;
  private final List<CoinbaseV2Transaction> data;

  @JsonCreator
  public CoinbaseV2TransactionsResponse(
      @JsonProperty("pagination") CoinbaseV2Pagination pagination,
      @JsonProperty("data") List<CoinbaseV2Transaction> data) {
    this.pagination = pagination;
    this.data = data == null ? Collections.emptyList() : Collections.unmodifiableList(data);
  }

  public CoinbaseV2Pagination getPagination() {
    return pagination;
  }

  public List<CoinbaseV2Transaction> getData() {
    return data;
  }

  @Override
  public String toString() {
    return "CoinbaseV2TransactionsResponse [pagination=" + pagination + ", data=" + data + "]";
  }
}

