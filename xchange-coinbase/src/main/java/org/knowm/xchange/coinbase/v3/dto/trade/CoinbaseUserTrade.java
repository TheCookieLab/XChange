package org.knowm.xchange.coinbase.v3.dto.trade;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.knowm.xchange.dto.trade.UserTrade;

/**
 * Coinbase user trade with both Advanced Trade fill identity namespaces.
 *
 * <p>{@link #getId()} is Coinbase's {@code trade_id}, which is the identity accepted by generic
 * XChange trade filters. {@link #getEntryId()} is Coinbase's immutable ledger entry identity and
 * must not be sent back as a trade identifier.
 */
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class CoinbaseUserTrade extends UserTrade {

  private static final long serialVersionUID = -5792224731149534854L;

  /** Coinbase Advanced Trade ledger entry identity for this fill. */
  private String entryId;
}
