package org.knowm.xchange.coinbase.v3.dto.accounts;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CoinbaseAccountResponse {

  @Getter
  private final CoinbaseAccount account;

  public CoinbaseAccountResponse(@JsonProperty("account") CoinbaseAccount account) {
    this.account = account;
  }

  @Override
  public String toString() {
    return "" + account;
  }
}
