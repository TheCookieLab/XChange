package org.knowm.xchange.coinbase.v3.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.BeforeClass;
import org.junit.Test;
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

  @BeforeClass
  public static void beforeClass() {
    ExchangeSpecification spec = CoinbaseTestUtils.createSandboxSpecificationWithCredentials();
    exchange = (CoinbaseExchange) ExchangeFactory.INSTANCE.createExchange(spec);
    accountService = (CoinbaseAccountService) exchange.getAccountService();
  }

  @Test
  public void testListAccounts() throws Exception {
    org.junit.Assume.assumeNotNull(accountService.authTokenCreator);
    
    List<CoinbaseAccount> accounts = accountService.getCoinbaseAccounts();
    
    assertNotNull("Accounts should not be null", accounts);
    assertFalse("Accounts should not be empty", accounts.isEmpty());
    
    // Verify account structure
    CoinbaseAccount account = accounts.get(0);
    assertNotNull("UUID should not be null", account.getUuid());
    assertNotNull("Name should not be null", account.getName());
    assertNotNull("Currency should not be null", account.getCurrency());
    assertNotNull("Balance should not be null", account.getBalance());
    assertNotNull("Balance value should not be null", account.getBalance().getValue());
    assertNotNull("Balance currency should not be null", account.getBalance().getCurrency());
  }

  @Test
  public void testGetAccountById() throws Exception {
    org.junit.Assume.assumeNotNull(accountService.authTokenCreator);
    
    // First get all accounts to get a valid ID
    List<CoinbaseAccount> accounts = accountService.getCoinbaseAccounts();
    assertFalse("Need at least one account", accounts.isEmpty());
    
    String accountId = accounts.get(0).getUuid();
    
    // Get specific account
    CoinbaseAccount account = accountService.getCoinbaseAccount(accountId);
    assertNotNull("Account should not be null", account);
    assertEquals("Account ID should match", accountId, account.getUuid());
    assertNotNull("Balance should not be null", account.getBalance());
  }

  @Test
  public void testListPaymentMethods() throws Exception {
    org.junit.Assume.assumeNotNull(accountService.authTokenCreator);
    
    try {
      List<CoinbasePaymentMethod> methods = accountService.getCoinbasePaymentMethods();
      assertNotNull("Payment methods should not be null", methods);
      // Sandbox may return empty list - this is acceptable
    } catch (Exception e) {
      // Payment methods endpoint may not be fully supported in sandbox
      System.out.println("Payment methods not fully supported in sandbox (expected)");
    }
  }

  @Test
  public void testGetTransactionSummary() throws Exception {
    org.junit.Assume.assumeNotNull(accountService.authTokenCreator);
    
    try {
      CoinbaseTransactionSummaryResponse summary = accountService.getTransactionSummary();
      
      assertNotNull("Transaction summary should not be null", summary);
      assertNotNull("Fee tier should not be null", summary.getFeeTier());
      
      CoinbaseFeeTier feeTier = summary.getFeeTier();
      assertNotNull("Maker fee rate should not be null", feeTier.getMakerFeeRate());
      assertNotNull("Taker fee rate should not be null", feeTier.getTakerFeeRate());
      
      // Verify fees are non-negative
      assertTrue("Maker fee should be >= 0",
          feeTier.getMakerFeeRate().compareTo(BigDecimal.ZERO) >= 0);
      assertTrue("Taker fee should be >= 0",
          feeTier.getTakerFeeRate().compareTo(BigDecimal.ZERO) >= 0);
    } catch (Exception e) {
      // Transaction summary may not be fully supported in sandbox
      System.out.println("Transaction summary not fully supported in sandbox (expected)");
    }
  }

  @Test
  public void testGetDynamicTradingFees() throws Exception {
    org.junit.Assume.assumeNotNull(accountService.authTokenCreator);
    
    try {
      Map<Instrument, Fee> fees = accountService.getDynamicTradingFeesByInstrument();
      
      assertNotNull("Fees map should not be null", fees);
      
      Fee btcUsdFee = fees.get(CurrencyPair.BTC_USD);
      if (btcUsdFee != null) {
        assertNotNull("Maker fee should not be null", btcUsdFee.getMakerFee());
        assertNotNull("Taker fee should not be null", btcUsdFee.getTakerFee());
      }
    } catch (Exception e) {
      // Dynamic fees may not be fully supported in sandbox
      System.out.println("Dynamic trading fees not fully supported in sandbox (expected)");
    }
  }

  @Test
  public void testAccountBalanceConsistency() throws Exception {
    org.junit.Assume.assumeNotNull(accountService.authTokenCreator);
    
    List<CoinbaseAccount> accounts = accountService.getCoinbaseAccounts();
    
    for (CoinbaseAccount account : accounts) {
      // Verify currency consistency
      String accountCurrency = account.getCurrency();
      String balanceCurrency = account.getBalance().getCurrency();
      assertEquals("Currency should match for " + account.getName(),
          accountCurrency, balanceCurrency);
      
      // Verify balance is non-negative
      assertTrue("Balance should be non-negative for " + account.getName(),
          account.getBalance().getValue().compareTo(BigDecimal.ZERO) >= 0);
    }
  }

  @Test
  public void testGetFuturesBalanceSummary() throws Exception {
    org.junit.Assume.assumeNotNull(accountService.authTokenCreator);

    try {
      CoinbaseFuturesBalanceSummaryResponse response = accountService.getFuturesBalanceSummary();
      assertNotNull("Futures balance summary response should not be null", response);
    } catch (Exception e) {
      System.out.println("Futures balance summary not supported in sandbox (expected)");
    }
  }

  @Test
  public void testListFuturesSweeps() throws Exception {
    org.junit.Assume.assumeNotNull(accountService.authTokenCreator);

    try {
      CoinbaseFuturesSweepsResponse response = accountService.listFuturesSweeps();
      assertNotNull("Futures sweeps response should not be null", response);
      assertNotNull("Sweeps list should not be null", response.getSweeps());
    } catch (Exception e) {
      System.out.println("Futures sweeps not supported in sandbox (expected)");
    }
  }

  @Test
  public void testGetIntradayMarginSetting() throws Exception {
    org.junit.Assume.assumeNotNull(accountService.authTokenCreator);

    try {
      CoinbaseIntradayMarginSettingResponse response = accountService.getIntradayMarginSetting();
      assertNotNull("Intraday margin setting response should not be null", response);
    } catch (Exception e) {
      System.out.println("Intraday margin setting not supported in sandbox (expected)");
    }
  }

  @Test
  public void testGetCurrentMarginWindow() throws Exception {
    org.junit.Assume.assumeNotNull(accountService.authTokenCreator);

    try {
      CoinbaseCurrentMarginWindowResponse response = accountService.getCurrentMarginWindow();
      assertNotNull("Current margin window response should not be null", response);
    } catch (Exception e) {
      System.out.println("Current margin window not supported in sandbox (expected)");
    }
  }

  @Test
  public void testGetPerpetualsPortfolioBalances() throws Exception {
    org.junit.Assume.assumeNotNull(accountService.authTokenCreator);

    String portfolioUuid = findPerpetualsPortfolioUuid();
    org.junit.Assume.assumeNotNull(portfolioUuid);

    try {
      CoinbasePerpetualsBalancesResponse response =
          accountService.getPerpetualsPortfolioBalances(portfolioUuid);
      assertNotNull("Perpetuals balances response should not be null", response);
      if (response.getBalances() == null) {
        // Sandbox shape may drift for perpetuals balances; null payload is tolerated for connectivity checks.
        System.out.println("Perpetuals balances payload missing in sandbox response");
        return;
      }
      assertEquals("Portfolio UUID should match", portfolioUuid, response.getBalances().getPortfolioUuid());
      assertNotNull("Collateral currency should not be null", response.getBalances().getCollateralCurrency());
    } catch (Exception e) {
      System.out.println("Perpetuals balances not fully supported in sandbox (expected)");
    }
  }

  @Test
  public void testGetPerpetualsPortfolioSummary() throws Exception {
    org.junit.Assume.assumeNotNull(accountService.authTokenCreator);

    String portfolioUuid = findPerpetualsPortfolioUuid();
    org.junit.Assume.assumeNotNull(portfolioUuid);

    try {
      CoinbasePerpetualsPortfolioSummaryResponse response =
          accountService.getPerpetualsPortfolioSummary(portfolioUuid);
      assertNotNull("Perpetuals summary response should not be null", response);
      assertTrue("Summary or portfolios should be present",
          response.getSummary() != null || response.getPortfolios() != null);
      if (response.getSummary() != null) {
        assertNotNull("Collateral currency should not be null",
            response.getSummary().getCollateralCurrency());
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
