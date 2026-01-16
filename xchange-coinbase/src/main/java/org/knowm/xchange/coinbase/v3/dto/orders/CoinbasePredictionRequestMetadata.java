package org.knowm.xchange.coinbase.v3.dto.orders;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Getter;

/**
 * Request metadata for prediction market orders.
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CoinbasePredictionRequestMetadata {

  private final CoinbasePredictionSide predictionSide;

  @JsonCreator
  public CoinbasePredictionRequestMetadata(
      @JsonProperty("prediction_side") CoinbasePredictionSide predictionSide) {
    this.predictionSide = predictionSide;
  }

  @Override
  public String toString() {
    return "CoinbasePredictionRequestMetadata [predictionSide=" + predictionSide + "]";
  }
}
