package info.bitrich.xchangestream.bitget.uta.v3.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

/**
 * Bitget UTA v3 position push payload.
 *
 * <p>Pushed on first subscription and on open/close/modify of futures close-position orders. {@code
 * size = available + frozen}; {@code positionStatus} is {@code opening} or {@code ended}.
 *
 * @since 5.1.0
 */
@Data
@Builder
@Jacksonized
public class BitgetUtaV3PositionData {

  @JsonProperty("symbol")
  private String symbol;

  @JsonProperty("leverage")
  private String leverage;

  @JsonProperty("openFeeTotal")
  private BigDecimal openFeeTotal;

  @JsonProperty("mmr")
  private BigDecimal mmr;

  @JsonProperty("breakEvenPrice")
  private BigDecimal breakEvenPrice;

  @JsonProperty("available")
  private BigDecimal available;

  @JsonProperty("liqPrice")
  private BigDecimal liquidationPrice;

  /** {@code crossed} or {@code isolated}. */
  @JsonProperty("marginMode")
  private String marginMode;

  @JsonProperty("unrealisedPnl")
  private BigDecimal unrealisedPnl;

  @JsonProperty("markPrice")
  private BigDecimal markPrice;

  @JsonProperty("createdTime")
  private String createdTime;

  @JsonProperty("avgPrice")
  private BigDecimal avgPrice;

  @JsonProperty("totalFundingFee")
  private BigDecimal totalFundingFee;

  @JsonProperty("cashDividend")
  private BigDecimal cashDividend;

  @JsonProperty("updatedTime")
  private String updatedTime;

  @JsonProperty("marginCoin")
  private String marginCoin;

  @JsonProperty("frozen")
  private BigDecimal frozen;

  @JsonProperty("profitRate")
  private BigDecimal profitRate;

  @JsonProperty("closeFeeTotal")
  private BigDecimal closeFeeTotal;

  @JsonProperty("marginSize")
  private BigDecimal marginSize;

  @JsonProperty("curRealisedPnl")
  private BigDecimal curRealisedPnl;

  /** Position size; {@code size = available + frozen}. */
  @JsonProperty("size")
  private BigDecimal size;

  /** {@code opening} or {@code ended}. */
  @JsonProperty("positionStatus")
  private String positionStatus;

  /** {@code long} or {@code short}. */
  @JsonProperty("posSide")
  private String posSide;

  /** {@code one_way_mode} or {@code hedge_mode}. */
  @JsonProperty("holdMode")
  private String holdMode;
}
