package org.knowm.xchange.coinbase.v3.service;

import java.util.List;
import java.util.stream.Collectors;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.coinbase.v3.CoinbaseExchange;
import org.knowm.xchange.coinbase.v3.dto.accounts.CoinbaseAccount;
import org.knowm.xchange.coinbase.v3.dto.paymentmethods.CoinbasePaymentMethod;
import org.knowm.xchange.utils.AuthUtils;

public class AccountServiceIntegration {

  static CoinbaseExchange exchange;
  static CoinbaseAccountService accountService;

  @BeforeClass
  public static void beforeClass() {
    ExchangeSpecification exchangeSpecification = ExchangeFactory.INSTANCE.createExchange(
        CoinbaseExchange.class).getDefaultExchangeSpecification();
    AuthUtils.setApiAndSecretKey(exchangeSpecification);
    exchange = (CoinbaseExchange) ExchangeFactory.INSTANCE.createExchange(exchangeSpecification);
    accountService = (CoinbaseAccountService) exchange.getAccountService();
  }

  @Test
  public void listAccounts() throws Exception {
    Assume.assumeNotNull(accountService.authTokenCreator);

    List<CoinbaseAccount> accounts = accountService.getCoinbaseAccounts();
    Assert.assertFalse(accounts.isEmpty());

    CoinbaseAccount btcAccount =
        accounts.stream()
            .filter(t -> t.getName().equals("BTC Wallet"))
            .collect(Collectors.toList())
            .get(0);
    Assert.assertEquals("BTC", btcAccount.getBalance().getCurrency());
    Assert.assertEquals("BTC Wallet", btcAccount.getName());
  }

  @Test
  public void getAccountById() throws Exception {
    Assume.assumeNotNull(accountService.authTokenCreator);

    CoinbaseAccount btcAccount = accountService.getCoinbaseAccount(
        "49c5ceb8-a14f-575b-a6c6-6bab51a73c46");
    Assert.assertEquals("BTC", btcAccount.getBalance().getCurrency());
    Assert.assertEquals("BTC Wallet", btcAccount.getName());
  }

  @Test
  public void listPaymentMethods() throws Exception {
    Assume.assumeNotNull(accountService.authTokenCreator);

    List<CoinbasePaymentMethod> methods = accountService.getCoinbasePaymentMethods();
    Assert.assertFalse(methods.isEmpty());
  }

  @Test
  public void testGetTransactionSummary() throws Exception {
    Assume.assumeNotNull(accountService.authTokenCreator);


  }
}
