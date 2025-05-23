package org.knowm.xchange.coinbase.v3.dto.accounts;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.List;
import lombok.Getter;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CoinbaseAccountsResponse {

  private final List<CoinbaseAccount> accounts;
  @Getter
  private final Boolean hasNext;
  @Getter
  private final String cursor;
  @Getter
  private final Integer size;

  private CoinbaseAccountsResponse(@JsonProperty("accounts") final List<CoinbaseAccount> accounts,
      @JsonProperty("has_next") Boolean hasNext, @JsonProperty("cursor") String cursor,
      @JsonProperty("size") Integer size) {
    this.accounts = accounts;
    this.hasNext = hasNext;
    this.cursor = cursor;
    this.size = size;
  }

  public List<CoinbaseAccount> getAccounts() {
    return Collections.unmodifiableList(accounts);
  }

  @Override
  public String toString() {
    return "hasNext=" + hasNext + ", cursor=" + cursor + ", size=" + size + ", accounts:"
        + accounts;
  }
}
