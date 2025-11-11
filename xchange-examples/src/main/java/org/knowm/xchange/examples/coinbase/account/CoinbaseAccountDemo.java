package org.knowm.xchange.examples.coinbase.account;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.coinbase.v3.dto.accounts.CoinbaseAccount;
import org.knowm.xchange.coinbase.v3.dto.paymentmethods.CoinbasePaymentMethod;
import org.knowm.xchange.coinbase.v3.dto.transactions.CoinbaseTransactionSummaryResponse;
import org.knowm.xchange.coinbase.v3.service.CoinbaseAccountService;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.dto.account.Fee;
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
 * </ul>
 *
 * <p><b>Note:</b> Account operations require API authentication.
 *
 * @author jamespedwards42
 */
public class CoinbaseAccountDemo {

  public static void main(String[] args) throws IOException {

    Exchange coinbase = CoinbaseDemoUtils.createExchange();
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
  }
}
