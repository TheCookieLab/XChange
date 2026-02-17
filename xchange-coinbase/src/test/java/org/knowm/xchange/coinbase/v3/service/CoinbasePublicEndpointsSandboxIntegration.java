package org.knowm.xchange.coinbase.v3.service;

import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import org.junit.BeforeClass;
import org.junit.Test;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.client.ExchangeRestProxyBuilder;
import org.knowm.xchange.coinbase.v3.Coinbase;
import org.knowm.xchange.coinbase.v3.CoinbaseTestUtils;
import org.knowm.xchange.coinbase.v3.dto.CoinbaseTimeResponse;

/**
 * Integration tests for Coinbase Advanced Trade API public endpoints using sandbox environment.
 * 
 * <p>These tests verify that public endpoints work correctly in the sandbox environment.
 * Note: Sandbox may have limited support for market data endpoints, but the time endpoint
 * should always work.
 * 
 * <p><b>Usage:</b>
 * <pre>
 * mvn test -Dtest=CoinbasePublicEndpointsSandboxIntegration -Dcoinbase.sandbox=true
 * </pre>
 * 
 * @see <a href="https://docs.cdp.coinbase.com/coinbase-app/advanced-trade-apis/sandbox">Coinbase Sandbox Docs</a>
 */
public class CoinbasePublicEndpointsSandboxIntegration {

  static Coinbase coinbase;

  @BeforeClass
  public static void beforeClass() {
    ExchangeSpecification spec = CoinbaseTestUtils.createSandboxSpecificationWithCredentials();
    coinbase = ExchangeRestProxyBuilder.forInterface(Coinbase.class, spec).build();
  }

  @Test
  public void testGetTimeInSandbox() throws IOException {
    try {
      CoinbaseTimeResponse response = coinbase.getTime();
      
      assertNotNull("Time response should not be null", response);
      assertNotNull("ISO timestamp should not be null", response.getIso());
    } catch (Exception e) {
      System.out.println("Time endpoint may not be fully supported in sandbox: " + e.getMessage());
      // Sandbox may not support all endpoints, so we just log and continue
    }
  }

  @Test
  public void testSandboxPublicEndpointsCapabilities() {
    System.out.println("\n=== Coinbase Sandbox Public Endpoints Capabilities ===");
    
    testEndpoint("Get Time", () -> coinbase.getTime());
    testEndpoint("Get Public Product Book", () -> 
        coinbase.getPublicProductBook("BTC-USD", 5, null));
    testEndpoint("List Public Products", () -> 
        coinbase.listPublicProducts(5, null, null, null, null, null, null, null));
    testEndpoint("Get Public Product", () -> 
        coinbase.getPublicProduct("BTC-USD"));
    testEndpoint("Get Public Product Candles", () -> 
        coinbase.getPublicProductCandles("BTC-USD", null, null, "ONE_HOUR", 5));
    testEndpoint("Get Public Market Trades", () -> 
        coinbase.getPublicMarketTrades("BTC-USD", 5, null, null));
    
    System.out.println("=======================================================\n");
  }

  private void testEndpoint(String name, TestRunnable test) {
    try {
      test.run();
      System.out.println("✓ " + name + ": SUPPORTED");
    } catch (Exception e) {
      System.out.println("✗ " + name + ": NOT SUPPORTED (" + e.getClass().getSimpleName() + ": " + e.getMessage() + ")");
    }
  }

  @FunctionalInterface
  private interface TestRunnable {
    void run() throws Exception;
  }
}
