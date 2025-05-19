package org.knowm.xchange.coinbase.v2.dto.marketdata;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.List;

public class CoinbaseCryptocurrencyData {

  private List<CoinbaseCryptocurrency> data;

  public List<CoinbaseCryptocurrency> getData() {
    return Collections.unmodifiableList(data);
  }

  public void setData(List<CoinbaseCryptocurrency> data) {
    this.data = data;
  }

  public static class CoinbaseCryptocurrency {

    private final String name;
    private final String code;

    @JsonCreator
    public CoinbaseCryptocurrency(@JsonProperty("name") String name, @JsonProperty("code") String code) {
      this.name = name;
      this.code = code;
    }

    public String getName() {
      return name;
    }

    public String getCode() {
      return code;
    }

    @Override
    public int hashCode() {
      return code.hashCode();
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
      CoinbaseCryptocurrency other = (CoinbaseCryptocurrency) obj;
      return code.equals(other.code);
    }

    @Override
    public String toString() {
      return code + " (" + name + ")";
    }
  }
}
