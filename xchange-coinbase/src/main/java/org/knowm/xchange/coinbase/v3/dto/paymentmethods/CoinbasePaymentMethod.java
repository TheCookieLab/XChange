package org.knowm.xchange.coinbase.v3.dto.paymentmethods;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CoinbasePaymentMethod {

  private final String id;
  private final String type;
  private final String name;
  private final String currency;

  @Getter @Setter private Boolean verified;
  @Getter @Setter private Boolean allowBuy;
  @Getter @Setter private Boolean allowSell;
  @Getter @Setter private Boolean allowDeposit;

  @JsonCreator
  public CoinbasePaymentMethod(@JsonProperty("id") String id,
      @JsonProperty("type") String type, @JsonProperty("name") String name,
      @JsonProperty("currency") String currency) {

    this.id = id;
    this.type = type;
    this.name = name;
    this.currency = currency;
  }

  @Override
  public String toString() {
    return "CoinbasePaymentMethod [id="
        + id
        + ", name="
        + name
        + ", type="
        + type
        + ", currency="
        + currency
        + "]";
  }
}
