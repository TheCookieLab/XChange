package org.knowm.xchange.coinbasederivatives;

import java.math.BigDecimal;
import java.util.List;

/**
 * Lossless placement result for reconciliation.
 *
 * <p>The {@code label} is neither unique nor idempotent. A caller must use provider order IDs and
 * the request correlation ID when recovering an ambiguous workflow.
 */
public record CoinbaseDerivativesPlacementResult(
    String primaryOrderId,
    List<String> relatedOrderIds,
    long requestCorrelationId,
    String instrumentName,
    String side,
    String orderType,
    BigDecimal amount,
    BigDecimal price,
    boolean reduceOnly,
    String label,
    String providerStatus) {

  public CoinbaseDerivativesPlacementResult {
    relatedOrderIds = relatedOrderIds == null ? List.of() : List.copyOf(relatedOrderIds);
  }
}
