package org.knowm.xchange.bitget.uta.v3.trade;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

/**
 * Bitget UTA v3 position.
 *
 * <p>{@code GET /api/v3/position/current-position} returns positions with {@code positionStatus:
 * normal}. Wire enums: holdMode {@code one_way_mode|hedge_mode}; posSide {@code long|short};
 * marginMode {@code crossed|isolated}. Timestamps are Unix milliseconds as decimal strings.
 */
@Data
@Builder
@Jacksonized
public class BitgetUtaV3Position {

  @JsonProperty("category")
  private String category;

  @JsonProperty("symbol")
  private String symbol;

  @JsonProperty("marginCoin")
  private String marginCoin;

  @JsonProperty("holdMode")
  private String holdMode;

  @JsonProperty("posSide")
  private String posSide;

  @JsonProperty("marginMode")
  private String marginMode;

  @JsonProperty("positionBalance")
  private BigDecimal positionBalance;

  @JsonProperty("available")
  private BigDecimal available;

  @JsonProperty("frozen")
  private BigDecimal frozen;

  @JsonProperty("total")
  private BigDecimal total;

  @JsonProperty("leverage")
  private String leverage;

  @JsonProperty("curRealisedPnl")
  private BigDecimal curRealisedPnl;

  @JsonProperty("avgPrice")
  private BigDecimal avgPrice;

  @JsonProperty("positionStatus")
  private String positionStatus;

  @JsonProperty("unrealisedPnl")
  private BigDecimal unrealisedPnl;

  @JsonProperty("liquidationPrice")
  private BigDecimal liquidationPrice;

  @JsonProperty("mmr")
  private BigDecimal mmr;

  @JsonProperty("profitRate")
  private BigDecimal profitRate;

  @JsonProperty("markPrice")
  private BigDecimal markPrice;

  @JsonProperty("breakEvenPrice")
  private BigDecimal breakEvenPrice;

  @JsonProperty("totalFunding")
  private BigDecimal totalFunding;

  @JsonProperty("openFeeTotal")
  private BigDecimal openFeeTotal;

  @JsonProperty("closeFeeTotal")
  private BigDecimal closeFeeTotal;

  @JsonProperty("cashDividend")
  private BigDecimal cashDividend;

  @JsonProperty("createdTime")
  private String createdTime;

  @JsonProperty("updatedTime")
  private String updatedTime;
}
