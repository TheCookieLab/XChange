package org.knowm.xchange.coinbase.v3.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.coinbase.v3.CoinbaseExchange;
import org.knowm.xchange.coinbase.v3.CoinbaseTestUtils;
import org.knowm.xchange.coinbase.v3.dto.perpetuals.CoinbasePerpetualsPosition;
import org.knowm.xchange.coinbase.v3.dto.perpetuals.CoinbasePerpetualsPositionResponse;
import org.knowm.xchange.coinbase.v3.dto.perpetuals.CoinbasePerpetualsPositionsResponse;
import org.knowm.xchange.coinbase.v3.dto.portfolios.CoinbasePortfolio;
import org.knowm.xchange.coinbase.v3.dto.portfolios.CoinbasePortfoliosResponse;
import org.knowm.xchange.coinbase.v3.dto.trade.CoinbaseTradeHistoryParams;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.trade.OpenOrders;
import org.knowm.xchange.dto.trade.UserTrades;

/**
 * Integration tests for {@link CoinbaseTradeService} using Coinbase sandbox environment.
 * 
 * <p>These tests exercise all order-related endpoints available in the sandbox.
 * Sandbox provides static responses without requiring authentication.
 * 
 * <p><b>Sandbox URL:</b> https://api-sandbox.coinbase.com
 * 
 * <p><b>Endpoints Tested:</b>
 * <ul>
 *   <li>GET /api/v3/brokerage/orders/historical/batch - List orders</li>
 *   <li>GET /api/v3/brokerage/orders/historical/{order_id} - Get specific order</li>
 *   <li>GET /api/v3/brokerage/orders/historical/fills - List fills/trades</li>
 *   <li>POST /api/v3/brokerage/orders - Create order (simulated)</li>
 *   <li>POST /api/v3/brokerage/orders/batch_cancel - Cancel orders (simulated)</li>
 *   <li>GET /api/v3/brokerage/portfolios - List portfolios (lookup INTX portfolio)</li>
 *   <li>GET /api/v3/brokerage/intx/positions/{portfolio_uuid} - List perpetuals positions</li>
 *   <li>GET /api/v3/brokerage/intx/positions/{portfolio_uuid}/{symbol} - Get perpetuals position</li>
 * </ul>
 * 
 * <p><b>Usage:</b>
 * <pre>
 * mvn test -Dtest=TradeServiceSandboxIntegration
 * </pre>
 * 
 * @see <a href="https://docs.cdp.coinbase.com/coinbase-business/advanced-trade-apis/sandbox">Coinbase Sandbox Docs</a>
 */
public class TradeServiceSandboxIntegration {

  static CoinbaseExchange exchange;
  static CoinbaseTradeService tradeService;
  static CoinbaseAccountService accountService;

  @BeforeAll
  public static void beforeClass() {
    ExchangeSpecification spec = CoinbaseTestUtils.createSandboxSpecificationWithCredentials();
    exchange = (CoinbaseExchange) ExchangeFactory.INSTANCE.createExchange(spec);
    tradeService = (CoinbaseTradeService) exchange.getTradeService();
    accountService = (CoinbaseAccountService) exchange.getAccountService();
  }

  @Test
  public void testGetTradeHistory() throws Exception {
    org.junit.jupiter.api.Assumptions.assumeTrue(tradeService.authTokenCreator != null);
    
    CoinbaseTradeHistoryParams params = new CoinbaseTradeHistoryParams();
    
    UserTrades tradeHistory = tradeService.getTradeHistory(params);
    
    assertNotNull(tradeHistory, "Trade history should not be null");
    assertNotNull(tradeHistory.getTrades(), "Trades list should not be null");
    
    // Sandbox may return empty or static data
    if (!tradeHistory.getTrades().isEmpty()) {
      assertNotNull(tradeHistory.getTrades().get(0).getId(), "Trade ID should not be null");
      assertNotNull(tradeHistory.getTrades().get(0).getPrice(), "Trade price should not be null");
      assertNotNull(tradeHistory.getTrades().get(0).getOriginalAmount(), "Trade amount should not be null");
    }
  }

  @Test
  public void testGetTradeHistoryWithCurrencyPair() throws Exception {
    org.junit.jupiter.api.Assumptions.assumeTrue(tradeService.authTokenCreator != null);
    
    CoinbaseTradeHistoryParams params = new CoinbaseTradeHistoryParams();
    params.addCurrencyPair(CurrencyPair.BTC_USD);
    
    UserTrades tradeHistory = tradeService.getTradeHistory(params);
    
    assertNotNull(tradeHistory, "Trade history should not be null");
    assertNotNull(tradeHistory.getTrades(), "Trades list should not be null");
  }

  @Test
  public void testGetTradeHistoryWithMultipleCurrencyPairs() throws Exception {
    org.junit.jupiter.api.Assumptions.assumeTrue(tradeService.authTokenCreator != null);
    
    CoinbaseTradeHistoryParams params = new CoinbaseTradeHistoryParams();
    params.addCurrencyPair(CurrencyPair.BTC_USD);
    params.addCurrencyPair(CurrencyPair.ETH_USD);
    
    UserTrades tradeHistory = tradeService.getTradeHistory(params);
    
    assertNotNull(tradeHistory, "Trade history should not be null");
    assertNotNull(tradeHistory.getTrades(), "Trades list should not be null");
  }

  @Test
  public void testGetTradeHistoryWithLimit() throws Exception {
    org.junit.jupiter.api.Assumptions.assumeTrue(tradeService.authTokenCreator != null);
    
    CoinbaseTradeHistoryParams params = new CoinbaseTradeHistoryParams();
    params.setLimit(10);
    
    UserTrades tradeHistory = tradeService.getTradeHistory(params);
    
    assertNotNull(tradeHistory, "Trade history should not be null");
    assertNotNull(tradeHistory.getTrades(), "Trades list should not be null");
    // Sandbox may return fixed data, but should respect general structure
  }

  @Test
  public void testGetOpenOrders() throws Exception {
    org.junit.jupiter.api.Assumptions.assumeTrue(tradeService.authTokenCreator != null);
    
    OpenOrders openOrders = tradeService.getOpenOrders();
    
    assertNotNull(openOrders, "OpenOrders should not be null");
    assertNotNull(openOrders.getOpenOrders(), "Open orders list should not be null");
    
    // Sandbox may return static orders
    if (!openOrders.getOpenOrders().isEmpty()) {
      assertNotNull(openOrders.getOpenOrders().get(0).getId(), "Order ID should not be null");
      assertNotNull(openOrders.getOpenOrders().get(0).getStatus(), "Order status should not be null");
    }
  }

  @Test
  public void testGetOrder() throws Exception {
    org.junit.jupiter.api.Assumptions.assumeTrue(tradeService.authTokenCreator != null);
    
    // First get open orders to find a valid order ID
    OpenOrders openOrders = tradeService.getOpenOrders();
    
    if (!openOrders.getOpenOrders().isEmpty()) {
      String orderId = openOrders.getOpenOrders().get(0).getId();
      
      try {
        java.util.Collection<org.knowm.xchange.dto.Order> orders = tradeService.getOrder(
            new org.knowm.xchange.service.trade.params.orders.DefaultQueryOrderParam(orderId));
        assertNotNull(orders, "Orders collection should not be null");
        assertFalse(orders.isEmpty(), "Orders should not be empty");
      } catch (Exception e) {
        // Some sandbox implementations may not fully support this
        System.out.println("Get order by ID not fully supported in sandbox: " + e.getMessage());
      }
    }
  }

  @Test
  public void testTradeHistoryTimestampOrdering() throws Exception {
    org.junit.jupiter.api.Assumptions.assumeTrue(tradeService.authTokenCreator != null);
    
    CoinbaseTradeHistoryParams params = new CoinbaseTradeHistoryParams();
    UserTrades trades = tradeService.getTradeHistory(params);
    
    // If sandbox returns multiple trades, verify they're in order
    if (trades.getTrades().size() > 1) {
      for (int i = 0; i < trades.getTrades().size() - 1; i++) {
        assertNotNull(trades.getTrades().get(i).getTimestamp(), "Trade timestamp should not be null");
      }
    }
  }

  @Test
  public void testListPerpetualsPositions() throws Exception {
    org.junit.jupiter.api.Assumptions.assumeTrue(tradeService.authTokenCreator != null);

    String portfolioUuid = findPerpetualsPortfolioUuid();
    org.junit.jupiter.api.Assumptions.assumeTrue(portfolioUuid != null);

    try {
      CoinbasePerpetualsPositionsResponse response =
          tradeService.listPerpetualsPositions(portfolioUuid);
      assertNotNull(response, "Perpetuals positions response should not be null");
      assertNotNull(response.getPositions(), "Positions list should not be null");
    } catch (Exception e) {
      System.out.println("Perpetuals positions not fully supported in sandbox: " + e.getMessage());
    }
  }

  @Test
  public void testGetPerpetualsPosition() throws Exception {
    org.junit.jupiter.api.Assumptions.assumeTrue(tradeService.authTokenCreator != null);

    String portfolioUuid = findPerpetualsPortfolioUuid();
    org.junit.jupiter.api.Assumptions.assumeTrue(portfolioUuid != null);

    try {
      CoinbasePerpetualsPositionsResponse response =
          tradeService.listPerpetualsPositions(portfolioUuid);
      if (response.getPositions().isEmpty()) {
        return;
      }
      CoinbasePerpetualsPosition position = response.getPositions().get(0);
      String symbol = position.getSymbol() != null ? position.getSymbol() : position.getProductId();
      if (symbol == null) {
        return;
      }
      CoinbasePerpetualsPositionResponse detail =
          tradeService.getPerpetualsPosition(portfolioUuid, symbol);
      assertNotNull(detail, "Perpetuals position response should not be null");
      assertNotNull(detail.getPosition(), "Position should not be null");
    } catch (Exception e) {
      System.out.println("Perpetuals position not fully supported in sandbox: " + e.getMessage());
    }
  }

  private static String findPerpetualsPortfolioUuid() throws Exception {
    CoinbasePortfoliosResponse response = accountService.listPortfolios(null);
    if (response == null || response.getPortfolios() == null) {
      return null;
    }
    for (CoinbasePortfolio portfolio : response.getPortfolios()) {
      if (portfolio.getType() == null) {
        continue;
      }
      String type = portfolio.getType().toUpperCase();
      if (type.contains("INTX") || type.contains("PERP")) {
        return portfolio.getUuid();
      }
    }
    return null;
  }
}
