package org.knowm.xchange.examples.coinbase.account;

import java.io.IOException;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.coinbase.v3.dto.futures.CoinbaseCurrentMarginWindowResponse;
import org.knowm.xchange.coinbase.v3.dto.futures.CoinbaseFuturesBalanceSummaryResponse;
import org.knowm.xchange.coinbase.v3.dto.futures.CoinbaseFuturesSweepsResponse;
import org.knowm.xchange.coinbase.v3.dto.futures.CoinbaseIntradayMarginSettingResponse;
import org.knowm.xchange.coinbase.v3.dto.perpetuals.CoinbasePerpetualsBalancesResponse;
import org.knowm.xchange.coinbase.v3.dto.perpetuals.CoinbasePerpetualsPortfolioSummaryResponse;
import org.knowm.xchange.coinbase.v3.dto.portfolios.CoinbasePortfolio;
import org.knowm.xchange.coinbase.v3.dto.portfolios.CoinbasePortfolioResponse;
import org.knowm.xchange.coinbase.v3.dto.portfolios.CoinbasePortfoliosResponse;
import org.knowm.xchange.coinbase.v3.service.CoinbaseAccountService;
import org.knowm.xchange.examples.coinbase.CoinbaseDemoUtils;

/**
 * Example demonstrating Coinbase Advanced Trade portfolio, futures, and perpetuals read-only
 * account endpoints.
 *
 * <p>This example shows how to:
 * <ul>
 *   <li>List portfolios and fetch a portfolio breakdown</li>
 *   <li>Read futures balance summaries and margin settings</li>
 *   <li>List futures sweeps (read-only)</li>
 *   <li>Fetch perpetuals portfolio summary and balances</li>
 * </ul>
 *
 * <p><b>Note:</b> These endpoints require API authentication. This demo performs read-only calls
 * and does not modify any account state.
 */
public class CoinbasePortfolioAndFuturesDemo {

  public static void main(String[] args) throws IOException {
    Exchange coinbase = CoinbaseDemoUtils.createExchange();
    if (!CoinbaseDemoUtils.isAuthConfigured(coinbase)) {
      System.out.println("No API credentials found (secret.keys). This demo requires auth.");
      System.out.println("Add a secret.keys file or set API credentials before running.");
      return;
    }

    CoinbaseAccountService accountService = (CoinbaseAccountService) coinbase.getAccountService();

    showPortfolios(accountService);
    showFuturesReadOnly(accountService);
    showPerpetualsReadOnly(accountService);
  }

  private static void showPortfolios(CoinbaseAccountService accountService) {
    try {
      CoinbasePortfoliosResponse portfolios = accountService.listPortfolios(null);
      System.out.println("Portfolios: " + portfolios);
      if (!portfolios.getPortfolios().isEmpty()) {
        CoinbasePortfolio portfolio = portfolios.getPortfolios().get(0);
        CoinbasePortfolioResponse breakdown =
            accountService.getPortfolioBreakdown(portfolio.getUuid());
        System.out.println("Portfolio Breakdown (" + portfolio.getUuid() + "): " + breakdown);
      }
    } catch (Exception e) {
      System.out.println("Portfolio data not available: " + e.getMessage());
    }
  }

  private static void showFuturesReadOnly(CoinbaseAccountService accountService) {
    try {
      CoinbaseFuturesBalanceSummaryResponse summary = accountService.getFuturesBalanceSummary();
      System.out.println("Futures Balance Summary: " + summary);
    } catch (Exception e) {
      System.out.println("Futures balance summary not available: " + e.getMessage());
    }

    try {
      CoinbaseIntradayMarginSettingResponse setting = accountService.getIntradayMarginSetting();
      System.out.println("Intraday Margin Setting: " + setting);
    } catch (Exception e) {
      System.out.println("Intraday margin setting not available: " + e.getMessage());
    }

    try {
      CoinbaseCurrentMarginWindowResponse window = accountService.getCurrentMarginWindow();
      System.out.println("Current Margin Window: " + window);
    } catch (Exception e) {
      System.out.println("Current margin window not available: " + e.getMessage());
    }

    try {
      CoinbaseFuturesSweepsResponse sweeps = accountService.listFuturesSweeps();
      System.out.println("Futures Sweeps: " + sweeps);
    } catch (Exception e) {
      System.out.println("Futures sweeps not available: " + e.getMessage());
    }
  }

  private static void showPerpetualsReadOnly(CoinbaseAccountService accountService)
      throws IOException {
    String portfolioUuid = resolvePerpPortfolioUuid(accountService);
    if (portfolioUuid == null) {
      System.out.println("Perpetuals portfolio not found. Set coinbase.perpPortfolioUuid or "
          + "COINBASE_PERP_PORTFOLIO_UUID to override.");
      return;
    }

    try {
      CoinbasePerpetualsPortfolioSummaryResponse summary =
          accountService.getPerpetualsPortfolioSummary(portfolioUuid);
      System.out.println("Perpetuals Portfolio Summary (" + portfolioUuid + "): " + summary);
    } catch (Exception e) {
      System.out.println("Perpetuals portfolio summary not available: " + e.getMessage());
    }

    try {
      CoinbasePerpetualsBalancesResponse balances =
          accountService.getPerpetualsPortfolioBalances(portfolioUuid);
      System.out.println("Perpetuals Portfolio Balances (" + portfolioUuid + "): " + balances);
    } catch (Exception e) {
      System.out.println("Perpetuals portfolio balances not available: " + e.getMessage());
    }
  }

  private static String resolvePerpPortfolioUuid(CoinbaseAccountService accountService)
      throws IOException {
    String override = readOptional("coinbase.perpPortfolioUuid", "COINBASE_PERP_PORTFOLIO_UUID");
    if (override != null) {
      return override;
    }
    CoinbasePortfoliosResponse portfolios = accountService.listPortfolios(null);
    for (CoinbasePortfolio portfolio : portfolios.getPortfolios()) {
      String type = portfolio.getType();
      if ("PERP".equalsIgnoreCase(type) || "INTX".equalsIgnoreCase(type)) {
        return portfolio.getUuid();
      }
    }
    return null;
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
}
