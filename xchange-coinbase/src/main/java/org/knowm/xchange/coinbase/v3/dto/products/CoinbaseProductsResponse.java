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

  /**
   * Constructs a products response; the {@code products} member is used by Jackson for wire
   * deserialization and by tests/offline catalog builders for deterministic construction.
   */
  public CoinbaseProductsResponse(@JsonProperty("products") List<CoinbaseProductResponse> products) {
    this.products = products == null ? null : Collections.unmodifiableList(products);
  }

  @Override
  public String toString() {
    return "CoinbaseProductsResponse [products:" + products + "]";
  }
}
