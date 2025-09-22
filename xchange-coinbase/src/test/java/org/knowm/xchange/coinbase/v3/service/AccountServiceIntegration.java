package org.knowm.xchange.coinbase.v3.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
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
import org.knowm.xchange.coinbase.v3.dto.transactions.CoinbaseFeeTier;
import org.knowm.xchange.coinbase.v3.dto.transactions.CoinbaseTransactionSummaryResponse;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.account.Fee;
import org.knowm.xchange.instrument.Instrument;
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

    CoinbaseAccount btcAccount = accounts.stream().filter(t -> t.getName().equals("BTC Wallet"))
        .collect(Collectors.toList()).get(0);
    assertEquals("BTC", btcAccount.getBalance().getCurrency());
    assertEquals("BTC Wallet", btcAccount.getName());
  }

  @Test
  public void getAccountById() throws Exception {
    Assume.assumeNotNull(accountService.authTokenCreator);

    CoinbaseAccount btcAccount = accountService.getCoinbaseAccount(
        "49c5ceb8-a14f-575b-a6c6-6bab51a73c46");
    assertEquals("BTC", btcAccount.getBalance().getCurrency());
    assertEquals("BTC Wallet", btcAccount.getName());
  }

  @Test
  public void listPaymentMethods() throws Exception {
    Assume.assumeNotNull(accountService.authTokenCreator);

    List<CoinbasePaymentMethod> methods = accountService.getCoinbasePaymentMethods();
    Assert.assertFalse(methods.isEmpty());
  }

  @Test
  public void testGetTransactionSummaryReturnsFeeRates() throws Exception {
    Assume.assumeNotNull(accountService.authTokenCreator);

    CoinbaseTransactionSummaryResponse transactionSummary = accountService.getTransactionSummary();
    CoinbaseFeeTier feeTier = transactionSummary.getFeeTier();

    assertEquals(1, feeTier.getMakerFeeRate().compareTo(BigDecimal.ZERO));
    assertEquals(1, feeTier.getTakerFeeRate().compareTo(BigDecimal.ZERO));
  }

  @Test
  public void testGetDynamicTradingFeesByInstrumentReturnsGlobalFees() throws Exception {
    Assume.assumeNotNull(accountService.authTokenCreator);

    Map<Instrument, Fee> fees = accountService.getDynamicTradingFeesByInstrument();

    Fee btcUsdFee = fees.get(CurrencyPair.BTC_USD);
    assertNotNull(btcUsdFee);
    assertEquals(1, btcUsdFee.getMakerFee().compareTo(BigDecimal.ZERO));
    assertEquals(1, btcUsdFee.getTakerFee().compareTo(BigDecimal.ZERO));

    Fee ethUsdFee = fees.get(CurrencyPair.ETH_USD);
    assertNotNull(ethUsdFee);
    assertEquals(1, ethUsdFee.getMakerFee().compareTo(BigDecimal.ZERO));
    assertEquals(1, ethUsdFee.getTakerFee().compareTo(BigDecimal.ZERO));
  }
}
