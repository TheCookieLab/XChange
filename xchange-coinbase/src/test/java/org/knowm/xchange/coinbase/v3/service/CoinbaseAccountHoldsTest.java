package org.knowm.xchange.coinbase.v3.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.coinbase.v3.CoinbaseAuthenticated;
import org.knowm.xchange.coinbase.v3.CoinbaseExchange;
import org.knowm.xchange.coinbase.v3.dto.accounts.CoinbaseAccount;
import org.knowm.xchange.coinbase.v3.dto.accounts.CoinbaseAccountsResponse;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.dto.account.Balance;
import org.knowm.xchange.exceptions.ExchangeException;
import si.mazi.rescu.ParamsDigest;

/**
 * Coinbase reports {@code available_balance} and {@code hold} separately, so a balance's total is
 * their sum. These run real response JSON through the account service, with only the REST call
 * stubbed.
 */
public class CoinbaseAccountHoldsTest {

  private static final String FIXTURE =
      "/org/knowm/xchange/coinbase/dto/v3/accounts/example-accounts-with-holds-response.json";

  @Test
  public void baseHeldBySellOrdersCountsTowardItsTotal() throws IOException {
    Balance btc = fixtureAccounts().getWallet("btc-account").getBalance(Currency.BTC);

    assertAmount("2", btc.getAvailable());
    assertAmount("3", btc.getFrozen());
    assertAmount("5", btc.getTotal());
  }

  @Test
  public void quoteHeldByABuyOrderCountsWithItsFee() throws IOException {
    Balance usd = fixtureAccounts().getWallet("usd-account").getBalance(Currency.USD);

    assertAmount("100.25", usd.getAvailable());
    assertAmount("50.30", usd.getFrozen());
    assertAmount("150.55", usd.getTotal());
  }

  @Test
  public void zeroHoldLeavesTheTotalAtAvailable() throws IOException {
    Balance eth = fixtureAccounts().getWallet("eth-account").getBalance(Currency.ETH);

    assertAmount("1.25", eth.getAvailable());
    assertAmount("0", eth.getFrozen());
    assertAmount("1.25", eth.getTotal());
  }

  @Test
  public void responseWithoutAHoldKeepsItsAvailableOnlyBalance() throws IOException {
    Balance sol = fixtureAccounts().getWallet("sol-account").getBalance(Currency.SOL);

    assertAmount("4", sol.getAvailable());
    assertAmount("0", sol.getFrozen());
    assertAmount("4", sol.getTotal());
  }

  @Test
  public void aMissingHoldStaysDistinguishableFromAProvenZero() throws IOException {
    CoinbaseAccountsResponse response;
    try (InputStream fixture = CoinbaseAccountHoldsTest.class.getResourceAsStream(FIXTURE)) {
      response = new ObjectMapper().readValue(fixture, CoinbaseAccountsResponse.class);
    }

    List<CoinbaseAccount> accounts = serviceReturning(response).getCoinbaseAccounts();

    assertEquals("eth-account", accounts.get(2).getUuid());
    assertEquals(0, accounts.get(2).getHold().getValue().signum());
    assertEquals("sol-account", accounts.get(3).getUuid());
    assertNull(accounts.get(3).getHold());
  }

  @Test
  public void negativeHoldIsRefusedRatherThanSubtractedFromTheTotal() throws IOException {
    CoinbaseAccountService service = serviceReturning(account("-1", "BTC"));

    ExchangeException refused = assertThrows(ExchangeException.class, service::getAccountInfo);

    assertTrue(refused.getMessage().contains("btc-account"), refused.getMessage());
  }

  @Test
  public void holdInAnotherCurrencyIsRefusedRatherThanAdded() throws IOException {
    CoinbaseAccountService service = serviceReturning(account("1", "USD"));

    ExchangeException refused = assertThrows(ExchangeException.class, service::getAccountInfo);

    assertTrue(refused.getMessage().contains("btc-account"), refused.getMessage());
  }

  private static AccountInfo fixtureAccounts() throws IOException {
    try (InputStream fixture = CoinbaseAccountHoldsTest.class.getResourceAsStream(FIXTURE)) {
      CoinbaseAccountsResponse response =
          new ObjectMapper().readValue(fixture, CoinbaseAccountsResponse.class);
      return serviceReturning(response).getAccountInfo();
    }
  }

  private static CoinbaseAccountsResponse account(String holdValue, String holdCurrency)
      throws IOException {
    return new ObjectMapper()
        .readValue(
            "{\"accounts\":[{\"uuid\":\"btc-account\",\"currency\":\"BTC\","
                + "\"available_balance\":{\"value\":\"2\",\"currency\":\"BTC\"},"
                + "\"hold\":{\"value\":\""
                + holdValue
                + "\",\"currency\":\""
                + holdCurrency
                + "\"}}],\"has_next\":false,\"cursor\":\"\",\"size\":1}",
            CoinbaseAccountsResponse.class);
  }

  private static CoinbaseAccountService serviceReturning(CoinbaseAccountsResponse response)
      throws IOException {
    CoinbaseAuthenticated authenticated = mock(CoinbaseAuthenticated.class);
    when(authenticated.listAccounts(any(ParamsDigest.class), eq(250), any())).thenReturn(response);
    CoinbaseExchange exchange = new CoinbaseExchange();
    exchange.applySpecification(exchange.getDefaultExchangeSpecification());
    return new CoinbaseAccountService(exchange, authenticated, mock(ParamsDigest.class));
  }

  private static void assertAmount(String expected, BigDecimal actual) {
    assertEquals(0, new BigDecimal(expected).compareTo(actual), "was " + actual);
  }
}
