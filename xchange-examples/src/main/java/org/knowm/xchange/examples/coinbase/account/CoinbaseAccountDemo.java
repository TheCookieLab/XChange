package org.knowm.xchange.examples.coinbase.account;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.coinbase.v3.dto.accounts.CoinbaseAccount;
import org.knowm.xchange.coinbase.v3.dto.paymentmethods.CoinbasePaymentMethod;
import org.knowm.xchange.coinbase.v3.dto.permissions.CoinbaseKeyPermissionsResponse;
import org.knowm.xchange.coinbase.v3.dto.portfolios.CoinbasePortfolio;
import org.knowm.xchange.coinbase.v3.dto.portfolios.CoinbasePortfolioResponse;
import org.knowm.xchange.coinbase.v3.dto.portfolios.CoinbasePortfoliosResponse;
import org.knowm.xchange.coinbase.v3.dto.transactions.CoinbaseTransactionSummaryResponse;
import org.knowm.xchange.coinbase.v3.service.CoinbaseAccountService;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.dto.account.Fee;
import org.knowm.xchange.dto.account.Wallet;
import org.knowm.xchange.examples.coinbase.CoinbaseDemoUtils;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.service.account.AccountService;

/**
 * Example demonstrating Coinbase Advanced Trade API v3 account operations.
 *
 * <p>This example shows how to:
 * <ul>
 *   <li>Get account information</li>
 *   <li>List accounts</li>
 *   <li>Get account details</li>
 *   <li>Retrieve payment methods</li>
 *   <li>Get transaction summaries and fee information</li>
 *   <li>Inspect API key permissions and portfolio metadata</li>
 *   <li>Fetch futures and perpetuals wallet snapshots (if enabled)</li>
 * </ul>
 *
 * <p><b>Note:</b> Account operations require API authentication.
 *
 * @author jamespedwards42
 */
public class CoinbaseAccountDemo {

  public static void main(String[] args) throws IOException {

    Exchange coinbase = CoinbaseDemoUtils.createExchange();
    if (!CoinbaseDemoUtils.isAuthConfigured(coinbase)) {
      System.out.println("No API credentials found (secret.keys). Account examples require auth.");
      System.out.println("Add a secret.keys file or set API credentials before running.");
      return;
    }
    AccountService accountService = coinbase.getAccountService();

    generic(accountService);
    raw((CoinbaseAccountService) accountService);
  }

  private static void generic(AccountService accountService) throws IOException {

    AccountInfo accountInfo = accountService.getAccountInfo();
    System.out.println("Account Info: " + accountInfo);

    // Note: requestDepositAddress is not available in Coinbase v3 Advanced Trade API
    // Deposit addresses need to be retrieved through the Coinbase web interface or primary API
  }

  public static void raw(CoinbaseAccountService accountService) throws IOException {

    // API key permissions
    try {
      CoinbaseKeyPermissionsResponse permissions = accountService.getKeyPermissions();
      System.out.println("Key Permissions: " + permissions);
    } catch (Exception e) {
      System.out.println("Key permissions not available: " + e.getMessage());
    }

    // List all accounts
    List<CoinbaseAccount> accounts = accountService.getCoinbaseAccounts();
    System.out.println("All Accounts: " + accounts);

    // Get details for a specific account (if we have one)
    if (!accounts.isEmpty()) {
      CoinbaseAccount firstAccount = accounts.get(0);
      System.out.println("First Account Details:");
      System.out.println("  UUID: " + firstAccount.getUuid());
      System.out.println("  Name: " + firstAccount.getName());
      System.out.println("  Currency: " + firstAccount.getCurrency());
      System.out.println("  Balance: " + firstAccount.getBalance());

      // Get specific account by ID
      CoinbaseAccount accountById = accountService.getCoinbaseAccount(firstAccount.getUuid());
      System.out.println("Account by ID: " + accountById);
    }

    // Get payment methods
    try {
      List<CoinbasePaymentMethod> paymentMethods = accountService.getCoinbasePaymentMethods();
      System.out.println("Payment Methods: " + paymentMethods);
    } catch (Exception e) {
      System.out.println("Payment methods not available: " + e.getMessage());
    }

    // Get transaction summary (fee information)
    try {
      CoinbaseTransactionSummaryResponse transactionSummary = accountService.getTransactionSummary();
      System.out.println("Transaction Summary: " + transactionSummary);
      if (transactionSummary.getFeeTier() != null) {
        System.out.println("  Maker Fee Rate: " + transactionSummary.getFeeTier().getMakerFeeRate());
        System.out.println("  Taker Fee Rate: " + transactionSummary.getFeeTier().getTakerFeeRate());
      }
    } catch (Exception e) {
      System.out.println("Transaction summary not available: " + e.getMessage());
    }

    // Get dynamic trading fees
    try {
      Map<Instrument, Fee> fees = accountService.getDynamicTradingFeesByInstrument();
      System.out.println("Dynamic Trading Fees: " + fees);
      Fee btcUsdFee = fees.get(CurrencyPair.BTC_USD);
      if (btcUsdFee != null) {
        System.out.println("  BTC/USD Maker Fee: " + btcUsdFee.getMakerFee());
        System.out.println("  BTC/USD Taker Fee: " + btcUsdFee.getTakerFee());
      }
    } catch (Exception e) {
      System.out.println("Dynamic trading fees not available: " + e.getMessage());
    }

    // List portfolios and fetch breakdown for the first portfolio
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

    // Futures wallet snapshot (CFM accounts only)
    try {
      Wallet futuresWallet = accountService.getFuturesWallet();
      System.out.println("Futures Wallet: " + futuresWallet);
    } catch (Exception e) {
      System.out.println("Futures wallet not available: " + e.getMessage());
    }

    // Perpetuals wallet snapshot (requires a perpetuals portfolio UUID)
    try {
      String portfolioUuid = resolvePortfolioUuid(accountService);
      if (portfolioUuid != null) {
        Wallet perpWallet = accountService.getPerpetualsWallet(portfolioUuid);
        System.out.println("Perpetuals Wallet (" + portfolioUuid + "): " + perpWallet);
      } else {
        System.out.println("Perpetuals wallet skipped: no PERP/INTX portfolio UUID found.");
      }
    } catch (Exception e) {
      System.out.println("Perpetuals wallet not available: " + e.getMessage());
    }
  }

  private static String resolvePortfolioUuid(CoinbaseAccountService accountService)
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
