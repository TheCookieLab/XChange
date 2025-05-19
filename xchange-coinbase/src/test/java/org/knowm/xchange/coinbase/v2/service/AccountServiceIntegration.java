package org.knowm.xchange.coinbase.v2.service;

import java.util.List;
import java.util.stream.Collectors;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.coinbase.v2.CoinbaseExchange;
import org.knowm.xchange.coinbase.v2.dto.CoinbaseException;
import org.knowm.xchange.coinbase.v2.dto.account.CoinbaseAccountData.CoinbaseAccount;
import org.knowm.xchange.coinbase.v2.dto.account.CoinbasePaymentMethodsData.CoinbasePaymentMethod;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.service.account.AccountService;
import org.knowm.xchange.utils.AuthUtils;

public class AccountServiceIntegration {

  static Exchange exchange;
  static AccountService accountService;

  @BeforeClass
  public static void beforeClass() {
    ExchangeSpecification exchangeSpecification = ExchangeFactory.INSTANCE.createExchange(CoinbaseExchange.class).getDefaultExchangeSpecification();
    AuthUtils.setApiAndSecretKey(exchangeSpecification);
    exchange = ExchangeFactory.INSTANCE.createExchange(exchangeSpecification);
    accountService = exchange.getAccountService();
  }

  @Test
  public void listAccounts() throws Exception {

    Assume.assumeNotNull(exchange.getExchangeSpecification().getApiKey());

    CoinbaseAccountService coinbaseService = (CoinbaseAccountService) accountService;
    List<CoinbaseAccount> accounts = coinbaseService.getCoinbaseAccounts();
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
  public void getAccountByCurrency() throws Exception {

    Assume.assumeNotNull(exchange.getExchangeSpecification().getApiKey());

    CoinbaseAccountService coinbaseService = (CoinbaseAccountService) accountService;
    CoinbaseAccount btcAccount = coinbaseService.getCoinbaseAccount(Currency.BTC);
    Assert.assertEquals("BTC", btcAccount.getBalance().getCurrency());
    Assert.assertEquals("BTC Wallet", btcAccount.getName());
  }
}
