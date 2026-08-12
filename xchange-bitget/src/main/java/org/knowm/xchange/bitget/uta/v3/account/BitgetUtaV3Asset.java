package org.knowm.xchange.bitget.uta.v3.account;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

/**
 * Unified UTA account balance for one coin.
 *
 * <p>{@code GET /api/v3/account/assets} returns one object per coin with the unified (cross-mode)
 * balance semantics: equity = balance + frozen margin + unrealized PnL; available = balance +
 * unrealized PnL; debt = ABS(min(balance + unrealized PnL, 0)).
 */
@Data
@Builder
@Jacksonized
public class BitgetUtaV3Asset {

  @JsonProperty("coin")
  private String coin;

  /** Balance excluding unrealized PnL (available to trade). */
  @JsonProperty("available")
  private BigDecimal available;

  @JsonProperty("locked")
  private BigDecimal locked;

  /** Frozen margin (futures positions). */
  @JsonProperty("frozen")
  private BigDecimal frozen;

  @JsonProperty("margin")
  private BigDecimal margin;

  @JsonProperty("debts")
  private BigDecimal debts;

  @JsonProperty("bonus")
  private BigDecimal bonus;

  /** Equity = balance + frozen margin + unrealized PnL. */
  @JsonProperty("equity")
  private BigDecimal equity;

  @JsonProperty("usdValue")
  private BigDecimal usdValue;

  @JsonProperty("unrealizedPnl")
  private BigDecimal unrealizedPnl;
}
