package org.knowm.xchange.bybit.config;

import static org.knowm.xchange.Exchange.USE_SANDBOX;
import static org.knowm.xchange.bybit.BybitExchange.SPECIFIC_PARAM_TESTNET;

import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.bybit.dto.BybitCategory;

/**
 * Bybit V5 execution environment.
 *
 * <p>Owns the REST base URL and the three WebSocket transports (public market data, private user
 * data, and order-entry "trade") for each environment, per the official Bybit V5 connectivity
 * documentation (https://bybit-exchange.github.io/docs/v5/ws/connect and
 * https://bybit-exchange.github.io/docs/v5/demo).
 *
 * <p>Environment selection is centralized in {@link #resolve(ExchangeSpecification)} so that the
 * REST module and every streaming transport resolve the same contract, and contradictory
 * production/demo/testnet flags fail fast instead of silently rerouting traffic.
 */
public enum BybitEnvironment {

  /** Live trading. */
  PRODUCTION(
      "https://api.bybit.com",
      "wss://stream.bybit.com/v5/public/",
      "wss://stream.bybit.com/v5/private",
      "wss://stream.bybit.com/v5/trade"),

  /**
   * Bybit demo trading. Per the official docs, demo public market data is identical to mainnet and
   * is served from the mainnet public stream host, while demo private streams use {@code
   * stream-demo.bybit.com}. The WebSocket order-entry (trade) transport is NOT available in demo,
   * so {@link #getTradeWebsocketUrl()} returns {@code null}.
   */
  DEMO(
      "https://api-demo.bybit.com",
      "wss://stream.bybit.com/v5/public/",
      "wss://stream-demo.bybit.com/v5/private",
      null),

  /** Bybit testnet. */
  TESTNET(
      "https://api-testnet.bybit.com",
      "wss://stream-testnet.bybit.com/v5/public/",
      "wss://stream-testnet.bybit.com/v5/private",
      "wss://stream-testnet.bybit.com/v5/trade");

  private final String restBaseUrl;
  private final String publicWebsocketBaseUrl;
  private final String privateWebsocketUrl;
  private final String tradeWebsocketUrl;

  BybitEnvironment(
      String restBaseUrl,
      String publicWebsocketBaseUrl,
      String privateWebsocketUrl,
      String tradeWebsocketUrl) {
    this.restBaseUrl = restBaseUrl;
    this.publicWebsocketBaseUrl = publicWebsocketBaseUrl;
    this.privateWebsocketUrl = privateWebsocketUrl;
    this.tradeWebsocketUrl = tradeWebsocketUrl;
  }

  /** REST base URL for this environment, e.g. {@code https://api.bybit.com}. */
  public String getRestBaseUrl() {
    return restBaseUrl;
  }

  /**
   * WebSocket base URL for public market data, without the trailing category segment. The category
   * path segment is appended per subscription via {@link #getPublicWebsocketUrl(BybitCategory)}.
   */
  public String getPublicWebsocketBaseUrl() {
    return publicWebsocketBaseUrl;
  }

  /** WebSocket URL for the authenticated private user-data stream. */
  public String getPrivateWebsocketUrl() {
    return privateWebsocketUrl;
  }

  /**
   * WebSocket URL for the authenticated order-entry ("trade") stream.
   *
   * @return the trade URL, or {@code null} when the environment does not support the trade
   *     transport (demo trading)
   */
  public String getTradeWebsocketUrl() {
    return tradeWebsocketUrl;
  }

  /** Whether this environment supports the WebSocket order-entry (trade) transport. */
  public boolean supportsTradeWebsocket() {
    return tradeWebsocketUrl != null;
  }

  /** Full WebSocket public-market-data URL for the given category, e.g. {@code .../v5/public/linear}. */
  public String getPublicWebsocketUrl(BybitCategory category) {
    return publicWebsocketBaseUrl + category.getValue();
  }

  /**
   * Resolves the environment from the legacy exchange-specific parameters, keeping the historical
   * source surface ({@code USE_SANDBOX}, {@code SPECIFIC_PARAM_TESTNET}) intact:
   *
   * <ul>
   *   <li>{@code USE_SANDBOX=true} selects demo trading;
   *   <li>{@code SPECIFIC_PARAM_TESTNET=true} selects testnet;
   *   <li>otherwise production.
   * </ul>
   *
   * Setting both flags is contradictory and is rejected.
   *
   * @throws IllegalArgumentException when demo and testnet flags are both true
   */
  public static BybitEnvironment resolve(ExchangeSpecification specification) {
    boolean sandbox =
        Boolean.TRUE.equals(specification.getExchangeSpecificParametersItem(USE_SANDBOX));
    boolean testnet =
        Boolean.TRUE.equals(
            specification.getExchangeSpecificParametersItem(SPECIFIC_PARAM_TESTNET));
    if (sandbox && testnet) {
      throw new IllegalArgumentException(
          "Conflicting Bybit environments: both Exchange.USE_SANDBOX (demo trading) and "
              + "BybitExchange.SPECIFIC_PARAM_TESTNET (testnet) are true. Set exactly one of them.");
    }
    if (sandbox) {
      return DEMO;
    }
    if (testnet) {
      return TESTNET;
    }
    return PRODUCTION;
  }
}
