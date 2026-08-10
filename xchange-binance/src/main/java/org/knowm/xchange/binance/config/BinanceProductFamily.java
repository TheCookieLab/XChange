package org.knowm.xchange.binance.config;

/**
 * The Binance product families that XChange exposes through explicit, versioned API boundaries.
 *
 * <p>Each family owns its REST base URL, sandbox URL, and the endpoints that belong to it. Product
 * ownership is explicit: a call must be routed through the client of the family it belongs to
 * rather than through loosely typed exchange parameters.
 */
public enum BinanceProductFamily {

  /** Spot trading, public market data, and the authenticated Spot account/order endpoints. */
  SPOT("spot", "https://api.binance.com", "https://testnet.binance.vision"),

  /** Wallet and SAPI endpoints: deposits, withdrawals, transfers, dust, simple-earn, fiat. */
  WALLET_SAPI("wallet/sapi", "https://api.binance.com", "https://testnet.binance.vision"),

  /** Cross and isolated margin trading, margin account, and margin transfers. */
  MARGIN("margin", "https://api.binance.com", "https://testnet.binance.vision"),

  /** USDⓈ-M futures (linear) REST and WebSocket surface. */
  USDM("usdm", "https://fapi.binance.com", "https://testnet.binancefuture.com"),

  /** COIN-M futures (inverse) REST and WebSocket surface. */
  COINM("coinm", "https://dapi.binance.com", "https://testnet.binancefuture.com"),

  /** Vanilla options. Not yet implemented; selecting this family fails specification validation. */
  OPTIONS("options", "https://eapi.binance.com", null),

  /** Portfolio margin account and trading. */
  PORTFOLIO_MARGIN(
      "portfolio-margin", "https://papi.binance.com", "https://testnet.binancefuture.com");

  private final String id;
  private final String restBaseUrl;
  private final String sandboxRestBaseUrl;

  BinanceProductFamily(String id, String restBaseUrl, String sandboxRestBaseUrl) {
    this.id = id;
    this.restBaseUrl = restBaseUrl;
    this.sandboxRestBaseUrl = sandboxRestBaseUrl;
  }

  /** Stable identifier used in endpoint policies and capability documentation. */
  public String getId() {
    return id;
  }

  /** Production REST base URL for this product family. */
  public String getRestBaseUrl() {
    return restBaseUrl;
  }

  /** Sandbox/testnet REST base URL, or {@code null} when the family has no public testnet. */
  public String getSandboxRestBaseUrl() {
    return sandboxRestBaseUrl;
  }

  /** Whether this product family has an implementation behind it in this module. */
  public boolean isImplemented() {
    return this != OPTIONS;
  }
}
