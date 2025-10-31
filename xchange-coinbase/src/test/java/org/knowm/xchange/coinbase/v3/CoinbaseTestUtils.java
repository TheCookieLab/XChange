package org.knowm.xchange.coinbase.v3;

import org.junit.Assume;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.utils.AuthUtils;

/**
 * Shared utilities for Coinbase v3 integration tests.
 * Supports both production and sandbox environments.
 */
public class CoinbaseTestUtils {

  public static final String SANDBOX_URL = "https://api-sandbox.coinbase.com";
  public static final String PRODUCTION_URL = "https://api.coinbase.com";
  
  public static final String PROPERTY_SANDBOX = "coinbase.sandbox";
  public static final String PROPERTY_API_URL = "coinbase.api.url";

  /**
   * Creates an exchange specification configured for sandbox testing.
   * Sandbox does not require authentication.
   *
   * @return configured exchange specification pointing to sandbox
   */
  public static ExchangeSpecification createSandboxSpecification() {
    ExchangeSpecification spec = new ExchangeSpecification(CoinbaseExchange.class);
    spec.setSslUri(SANDBOX_URL);
    spec.setHost("api-sandbox.coinbase.com");
    spec.setExchangeName("Coinbase Sandbox");
    spec.setExchangeDescription("Coinbase Advanced Trade Sandbox Environment");
    return spec;
  }

  /**
   * Creates an exchange specification configured for production with authentication.
   *
   * @return configured exchange specification with API keys
   */
  public static ExchangeSpecification createProductionSpecification() {
    ExchangeSpecification spec = ExchangeFactory.INSTANCE
        .createExchange(CoinbaseExchange.class)
        .getDefaultExchangeSpecification();
    AuthUtils.setApiAndSecretKey(spec);
    return spec;
  }

  /**
   * Creates an exchange specification with URL override support.
   * Checks system properties for custom API URL or sandbox mode.
   *
   * @return configured exchange specification
   */
  public static ExchangeSpecification createSpecificationWithOverride() {
    // Check if sandbox mode is explicitly enabled
    boolean useSandbox = "true".equalsIgnoreCase(System.getProperty(PROPERTY_SANDBOX));
    
    if (useSandbox) {
      return createSandboxSpecification();
    }
    
    // Check for custom API URL override
    String customUrl = System.getProperty(PROPERTY_API_URL);
    
    if (customUrl != null && !customUrl.isEmpty()) {
      ExchangeSpecification spec = ExchangeFactory.INSTANCE
          .createExchange(CoinbaseExchange.class)
          .getDefaultExchangeSpecification();
      spec.setSslUri(customUrl);
      
      try {
        java.net.URI uri = new java.net.URI(customUrl);
        spec.setHost(uri.getHost());
      } catch (java.net.URISyntaxException e) {
        throw new IllegalArgumentException("Invalid API URL: " + customUrl, e);
      }
      
      // Only set auth for non-sandbox URLs
      if (!customUrl.contains("sandbox")) {
        AuthUtils.setApiAndSecretKey(spec);
      }
      
      return spec;
    }
    
    // Default to production with auth
    return createProductionSpecification();
  }

  /**
   * Checks if authentication credentials are configured.
   *
   * @param spec exchange specification to check
   * @return true if API key and secret are present
   */
  public static boolean isAuthConfigured(ExchangeSpecification spec) {
    return spec.getApiKey() != null 
        && !spec.getApiKey().isEmpty()
        && spec.getSecretKey() != null
        && !spec.getSecretKey().isEmpty();
  }

  /**
   * Checks if the specification points to sandbox environment.
   *
   * @param spec exchange specification to check
   * @return true if using sandbox URL
   */
  public static boolean isSandbox(ExchangeSpecification spec) {
    return spec.getSslUri().contains("sandbox");
  }

  /**
   * Assumes test is running in sandbox mode; skips otherwise.
   * Use with @Test methods that should only run against sandbox.
   */
  public static void assumeSandboxMode() {
    Assume.assumeTrue("Sandbox mode only (set -Dcoinbase.sandbox=true)", 
        "true".equalsIgnoreCase(System.getProperty(PROPERTY_SANDBOX)));
  }

  /**
   * Assumes test is running in production mode; skips otherwise.
   * Use with @Test methods that require real API access.
   */
  public static void assumeProductionMode() {
    Assume.assumeTrue("Production mode only (auth required)", 
        !"true".equalsIgnoreCase(System.getProperty(PROPERTY_SANDBOX)));
  }

  /**
   * Assumes authentication is configured; skips test otherwise.
   * Use with @Test methods that require authenticated endpoints.
   *
   * @param spec exchange specification to check
   */
  public static void assumeAuthConfigured(ExchangeSpecification spec) {
    Assume.assumeTrue("Authentication required (API key and secret must be configured)", 
        isAuthConfigured(spec));
  }

  /**
   * Gets a test-friendly account ID.
   * In sandbox: returns static sandbox account ID.
   * In production: returns value from system property or null.
   *
   * @param isSandbox whether running in sandbox mode
   * @return account ID for testing or null
   */
  public static String getTestAccountId(boolean isSandbox) {
    if (isSandbox) {
      return "sandbox-account-123";  // Static sandbox account
    }
    return System.getProperty("coinbase.test.account.id");
  }

  /**
   * Gets a test-friendly order ID.
   * In sandbox: returns static sandbox order ID.
   * In production: returns value from system property or null.
   *
   * @param isSandbox whether running in sandbox mode
   * @return order ID for testing or null
   */
  public static String getTestOrderId(boolean isSandbox) {
    if (isSandbox) {
      return "sandbox-order-456";  // Static sandbox order
    }
    return System.getProperty("coinbase.test.order.id");
  }
}

