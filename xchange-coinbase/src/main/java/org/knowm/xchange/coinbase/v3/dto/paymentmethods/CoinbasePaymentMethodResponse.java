package org.knowm.xchange.coinbase.v3.dto.paymentmethods;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class CoinbasePaymentMethodResponse {

  private final CoinbasePaymentMethod paymentMethod;

  public CoinbasePaymentMethodResponse(
      @JsonProperty("payment_method") CoinbasePaymentMethod paymentMethod) {
    this.paymentMethod = paymentMethod;
  }

  @Override
  public String toString() {
    return "CoinbasePaymentMethodResponse [paymentMethod="
        + (paymentMethod == null ? null : paymentMethod.toString())
        + "]";
  }
}
