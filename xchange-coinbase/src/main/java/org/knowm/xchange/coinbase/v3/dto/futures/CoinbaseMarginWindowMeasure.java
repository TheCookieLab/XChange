package org.knowm.xchange.coinbase.v3.dto.futures;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import lombok.Getter;

/**
 * Represents margin window measure for futures trading.
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class CoinbaseMarginWindowMeasure {

  private final String marginWindowType;
  private final String marginLevel;
  private final BigDecimal initialMargin;
  private final BigDecimal maintenanceMargin;
  private final BigDecimal liquidationBufferPercentage;
  private final BigDecimal totalHold;
  private final BigDecimal futuresBuyingPower;

  @JsonCreator
  public CoinbaseMarginWindowMeasure(
      @JsonProperty("margin_window_type") String marginWindowType,
      @JsonProperty("margin_level") String marginLevel,
      @JsonProperty("initial_margin") BigDecimal initialMargin,
      @JsonProperty("maintenance_margin") BigDecimal maintenanceMargin,
      @JsonProperty("liquidation_buffer_percentage") BigDecimal liquidationBufferPercentage,
      @JsonProperty("total_hold") BigDecimal totalHold,
      @JsonProperty("futures_buying_power") BigDecimal futuresBuyingPower) {
    this.marginWindowType = marginWindowType;
    this.marginLevel = marginLevel;
    this.initialMargin = initialMargin;
    this.maintenanceMargin = maintenanceMargin;
    this.liquidationBufferPercentage = liquidationBufferPercentage;
    this.totalHold = totalHold;
    this.futuresBuyingPower = futuresBuyingPower;
  }

  @Override
  public String toString() {
    return "CoinbaseMarginWindowMeasure [marginWindowType=" + marginWindowType + ", marginLevel=" + marginLevel + "]";
  }
}

