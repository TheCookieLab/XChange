package org.knowm.xchange.coinbase.v3.dto.orders;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.math.BigDecimal;
import lombok.Getter;

/**
 * Request payload for creating or previewing orders.
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CoinbaseOrderRequest {

  private final String clientOrderId;
  private final String productId;
  private final CoinbaseOrderSide side;
  private final CoinbaseOrderConfiguration orderConfiguration;
  @JsonSerialize(using = ToStringSerializer.class)
  private final BigDecimal leverage;
  private final CoinbaseMarginType marginType;
  private final String retailPortfolioId;
  private final String previewId;
  private final CoinbaseOrderConfiguration attachedOrderConfiguration;
  private final CoinbaseSorPreference sorPreference;
  private final CoinbasePredictionRequestMetadata predictionMetadata;

  @JsonCreator
  public CoinbaseOrderRequest(
      @JsonProperty("client_order_id") String clientOrderId,
      @JsonProperty("product_id") String productId,
      @JsonProperty("side") CoinbaseOrderSide side,
      @JsonProperty("order_configuration") CoinbaseOrderConfiguration orderConfiguration,
      @JsonProperty("leverage") BigDecimal leverage,
      @JsonProperty("margin_type") CoinbaseMarginType marginType,
      @JsonProperty("retail_portfolio_id") String retailPortfolioId,
      @JsonProperty("preview_id") String previewId,
      @JsonProperty("attached_order_configuration") CoinbaseOrderConfiguration attachedOrderConfiguration,
      @JsonProperty("sor_preference") CoinbaseSorPreference sorPreference,
      @JsonProperty("prediction_metadata") CoinbasePredictionRequestMetadata predictionMetadata) {
    this.clientOrderId = clientOrderId;
    this.productId = productId;
    this.side = side;
    this.orderConfiguration = orderConfiguration;
    this.leverage = leverage;
    this.marginType = marginType;
    this.retailPortfolioId = retailPortfolioId;
    this.previewId = previewId;
    this.attachedOrderConfiguration = attachedOrderConfiguration;
    this.sorPreference = sorPreference;
    this.predictionMetadata = predictionMetadata;
  }

  @Override
  public String toString() {
    return "CoinbaseOrderRequest [clientOrderId=" + clientOrderId
        + ", productId=" + productId + ", side=" + side
        + ", orderConfiguration=" + orderConfiguration
        + ", leverage=" + leverage + ", marginType=" + marginType
        + ", retailPortfolioId=" + retailPortfolioId + ", previewId=" + previewId
        + ", attachedOrderConfiguration=" + attachedOrderConfiguration
        + ", sorPreference=" + sorPreference
        + ", predictionMetadata=" + predictionMetadata + "]";
  }
}
