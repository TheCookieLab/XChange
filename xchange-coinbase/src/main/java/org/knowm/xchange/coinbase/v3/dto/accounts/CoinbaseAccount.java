package org.knowm.xchange.coinbase.v3.dto.accounts;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class CoinbaseAccount {

  private final String uuid;
  private final String name;
  private final String currency;
  private final CoinbaseAmount balance;
  /** Funds on hold. {@code null} means the response omitted it, which does not prove a zero hold. */
  private final CoinbaseAmount hold;

  @JsonCreator
  CoinbaseAccount(@JsonProperty("uuid") String uuid, @JsonProperty("name") String name,
      @JsonProperty("currency") String currency,
      @JsonProperty("available_balance") CoinbaseAmount balance,
      @JsonProperty("hold") CoinbaseAmount hold) {
    this.uuid = uuid;
    this.name = name;
    this.currency = currency;
    this.balance = balance;
    this.hold = hold;
  }

  @Override
  public String toString() {
    return "CoinbaseAccount [uuid=" + uuid + ", name=" + name + ", currency=" + currency
        + ", balance=" + balance + ", hold=" + hold + "]";
  }

}
