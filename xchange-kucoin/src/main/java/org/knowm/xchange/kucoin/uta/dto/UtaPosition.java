package org.knowm.xchange.kucoin.uta.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import lombok.Data;

/**
 * UTA futures position.
 *
 * <p>{@code size} is in contracts (positive long, negative short). {@code creationTime} is in
 * nanoseconds.
 */
@Data
public class UtaPosition {

  @JsonProperty("symbol")
  private String symbol;

  @JsonProperty("id")
  private String id;

  @JsonProperty("marginMode")
  private String marginMode;

  @JsonProperty("size")
  private BigDecimal size;

  @JsonProperty("entryPrice")
  private BigDecimal entryPrice;

  @JsonProperty("positionValue")
  private BigDecimal positionValue;

  @JsonProperty("markPrice")
  private BigDecimal markPrice;

  @JsonProperty("leverage")
  private BigDecimal leverage;

  @JsonProperty("unrealizedPnL")
  private BigDecimal unrealizedPnL;

  @JsonProperty("realizedPnL")
  private BigDecimal realizedPnL;

  @JsonProperty("initialMargin")
  private BigDecimal initialMargin;

  @JsonProperty("mmr")
  private BigDecimal mmr;

  @JsonProperty("maintenanceMargin")
  private BigDecimal maintenanceMargin;

  @JsonProperty("creationTime")
  private Long creationTime;

  @JsonProperty("liquidationPrice")
  private BigDecimal liquidationPrice;

  @JsonProperty("adlPercentage")
  private BigDecimal adlPercentage;

  @JsonProperty("riskRatio")
  private BigDecimal riskRatio;
}
