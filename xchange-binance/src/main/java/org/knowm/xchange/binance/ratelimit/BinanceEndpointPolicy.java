package org.knowm.xchange.binance.ratelimit;

import org.knowm.xchange.binance.config.BinanceProductFamily;
import org.knowm.xchange.binance.error.BinanceRetryClassification;

/**
 * Endpoint policy describing the rate-limit dimensions and retry safety of one Binance operation
 * class.
 *
 * <p>Weight and order-count dimensions come from the Binance exchangeInfo rate-limit definitions
 * and the published endpoint docs. The retry classification drives resilience behavior: order
 * placement is never replay-safe, reads and cancellations are.
 *
 * @param family the product family that owns the endpoint
 * @param operation operation class, e.g. {@code marketData}, {@code orderPlacement}
 * @param weight request-weight units the operation consumes
 * @param orderCount10s order-count units the operation consumes in the 10-second bucket
 * @param retry classification governing retry/reconciliation of this operation
 */
public record BinanceEndpointPolicy(
    BinanceProductFamily family,
    String operation,
    int weight,
    int orderCount10s,
    BinanceRetryClassification retry) {

  public static BinanceEndpointPolicy of(
      BinanceProductFamily family,
      String operation,
      int weight,
      int orderCount10s,
      BinanceRetryClassification retry) {
    return new BinanceEndpointPolicy(family, operation, weight, orderCount10s, retry);
  }

  /** Stable registry key: {@code family/operation}. */
  public String key() {
    return family.getId() + "/" + operation;
  }
}
