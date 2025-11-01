package org.knowm.xchange.coinbase.v3.dto.paymentmethods;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.List;
import lombok.Getter;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CoinbasePaymentMethodsResponse {

  @Getter
  private final List<CoinbasePaymentMethod> paymentMethods;

  public CoinbasePaymentMethodsResponse(
      @JsonProperty("payment_methods") List<CoinbasePaymentMethod> paymentMethods) {
    this.paymentMethods = paymentMethods == null ? Collections.emptyList() : Collections.unmodifiableList(paymentMethods);
  }

  @Override
  public String toString() {
    return "payment_methods:" + paymentMethods;
  }
}
