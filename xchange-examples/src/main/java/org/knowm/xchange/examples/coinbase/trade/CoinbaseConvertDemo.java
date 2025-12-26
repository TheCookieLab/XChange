package org.knowm.xchange.examples.coinbase.trade;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.coinbase.v3.dto.accounts.CoinbaseAccount;
import org.knowm.xchange.coinbase.v3.dto.converts.CoinbaseCommitConvertTradeRequest;
import org.knowm.xchange.coinbase.v3.dto.converts.CoinbaseConvertQuoteRequest;
import org.knowm.xchange.coinbase.v3.dto.converts.CoinbaseConvertQuoteResponse;
import org.knowm.xchange.coinbase.v3.dto.converts.CoinbaseConvertTradeResponse;
import org.knowm.xchange.coinbase.v3.service.CoinbaseAccountService;
import org.knowm.xchange.coinbase.v3.service.CoinbaseTradeService;
import org.knowm.xchange.examples.coinbase.CoinbaseDemoUtils;

/**
 * Example demonstrating Coinbase Advanced Trade convert operations.
 *
 * <p>This example shows how to:
 * <ul>
 *   <li>Create a convert quote</li>
 *   <li>Optionally commit the convert trade (guarded)</li>
 *   <li>Fetch the trade status by trade id</li>
 * </ul>
 *
 * <p><b>Note:</b> Convert operations require API authentication.
 * The commit step changes balances and is disabled by default.
 */
public class CoinbaseConvertDemo {

  public static void main(String[] args) throws IOException {
    Exchange coinbase = CoinbaseDemoUtils.createExchange();
    if (!CoinbaseDemoUtils.isAuthConfigured(coinbase)) {
      System.out.println("No API credentials found (secret.keys). Convert examples require auth.");
      System.out.println("Add a secret.keys file or set API credentials before running.");
      return;
    }

    CoinbaseAccountService accountService = (CoinbaseAccountService) coinbase.getAccountService();
    CoinbaseTradeService tradeService = (CoinbaseTradeService) coinbase.getTradeService();

    String fromCurrency =
        readOptional("coinbase.convert.fromCurrency", "COINBASE_CONVERT_FROM_CURRENCY");
    String toCurrency =
        readOptional("coinbase.convert.toCurrency", "COINBASE_CONVERT_TO_CURRENCY");
    BigDecimal amount = readDecimal("coinbase.convert.amount", "COINBASE_CONVERT_AMOUNT");

    if (fromCurrency == null || toCurrency == null || amount == null) {
      System.out.println("Convert demo requires the following settings:");
      System.out.println("  coinbase.convert.fromCurrency / COINBASE_CONVERT_FROM_CURRENCY");
      System.out.println("  coinbase.convert.toCurrency / COINBASE_CONVERT_TO_CURRENCY");
      System.out.println("  coinbase.convert.amount / COINBASE_CONVERT_AMOUNT");
      return;
    }

    List<CoinbaseAccount> accounts = accountService.getCoinbaseAccounts();
    CoinbaseAccount fromAccount = findAccount(accounts, fromCurrency);
    CoinbaseAccount toAccount = findAccount(accounts, toCurrency);

    if (fromAccount == null || toAccount == null) {
      System.out.println("Unable to locate matching accounts for the requested currencies.");
      System.out.println("From currency: " + fromCurrency + ", to currency: " + toCurrency);
      return;
    }

    CoinbaseConvertQuoteRequest quoteRequest = new CoinbaseConvertQuoteRequest(
        fromAccount.getUuid(),
        toAccount.getUuid(),
        amount,
        null);
    CoinbaseConvertQuoteResponse quote = tradeService.createConvertQuote(quoteRequest);
    System.out.println("Convert Quote: " + quote);

    String tradeId = quote.getTradeId();
    if (tradeId == null || tradeId.trim().isEmpty()) {
      System.out.println("Convert quote did not return a trade id. Aborting.");
      return;
    }

    if (isEnabled("coinbase.convert.commit", "COINBASE_CONVERT_COMMIT")) {
      CoinbaseCommitConvertTradeRequest commitRequest =
          new CoinbaseCommitConvertTradeRequest(fromAccount.getUuid(), toAccount.getUuid());
      CoinbaseConvertTradeResponse trade =
          tradeService.commitConvertTrade(tradeId, commitRequest);
      System.out.println("Convert Trade Commit: " + trade);
    } else {
      System.out.println("Commit skipped. Set coinbase.convert.commit or COINBASE_CONVERT_COMMIT to execute.");
    }

    CoinbaseConvertTradeResponse status = tradeService.getConvertTrade(tradeId);
    System.out.println("Convert Trade Status: " + status);
  }

  private static CoinbaseAccount findAccount(List<CoinbaseAccount> accounts, String currency) {
    if (accounts == null || currency == null) {
      return null;
    }
    for (CoinbaseAccount account : accounts) {
      if (currency.equalsIgnoreCase(account.getCurrency())) {
        return account;
      }
    }
    return null;
  }

  private static boolean isEnabled(String propertyKey, String envKey) {
    String propertyValue = System.getProperty(propertyKey);
    if (propertyValue == null || propertyValue.trim().isEmpty()) {
      propertyValue = System.getenv(envKey);
    }
    if (propertyValue == null) {
      return false;
    }
    String normalized = propertyValue.trim().toLowerCase();
    return "true".equals(normalized) || "1".equals(normalized) || "yes".equals(normalized);
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

  private static BigDecimal readDecimal(String propertyKey, String envKey) {
    String value = readOptional(propertyKey, envKey);
    if (value == null) {
      return null;
    }
    return new BigDecimal(value);
  }
}
