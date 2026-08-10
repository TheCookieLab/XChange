package org.knowm.xchange.binance.ratelimit;

import static org.knowm.xchange.binance.config.BinanceProductFamily.COINM;
import static org.knowm.xchange.binance.config.BinanceProductFamily.MARGIN;
import static org.knowm.xchange.binance.config.BinanceProductFamily.PORTFOLIO_MARGIN;
import static org.knowm.xchange.binance.config.BinanceProductFamily.SPOT;
import static org.knowm.xchange.binance.config.BinanceProductFamily.USDM;
import static org.knowm.xchange.binance.config.BinanceProductFamily.WALLET_SAPI;
import static org.knowm.xchange.binance.error.BinanceRetryClassification.RECONCILE;
import static org.knowm.xchange.binance.error.BinanceRetryClassification.REPLAY_SAFE;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.knowm.xchange.binance.config.BinanceProductFamily;

/**
 * Registry of endpoint policies describing weight, order-count, and retry safety per Binance
 * operation class.
 *
 * <p>Policies are the source of truth for resilience configuration and for the published
 * capability matrix. Operations without an explicit policy fall back to the family default.
 */
public final class BinanceEndpointPolicies {

  public static final String MARKET_DATA = "marketData";
  public static final String ORDER_PLACEMENT = "orderPlacement";
  public static final String ORDER_QUERY = "orderQuery";
  public static final String ORDER_CANCEL = "orderCancel";
  public static final String ACCOUNT = "account";
  public static final String WALLET = "wallet";
  public static final String TRANSFER = "transfer";
  public static final String FUNDING = "funding";

  private static final Map<String, BinanceEndpointPolicy> POLICIES = new ConcurrentHashMap<>();

  static {
    // Spot.
    register(BinanceEndpointPolicy.of(SPOT, MARKET_DATA, 2, 0, REPLAY_SAFE));
    register(BinanceEndpointPolicy.of(SPOT, ORDER_PLACEMENT, 1, 10, RECONCILE));
    register(BinanceEndpointPolicy.of(SPOT, ORDER_QUERY, 2, 0, REPLAY_SAFE));
    register(BinanceEndpointPolicy.of(SPOT, ORDER_CANCEL, 1, 10, REPLAY_SAFE));
    register(BinanceEndpointPolicy.of(SPOT, ACCOUNT, 10, 0, REPLAY_SAFE));

    // Wallet/SAPI and transfers.
    register(BinanceEndpointPolicy.of(WALLET_SAPI, WALLET, 1, 0, REPLAY_SAFE));
    register(BinanceEndpointPolicy.of(WALLET_SAPI, TRANSFER, 1, 0, REPLAY_SAFE));
    register(BinanceEndpointPolicy.of(SPOT, TRANSFER, 1, 0, REPLAY_SAFE));

    // Margin (spot host, SAPI-adjacent).
    register(BinanceEndpointPolicy.of(MARGIN, MARKET_DATA, 1, 0, REPLAY_SAFE));
    register(BinanceEndpointPolicy.of(MARGIN, ORDER_PLACEMENT, 1, 10, RECONCILE));
    register(BinanceEndpointPolicy.of(MARGIN, ORDER_QUERY, 1, 0, REPLAY_SAFE));
    register(BinanceEndpointPolicy.of(MARGIN, ORDER_CANCEL, 1, 10, REPLAY_SAFE));
    register(BinanceEndpointPolicy.of(MARGIN, ACCOUNT, 10, 0, REPLAY_SAFE));

    // USDⓈ-M futures.
    register(BinanceEndpointPolicy.of(USDM, MARKET_DATA, 2, 0, REPLAY_SAFE));
    register(BinanceEndpointPolicy.of(USDM, ORDER_PLACEMENT, 0, 10, RECONCILE));
    register(BinanceEndpointPolicy.of(USDM, ORDER_QUERY, 2, 0, REPLAY_SAFE));
    register(BinanceEndpointPolicy.of(USDM, ORDER_CANCEL, 1, 10, REPLAY_SAFE));
    register(BinanceEndpointPolicy.of(USDM, ACCOUNT, 5, 0, REPLAY_SAFE));
    register(BinanceEndpointPolicy.of(USDM, FUNDING, 1, 0, REPLAY_SAFE));

    // COIN-M futures.
    register(BinanceEndpointPolicy.of(COINM, MARKET_DATA, 2, 0, REPLAY_SAFE));
    register(BinanceEndpointPolicy.of(COINM, ORDER_PLACEMENT, 0, 10, RECONCILE));
    register(BinanceEndpointPolicy.of(COINM, ORDER_QUERY, 2, 0, REPLAY_SAFE));
    register(BinanceEndpointPolicy.of(COINM, ORDER_CANCEL, 1, 10, REPLAY_SAFE));
    register(BinanceEndpointPolicy.of(COINM, ACCOUNT, 5, 0, REPLAY_SAFE));
    register(BinanceEndpointPolicy.of(COINM, FUNDING, 1, 0, REPLAY_SAFE));

    // Portfolio margin.
    register(BinanceEndpointPolicy.of(PORTFOLIO_MARGIN, ORDER_PLACEMENT, 1, 10, RECONCILE));
    register(BinanceEndpointPolicy.of(PORTFOLIO_MARGIN, ORDER_QUERY, 1, 0, REPLAY_SAFE));
    register(BinanceEndpointPolicy.of(PORTFOLIO_MARGIN, ORDER_CANCEL, 1, 10, REPLAY_SAFE));
    register(BinanceEndpointPolicy.of(PORTFOLIO_MARGIN, ACCOUNT, 5, 0, REPLAY_SAFE));
  }

  private BinanceEndpointPolicies() {}

  /**
   * Returns the policy for a family/operation pair.
   *
   * @return the registered policy, or a family-default {@link BinanceEndpointPolicy} with zero
   *     weight and {@link org.knowm.xchange.binance.error.BinanceRetryClassification#REPLAY_SAFE}
   *     when none is registered.
   */
  public static BinanceEndpointPolicy policy(BinanceProductFamily family, String operation) {
    return POLICIES.getOrDefault(
        family.getId() + "/" + operation,
        BinanceEndpointPolicy.of(family, operation, 0, 0, REPLAY_SAFE));
  }

  /** All registered policies, for the capability matrix and documentation. */
  public static Map<String, BinanceEndpointPolicy> all() {
    return Map.copyOf(POLICIES);
  }

  private static void register(BinanceEndpointPolicy policy) {
    POLICIES.put(policy.key(), policy);
  }
}
