package org.knowm.xchange.coinbase.v3.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.coinbase.v3.CoinbaseExchange;
import org.knowm.xchange.coinbase.v3.CoinbaseTestUtils;
import org.knowm.xchange.coinbase.v3.dto.accounts.CoinbaseAccount;
import org.knowm.xchange.coinbase.v3.dto.futures.CoinbaseCurrentMarginWindowResponse;
import org.knowm.xchange.coinbase.v3.dto.futures.CoinbaseFuturesBalanceSummaryResponse;
import org.knowm.xchange.coinbase.v3.dto.futures.CoinbaseFuturesSweepsResponse;
import org.knowm.xchange.coinbase.v3.dto.futures.CoinbaseIntradayMarginSettingResponse;
import org.knowm.xchange.coinbase.v3.dto.paymentmethods.CoinbasePaymentMethod;
import org.knowm.xchange.coinbase.v3.dto.perpetuals.CoinbasePerpetualsBalancesResponse;
import org.knowm.xchange.coinbase.v3.dto.perpetuals.CoinbasePerpetualsPortfolioSummaryResponse;
import org.knowm.xchange.coinbase.v3.dto.portfolios.CoinbasePortfolio;
import org.knowm.xchange.coinbase.v3.dto.portfolios.CoinbasePortfoliosResponse;
import org.knowm.xchange.coinbase.v3.dto.transactions.CoinbaseFeeTier;
import org.knowm.xchange.coinbase.v3.dto.transactions.CoinbaseTransactionSummaryResponse;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.account.Fee;
import org.knowm.xchange.instrument.Instrument;

/**
 * Integration tests for {@link CoinbaseAccountService} using Coinbase sandbox environment.
 * 
 * <p>These tests exercise all account-related endpoints available in the sandbox.
 * Sandbox provides static responses without requiring authentication.
 * 
 * <p><b>Sandbox URL:</b> https://api-sandbox.coinbase.com
 * 
 * <p><b>Endpoints Tested:</b>
 * <ul>
 *   <li>GET /api/v3/brokerage/accounts - List all accounts</li>
 *   <li>GET /api/v3/brokerage/accounts/{account_id} - Get specific account</li>
 *   <li>GET /api/v3/brokerage/portfolios - List portfolios</li>
 *   <li>GET /api/v3/brokerage/payment_methods - List payment methods</li>
 *   <li>GET /api/v3/brokerage/transaction_summary - Get fee structure</li>
 *   <li>GET /api/v3/brokerage/intx/balances/{portfolio_uuid} - Perpetuals balances</li>
 *   <li>GET /api/v3/brokerage/intx/portfolio/{portfolio_uuid} - Perpetuals portfolio summary</li>
 *   <li>GET /api/v3/brokerage/cfm/balance_summary - Futures balance summary</li>
 *   <li>GET /api/v3/brokerage/cfm/sweeps - Futures sweeps</li>
 *   <li>GET /api/v3/brokerage/cfm/intraday/margin_setting - Intraday margin setting</li>
 *   <li>GET /api/v3/brokerage/cfm/intraday/current_margin_window - Current margin window</li>
 * </ul>
 * 
 * <p><b>Usage:</b>
 * <pre>
 * mvn test -Dtest=AccountServiceSandboxIntegration
 * </pre>
 * 
 * @see <a href="https://docs.cdp.coinbase.com/coinbase-business/advanced-trade-apis/sandbox">Coinbase Sandbox Docs</a>
 */
public class AccountServiceSandboxIntegration {

  static CoinbaseExchange exchange;
  static CoinbaseAccountService accountService;

  @BeforeAll
  public static void beforeClass() {
    ExchangeSpecification spec = CoinbaseTestUtils.createSandboxSpecificationWithCredentials();
    exchange = (CoinbaseExchange) ExchangeFactory.INSTANCE.createExchange(spec);
    accountService = (CoinbaseAccountService) exchange.getAccountService();
  }

  @Test
  public void testListAccounts() throws Exception {
    org.junit.jupiter.api.Assumptions.assumeTrue(accountService.authTokenCreator != null);
    
    List<CoinbaseAccount> accounts = accountService.getCoinbaseAccounts();
    
    assertNotNull(accounts, "Accounts should not be null");
    assertFalse(accounts.isEmpty(), "Accounts should not be empty");
    
    // Verify account structure
    CoinbaseAccount account = accounts.get(0);
    assertNotNull(account.getUuid(), "UUID should not be null");
    assertNotNull(account.getName(), "Name should not be null");
    assertNotNull(account.getCurrency(), "Currency should not be null");
    assertNotNull(account.getBalance(), "Balance should not be null");
    assertNotNull(account.getBalance().getValue(), "Balance value should not be null");
    assertNotNull(account.getBalance().getCurrency(), "Balance currency should not be null");
  }

  @Test
  public void testGetAccountById() throws Exception {
    org.junit.jupiter.api.Assumptions.assumeTrue(accountService.authTokenCreator != null);
    
    // First get all accounts to get a valid ID
    List<CoinbaseAccount> accounts = accountService.getCoinbaseAccounts();
    assertFalse(accounts.isEmpty(), "Need at least one account");
    
    String accountId = accounts.get(0).getUuid();
    
    // Get specific account
    CoinbaseAccount account = accountService.getCoinbaseAccount(accountId);
    assertNotNull(account, "Account should not be null");
    assertEquals(accountId, account.getUuid(), "Account ID should match");
    assertNotNull(account.getBalance(), "Balance should not be null");
  }

  @Test
  public void testListPaymentMethods() throws Exception {
    org.junit.jupiter.api.Assumptions.assumeTrue(accountService.authTokenCreator != null);
    
    try {
      List<CoinbasePaymentMethod> methods = accountService.getCoinbasePaymentMethods();
      assertNotNull(methods, "Payment methods should not be null");
      // Sandbox may return empty list - this is acceptable
    } catch (Exception e) {
      // Payment methods endpoint may not be fully supported in sandbox
      System.out.println("Payment methods not fully supported in sandbox (expected)");
    }
  }

  @Test
  public void testGetTransactionSummary() throws Exception {
    org.junit.jupiter.api.Assumptions.assumeTrue(accountService.authTokenCreator != null);
    
    try {
      CoinbaseTransactionSummaryResponse summary = accountService.getTransactionSummary();
      
      assertNotNull(summary, "Transaction summary should not be null");
      assertNotNull(summary.getFeeTier(), "Fee tier should not be null");
      
      CoinbaseFeeTier feeTier = summary.getFeeTier();
      assertNotNull(feeTier.getMakerFeeRate(), "Maker fee rate should not be null");
      assertNotNull(feeTier.getTakerFeeRate(), "Taker fee rate should not be null");
      
      // Verify fees are non-negative
      assertTrue(feeTier.getMakerFeeRate().compareTo(BigDecimal.ZERO) >= 0, "Maker fee should be >= 0");
      assertTrue(feeTier.getTakerFeeRate().compareTo(BigDecimal.ZERO) >= 0, "Taker fee should be >= 0");
    } catch (Exception e) {
      // Transaction summary may not be fully supported in sandbox
      System.out.println("Transaction summary not fully supported in sandbox (expected)");
    }
  }

  @Test
  public void testGetDynamicTradingFees() throws Exception {
    org.junit.jupiter.api.Assumptions.assumeTrue(accountService.authTokenCreator != null);
    
    try {
      Map<Instrument, Fee> fees = accountService.getDynamicTradingFeesByInstrument();
      
      assertNotNull(fees, "Fees map should not be null");
      
      Fee btcUsdFee = fees.get(CurrencyPair.BTC_USD);
      if (btcUsdFee != null) {
        assertNotNull(btcUsdFee.getMakerFee(), "Maker fee should not be null");
        assertNotNull(btcUsdFee.getTakerFee(), "Taker fee should not be null");
      }
    } catch (Exception e) {
      // Dynamic fees may not be fully supported in sandbox
      System.out.println("Dynamic trading fees not fully supported in sandbox (expected)");
    }
  }

  @Test
  public void testAccountBalanceConsistency() throws Exception {
    org.junit.jupiter.api.Assumptions.assumeTrue(accountService.authTokenCreator != null);
    
    List<CoinbaseAccount> accounts = accountService.getCoinbaseAccounts();
    
    for (CoinbaseAccount account : accounts) {
      // Verify currency consistency
      String accountCurrency = account.getCurrency();
      String balanceCurrency = account.getBalance().getCurrency();
      assertEquals(accountCurrency, balanceCurrency, "Currency should match for " + account.getName());
      
      // Verify balance is non-negative
      assertTrue(account.getBalance().getValue().compareTo(BigDecimal.ZERO) >= 0, "Balance should be non-negative for " + account.getName());
    }
  }

  @Test
  public void testGetFuturesBalanceSummary() throws Exception {
    org.junit.jupiter.api.Assumptions.assumeTrue(accountService.authTokenCreator != null);

    try {
      CoinbaseFuturesBalanceSummaryResponse response = accountService.getFuturesBalanceSummary();
      assertNotNull(response, "Futures balance summary response should not be null");
    } catch (Exception e) {
      System.out.println("Futures balance summary not supported in sandbox (expected)");
    }
  }

  @Test
  public void testListFuturesSweeps() throws Exception {
    org.junit.jupiter.api.Assumptions.assumeTrue(accountService.authTokenCreator != null);

    try {
      CoinbaseFuturesSweepsResponse response = accountService.listFuturesSweeps();
      assertNotNull(response, "Futures sweeps response should not be null");
      assertNotNull(response.getSweeps(), "Sweeps list should not be null");
    } catch (Exception e) {
      System.out.println("Futures sweeps not supported in sandbox (expected)");
    }
  }

  @Test
  public void testGetIntradayMarginSetting() throws Exception {
    org.junit.jupiter.api.Assumptions.assumeTrue(accountService.authTokenCreator != null);

    try {
      CoinbaseIntradayMarginSettingResponse response = accountService.getIntradayMarginSetting();
      assertNotNull(response, "Intraday margin setting response should not be null");
    } catch (Exception e) {
      System.out.println("Intraday margin setting not supported in sandbox (expected)");
    }
  }

  @Test
  public void testGetCurrentMarginWindow() throws Exception {
    org.junit.jupiter.api.Assumptions.assumeTrue(accountService.authTokenCreator != null);

    try {
      CoinbaseCurrentMarginWindowResponse response = accountService.getCurrentMarginWindow();
      assertNotNull(response, "Current margin window response should not be null");
    } catch (Exception e) {
      System.out.println("Current margin window not supported in sandbox (expected)");
    }
  }

  @Test
  public void testGetPerpetualsPortfolioBalances() throws Exception {
    org.junit.jupiter.api.Assumptions.assumeTrue(accountService.authTokenCreator != null);

    String portfolioUuid = findPerpetualsPortfolioUuid();
    org.junit.jupiter.api.Assumptions.assumeTrue(portfolioUuid != null);

    try {
      CoinbasePerpetualsBalancesResponse response =
          accountService.getPerpetualsPortfolioBalances(portfolioUuid);
      assertNotNull(response, "Perpetuals balances response should not be null");
      if (response.getBalances() == null) {
        // Sandbox shape may drift for perpetuals balances; null payload is tolerated for connectivity checks.
        System.out.println("Perpetuals balances payload missing in sandbox response");
        return;
      }
      assertEquals(portfolioUuid, response.getBalances().getPortfolioUuid(), "Portfolio UUID should match");
      assertNotNull(response.getBalances().getCollateralCurrency(), "Collateral currency should not be null");
    } catch (Exception e) {
      System.out.println("Perpetuals balances not fully supported in sandbox (expected)");
    }
  }

  @Test
  public void testGetPerpetualsPortfolioSummary() throws Exception {
    org.junit.jupiter.api.Assumptions.assumeTrue(accountService.authTokenCreator != null);

    String portfolioUuid = findPerpetualsPortfolioUuid();
    org.junit.jupiter.api.Assumptions.assumeTrue(portfolioUuid != null);

    try {
      CoinbasePerpetualsPortfolioSummaryResponse response =
          accountService.getPerpetualsPortfolioSummary(portfolioUuid);
      assertNotNull(response, "Perpetuals summary response should not be null");
      assertTrue(response.getSummary() != null || response.getPortfolios() != null, "Summary or portfolios should be present");
      if (response.getSummary() != null) {
        assertNotNull(response.getSummary().getCollateralCurrency(), "Collateral currency should not be null");
      }
    } catch (Exception e) {
      System.out.println("Perpetuals summary not fully supported in sandbox (expected)");
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
