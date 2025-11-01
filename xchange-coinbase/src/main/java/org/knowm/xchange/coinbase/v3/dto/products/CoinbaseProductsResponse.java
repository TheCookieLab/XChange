package org.knowm.xchange.coinbase.v3.dto.products;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.List;
import lombok.Getter;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CoinbaseProductsResponse {

  @Getter
  private final List<CoinbaseProductResponse> products;

  private CoinbaseProductsResponse(@JsonProperty("products") List<CoinbaseProductResponse> products) {
    this.products = products == null ? Collections.emptyList() : Collections.unmodifiableList(products);
  }

  @Override
  public String toString() {
    return "CoinbaseProductsResponse [products:" + products + "]";
  }
}
