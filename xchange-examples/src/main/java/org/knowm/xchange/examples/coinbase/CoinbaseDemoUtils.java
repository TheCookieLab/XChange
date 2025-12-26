package org.knowm.xchange.examples.coinbase;

import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.coinbase.v3.CoinbaseExchange;
import org.knowm.xchange.utils.AuthUtils;

/**
 * Utility class for creating Coinbase exchange instances for examples.
 *
 * <p>Provides methods to create exchanges with or without authentication.
 * Public endpoints (market data) don't require authentication, while
 * account and trade operations require API keys.
 *
 * @author jamespedwards42
 */
public class CoinbaseDemoUtils {

  private static final String SANDBOX_URL = "https://api-sandbox.coinbase.com";
  private static final String SANDBOX_HOST = "api-sandbox.coinbase.com";
  private static final String PROPERTY_SANDBOX = "coinbase.sandbox";
  private static final String ENV_SANDBOX = "COINBASE_SANDBOX";
  private static final String PROPERTY_API_URL = "coinbase.api.url";
  private static final String ENV_API_URL = "COINBASE_API_URL";

  /**
   * Creates an exchange instance with authentication if credentials are available.
   * <p>
   * This method loads credentials from {@code secret.keys} (or {@code ~/.ssh/secret.keys}).
   * If credentials are not found, the exchange is still created but only public endpoints
   * (market data) will be usable. Sandbox is used by default unless explicitly disabled.
   *
   * @return Exchange instance with API keys configured when available
   */
  public static Exchange createExchange() {
    ExchangeSpecification exSpec = createExchangeSpecification();
    AuthUtils.setApiAndSecretKey(exSpec);
    return ExchangeFactory.INSTANCE.createExchange(exSpec);
  }

  /**
   * Creates an exchange instance without authentication.
   * Use this for public endpoints (market data) that don't require API keys.
   * Sandbox is used by default unless explicitly disabled.
   *
   * @return Exchange instance without API keys
   */
  public static Exchange createExchangeWithoutAuth() {
    ExchangeSpecification exSpec = createExchangeSpecification();
    return ExchangeFactory.INSTANCE.createExchange(exSpec);
  }

  /**
   * Creates an exchange specification using sandbox by default.
   * <p>
   * Override behavior via:
   * <ul>
   *   <li>{@code -Dcoinbase.sandbox=false} or {@code COINBASE_SANDBOX=false} for production</li>
   *   <li>{@code -Dcoinbase.api.url=https://...} or {@code COINBASE_API_URL} for custom URLs</li>
   * </ul>
   *
   * @return Exchange specification configured for sandbox or production
   */
  public static ExchangeSpecification createExchangeSpecification() {
    ExchangeSpecification exSpec = new CoinbaseExchange().getDefaultExchangeSpecification();
    applySandboxPreference(exSpec);
    return exSpec;
  }

  /**
   * Returns true if API credentials are configured on the exchange specification.
   *
   * @param exchange Exchange instance to inspect
   * @return true when both API key and secret key are present
   */
  public static boolean isAuthConfigured(Exchange exchange) {
    if (exchange == null || exchange.getExchangeSpecification() == null) {
      return false;
    }
    ExchangeSpecification spec = exchange.getExchangeSpecification();
    return spec.getApiKey() != null && spec.getSecretKey() != null;
  }

  /**
   * Returns true if sandbox mode is preferred for examples.
   *
   * @return true when sandbox is preferred
   */
  public static boolean isSandboxPreferred() {
    String value = readOptional(PROPERTY_SANDBOX, ENV_SANDBOX);
    if (value == null) {
      return true;
    }
    return parseBoolean(value);
  }

  /**
   * Returns true if the exchange specification points to sandbox.
   *
   * @param exchange Exchange instance to inspect
   * @return true when the base URL is a sandbox endpoint
   */
  public static boolean isSandboxExchange(Exchange exchange) {
    if (exchange == null || exchange.getExchangeSpecification() == null) {
      return false;
    }
    String uri = exchange.getExchangeSpecification().getSslUri();
    return uri != null && uri.contains("sandbox");
  }

  private static void applySandboxPreference(ExchangeSpecification exSpec) {
    String apiUrl = readOptional(PROPERTY_API_URL, ENV_API_URL);
    if (apiUrl != null) {
      applyApiUrl(exSpec, apiUrl);
      return;
    }
    if (isSandboxPreferred()) {
      exSpec.setSslUri(SANDBOX_URL);
      exSpec.setHost(SANDBOX_HOST);
      exSpec.setExchangeName("Coinbase Sandbox");
      exSpec.setExchangeDescription("Coinbase Advanced Trade Sandbox Environment");
    }
  }

  private static void applyApiUrl(ExchangeSpecification spec, String apiUrl) {
    spec.setSslUri(apiUrl);
    try {
      java.net.URI uri = new java.net.URI(apiUrl);
      if (uri.getHost() != null) {
        spec.setHost(uri.getHost());
      }
    } catch (java.net.URISyntaxException e) {
      throw new IllegalArgumentException("Invalid API URL: " + apiUrl, e);
    }
  }

  private static String readOptional(String propertyKey, String envKey) {
    String value = System.getProperty(propertyKey);
    if (value == null || value.trim().isEmpty()) {
      value = System.getenv(envKey);
    }
    if (value == null || value.trim().isEmpty()) {
      return null;
    }
    return value.trim();
  }

  private static boolean parseBoolean(String value) {
    if (value == null) {
      return false;
    }
    String normalized = value.trim().toLowerCase();
    return "true".equals(normalized) || "1".equals(normalized) || "yes".equals(normalized);
  }
}
