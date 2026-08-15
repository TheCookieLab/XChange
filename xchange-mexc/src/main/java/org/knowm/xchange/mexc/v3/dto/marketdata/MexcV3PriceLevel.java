package org.knowm.xchange.mexc.v3.dto.marketdata;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.List;

/**
 * One price level of a MEXC Spot v3 order book.
 *
 * <p>Wire form is a positional pair {@code [price, quantity]} with string values; quantities are
 * absolute at the level and {@code "0"} removes the level in delta streams.
 */
public class MexcV3PriceLevel {

  private final String price;
  private final String quantity;

  @JsonCreator
  public MexcV3PriceLevel(List<String> values) {
    if (values == null || values.size() < 2) {
      throw new IllegalArgumentException(
          "Malformed MEXC price level: expected [price, quantity], got "
              + (values == null ? "null" : values));
    }
    this.price = values.get(0);
    this.quantity = values.get(1);
  }

  public String getPrice() {
    return price;
  }

  public String getQuantity() {
    return quantity;
  }
}
