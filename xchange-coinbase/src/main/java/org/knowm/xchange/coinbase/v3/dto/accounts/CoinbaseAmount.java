package org.knowm.xchange.coinbase.v3.dto.accounts;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import lombok.Getter;
import org.knowm.xchange.utils.Assert;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CoinbaseAmount {

  @Getter
  private final String currency;
  @Getter
  private final BigDecimal value;
  private final String toString;

  @JsonCreator
  public CoinbaseAmount(@JsonProperty("currency") String currency,
      @JsonProperty("value") BigDecimal value) {
    Assert.notNull(currency, "Null currency");
    Assert.notNull(value, "Null amount");
    this.currency = currency;
    this.value = value;

    toString = String.format("%.8f %s", value, currency);
  }

  @Override
  public int hashCode() {
    return toString().hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    CoinbaseAmount other = (CoinbaseAmount) obj;
    return value.compareTo(other.value) == 0 && currency.equals(other.currency);
  }

  @Override
  public String toString() {
    return toString;
  }
}
