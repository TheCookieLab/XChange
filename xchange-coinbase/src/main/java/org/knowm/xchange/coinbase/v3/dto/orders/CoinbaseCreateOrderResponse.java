package org.knowm.xchange.coinbase.v3.dto.orders;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class CoinbaseCreateOrderResponse {

  private final boolean success;
  private final SuccessResponse successResponse;
  private final ErrorResponse errorResponse;

  @JsonCreator
  public CoinbaseCreateOrderResponse(
      @JsonProperty("success") boolean success,
      @JsonProperty("success_response") SuccessResponse successResponse,
      @JsonProperty("error_response") ErrorResponse errorResponse) {
    this.success = success;
    this.successResponse = successResponse;
    this.errorResponse = errorResponse;
  }

  public String getOrderId() {
    return successResponse == null ? null : successResponse.orderId;
  }

  @Getter
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class SuccessResponse {
    private final String orderId;

    @JsonCreator
    public SuccessResponse(@JsonProperty("order_id") String orderId) {
      this.orderId = orderId;
    }
  }

  @Getter
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class ErrorResponse {
    private final String error;
    private final String message;

    @JsonCreator
    public ErrorResponse(
        @JsonProperty("error") String error,
        @JsonProperty("message") String message) {
      this.error = error;
      this.message = message;
    }
  }
}


