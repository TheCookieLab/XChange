package org.knowm.xchange.coinbase.v2.dto.marketdata;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

public class CoinbaseFiatCurrencyData {

  private List<CoinbaseFiatCurrency> data;

  public List<CoinbaseFiatCurrency> getData() {
    return Collections.unmodifiableList(data);
  }

  public void setData(List<CoinbaseFiatCurrency> data) {
    this.data = data;
  }

  public static class CoinbaseFiatCurrency {

    private final String name;
    private final String id;
    private final BigDecimal minSize;

    @JsonCreator
    public CoinbaseFiatCurrency(@JsonProperty("name") String name, @JsonProperty("id") String id,
        @JsonProperty("min_size") BigDecimal minSize) {
      this.name = name;
      this.id = id;
      this.minSize = minSize;
    }

    public String getName() {
      return name;
    }

    public String getId() {
      return id;
    }

    public BigDecimal getMinSize() {
      return minSize;
    }

    @Override
    public int hashCode() {
      return id.hashCode();
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
      CoinbaseFiatCurrency other = (CoinbaseFiatCurrency) obj;
      return id.equals(other.id);
    }

    @Override
    public String toString() {
      return id + " (" + name + ")";
    }
  }
}
