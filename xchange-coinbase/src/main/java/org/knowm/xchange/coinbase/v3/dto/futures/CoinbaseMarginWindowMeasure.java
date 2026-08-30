package org.knowm.xchange.coinbase.v3.dto.futures;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import lombok.Getter;

/**
 * Margin-window measure from the CFM balance summary.
 *
 * <p>{@code liquidation_buffer} is the API's decimal string. It is distinct from the response-level
 * liquidation-buffer percentage and is exposed with its exact wire meaning.
 *
 * @since 1.0.2
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class CoinbaseMarginWindowMeasure {
  private final String marginWindowType;
  private final String marginLevel;
  private final BigDecimal initialMargin;
  private final BigDecimal maintenanceMargin;
  private final BigDecimal liquidationBuffer;
  private final BigDecimal totalHold;
  private final BigDecimal futuresBuyingPower;

  /** Deserializes a margin-window measure without substituting missing values. */
  @JsonCreator
  public CoinbaseMarginWindowMeasure(
      @JsonProperty("margin_window_type") String marginWindowType,
      @JsonProperty("margin_level") String marginLevel,
      @JsonProperty("initial_margin") BigDecimal initialMargin,
      @JsonProperty("maintenance_margin") BigDecimal maintenanceMargin,
      @JsonProperty("liquidation_buffer") String liquidationBuffer,
      @JsonProperty("total_hold") BigDecimal totalHold,
      @JsonProperty("futures_buying_power") BigDecimal futuresBuyingPower) {
    this.marginWindowType = marginWindowType;
    this.marginLevel = marginLevel;
    this.initialMargin = initialMargin;
    this.maintenanceMargin = maintenanceMargin;
    this.liquidationBuffer = liquidationBuffer == null ? null : new BigDecimal(liquidationBuffer);
    this.totalHold = totalHold;
    this.futuresBuyingPower = futuresBuyingPower;
  }
}
