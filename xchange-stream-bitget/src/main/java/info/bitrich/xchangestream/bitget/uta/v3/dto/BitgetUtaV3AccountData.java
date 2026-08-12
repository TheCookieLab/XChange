package info.bitrich.xchangestream.bitget.uta.v3.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

/**
 * Bitget UTA v3 account push payload ({@code {totalEquity, effEquity, mmr, imr, mgnRatio,
 * positionMgnRatio, unrealisedPnL, coin: [...]}}).
 *
 * <p>Pushed on first subscription and on balance/fill/settlement changes. Equity fields are
 * USD-converted values.
 *
 * @since 5.1.0
 */
@Data
@Builder
@Jacksonized
public class BitgetUtaV3AccountData {

  @JsonProperty("totalEquity")
  private BigDecimal totalEquity;

  @JsonProperty("effEquity")
  private BigDecimal effectiveEquity;

  @JsonProperty("mmr")
  private BigDecimal maintenanceMarginRequirement;

  @JsonProperty("imr")
  private BigDecimal initialMarginRequirement;

  @JsonProperty("mgnRatio")
  private BigDecimal marginRatio;

  @JsonProperty("positionMgnRatio")
  private BigDecimal positionMarginRatio;

  @JsonProperty("unrealisedPnL")
  private BigDecimal unrealisedPnl;

  @JsonProperty("coin")
  private List<BitgetUtaV3CoinData> coins;

  /**
   * Per-coin balance entry of an account push.
   *
   * @since 5.1.0
   */
  @Data
  @Builder
  @Jacksonized
  public static class BitgetUtaV3CoinData {

    @JsonProperty("coin")
    private String coin;

    @JsonProperty("balance")
    private BigDecimal balance;

    @JsonProperty("locked")
    private BigDecimal locked;

    @JsonProperty("equity")
    private BigDecimal equity;

    @JsonProperty("usdValue")
    private BigDecimal usdValue;

    @JsonProperty("available")
    private BigDecimal available;

    @JsonProperty("borrow")
    private BigDecimal borrow;

    @JsonProperty("debts")
    private BigDecimal debts;

    @JsonProperty("bonus")
    private BigDecimal bonus;
  }
}
