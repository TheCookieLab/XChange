package org.knowm.xchange.examples.bitso.account;

import org.knowm.xchange.Exchange;
import org.knowm.xchange.bitso.dto.account.BitsoBalance;
import org.knowm.xchange.bitso.dto.account.BitsoWithdrawalRequest;
import org.knowm.xchange.bitso.dto.account.BitsoWithdrawalResponse;
import org.knowm.xchange.bitso.service.BitsoAccountServiceRaw;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.examples.bitso.BitsoDemoUtils;
import org.knowm.xchange.service.account.AccountService;

import java.io.IOException;
import java.math.BigDecimal;

/**
 * Example showing the following:
 *
 * <ul>
 *   <li>Connect to Bitso exchange with authentication
 *   <li>View account balance
 *   <li>Get the bitcoin deposit address
 *   <li>Withdraw a small amount of BTC
 * </ul>
 */
public class BitsoAccountDemo {

  public static void main(String[] args) throws IOException {

    Exchange bitso = BitsoDemoUtils.createExchange();
    AccountService accountService = bitso.getAccountService();

    generic(accountService);
    raw((BitsoAccountServiceRaw) accountService);
  }

  private static void generic(AccountService accountService) throws IOException {

    // Get the account information
    AccountInfo wallet = accountService.getAccountInfo();
    System.out.println("Wallet as String: " + wallet.toString());

    // Note: Deposit address functionality is not implemented in Bitso
    // String depositAddress = accountService.requestDepositAddress(Currency.BTC);
    // System.out.println("Deposit address: " + depositAddress);

    String withdrawResult =
        accountService.withdrawFunds(Currency.BTC, new BigDecimal(1).movePointLeft(4), "XXX");
    System.out.println("withdrawResult = " + withdrawResult);
  }

  private static void raw(BitsoAccountServiceRaw accountService) throws IOException {

    BitsoBalance bitsoBalance = accountService.getBitsoBalance();
    System.out.println("Bitso balance: " + bitsoBalance);

    // Note: Deposit address functionality is not implemented in Bitso
    // BitsoDepositAddress depositAddress = accountService.getBitsoBitcoinDepositAddress();
    // System.out.println("Bitcoin deposit address: " + depositAddress);

    // Example of crypto withdrawal using the new API
    BitsoWithdrawalRequest withdrawalRequest = BitsoWithdrawalRequest.builder()
        .currency("btc")
        .amount(new BigDecimal(1).movePointLeft(4))
        .address("XXX")
        .build();
    
    BitsoWithdrawalResponse withdrawResult = accountService.createBitsoCryptoWithdrawal(withdrawalRequest);
    System.out.println("Bitso withdrawal response = " + withdrawResult);
  }
}
