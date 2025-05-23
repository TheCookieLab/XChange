package org.knowm.xchange.coinbase.v3.dto.accounts;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CoinbaseAccount {

  private final String uuid;
  private final String name;
  private final String currency;
  private final CoinbaseAmount balance;

  @JsonCreator
  CoinbaseAccount(@JsonProperty("uuid") String uuid, @JsonProperty("name") String name,
      @JsonProperty("currency") String currency,
      @JsonProperty("available_balance") CoinbaseAmount balance) {
    this.uuid = uuid;
    this.name = name;
    this.currency = currency;
    this.balance = balance;
  }

  public String getUuid() {
    return uuid;
  }

  public String getName() {
    return name;
  }

  public CoinbaseAmount getBalance() {
    return balance;
  }

  public String getCurrency() {
    return this.currency;
  }

  @Override
  public String toString() {
    return "CoinbaseAccount [uuid=" + uuid + ", name=" + name + ", currency=" + currency
        + ", balance=" + balance + "]";
  }

}
