package org.knowm.xchange.bitget.uta.v3.account;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

/**
 * Transferable amount of one coin between two account types.
 *
 * <p>{@code GET /api/v3/account/transferable-coins} returns {@code {coin, amount}} per row for the
 * requested {@code fromType}/{@code toType} pair (e.g. {@code uta} to {@code spot}).
 */
@Data
@Builder
@Jacksonized
public class BitgetUtaV3TransferableCoin {

  @JsonProperty("coin")
  private String coin;

  @JsonProperty("amount")
  private BigDecimal amount;
}
