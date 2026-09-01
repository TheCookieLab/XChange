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
  private final BigDecimal liquidationBufferPercentage;
  private final BigDecimal totalHold;
  private final BigDecimal futuresBuyingPower;

  /**
   * Deserializes a margin-window measure without substituting missing values.
   *
   * @param marginWindowType exchange window classification
   * @param marginLevel exchange margin level
   * @param initialMargin initial margin requirement
   * @param maintenanceMargin maintenance margin requirement
   * @param liquidationBuffer current liquidation buffer
   * @param totalHold aggregate order hold
   * @param futuresBuyingPower available futures buying power
   */
  @JsonCreator
  private CoinbaseMarginWindowMeasure(
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
    this.liquidationBufferPercentage = null;
    this.totalHold = totalHold;
    this.futuresBuyingPower = futuresBuyingPower;
  }

  /**
   * Creates a measure using Coinbase's current {@code liquidation_buffer} wire field.
   *
   * <p>A named factory avoids a source-ambiguous constructor overload for legacy callers that
   * pass {@code null} as the deprecated liquidation-buffer percentage.
   *
   * @param marginWindowType exchange window classification
   * @param marginLevel exchange margin level
   * @param initialMargin initial margin requirement
   * @param maintenanceMargin maintenance margin requirement
   * @param liquidationBuffer current liquidation buffer
   * @param totalHold aggregate order hold
   * @param futuresBuyingPower available futures buying power
   * @return current-schema margin-window measure
   */
  public static CoinbaseMarginWindowMeasure fromCurrentSchema(
      String marginWindowType,
      String marginLevel,
      BigDecimal initialMargin,
      BigDecimal maintenanceMargin,
      String liquidationBuffer,
      BigDecimal totalHold,
      BigDecimal futuresBuyingPower) {
    return new CoinbaseMarginWindowMeasure(
        marginWindowType,
        marginLevel,
        initialMargin,
        maintenanceMargin,
        liquidationBuffer,
        totalHold,
        futuresBuyingPower);
  }

  /**
   * Preserves the pre-1.0.2 margin-window construction contract.
   *
   * @deprecated the current API exposes {@code liquidation_buffer}, not a window percentage
   */
  @Deprecated
  public CoinbaseMarginWindowMeasure(
      String marginWindowType,
      String marginLevel,
      BigDecimal initialMargin,
      BigDecimal maintenanceMargin,
      BigDecimal liquidationBufferPercentage,
      BigDecimal totalHold,
      BigDecimal futuresBuyingPower) {
    this.marginWindowType = marginWindowType;
    this.marginLevel = marginLevel;
    this.initialMargin = initialMargin;
    this.maintenanceMargin = maintenanceMargin;
    this.liquidationBuffer = null;
    this.liquidationBufferPercentage = liquidationBufferPercentage;
    this.totalHold = totalHold;
    this.futuresBuyingPower = futuresBuyingPower;
  }

  @Override
  public String toString() {
    return "CoinbaseMarginWindowMeasure [marginWindowType=" + marginWindowType
        + ", marginLevel=" + marginLevel + "]";
  }
}
