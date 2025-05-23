package org.knowm.xchange.coinbase.v3.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.coinbase.v3.dto.accounts.CoinbaseAccount;
import org.knowm.xchange.coinbase.v3.dto.accounts.CoinbaseAccountResponse;
import org.knowm.xchange.coinbase.v3.dto.accounts.CoinbaseAccountsResponse;
import org.knowm.xchange.coinbase.v3.dto.paymentmethods.CoinbasePaymentMethod;
import org.knowm.xchange.coinbase.v3.dto.paymentmethods.CoinbasePaymentMethodsResponse;

public class CoinbaseAccountServiceRaw extends CoinbaseBaseService {

  public CoinbaseAccountServiceRaw(Exchange exchange) {
    super(exchange);
  }

//  public CoinbaseTransactionsResponse getTransactions(String accountId) throws IOException {
//    String apiKey = exchange.getExchangeSpecification().getApiKey();
//    BigDecimal timestamp = coinbaseAdvancedTrade.getTime(Coinbase.CB_VERSION_VALUE).getData()
//        .getEpoch();
//
//    return coinbaseAdvancedTrade.getTransactions(Coinbase.CB_VERSION_VALUE, apiKey,
//        authTokenCreator, timestamp, accountId);
//  }
//
//  public Map getDeposits(String accountId) throws IOException {
//    String apiKey = exchange.getExchangeSpecification().getApiKey();
//    BigDecimal timestamp = coinbaseAdvancedTrade.getTime(Coinbase.CB_VERSION_VALUE).getData()
//        .getEpoch();
//
//    return coinbaseAdvancedTrade.getDeposits(Coinbase.CB_VERSION_VALUE, apiKey, authTokenCreator,
//        timestamp, accountId);
//  }
//
//  public Map getWithdrawals(String accountId) throws IOException {
//    String apiKey = exchange.getExchangeSpecification().getApiKey();
//    BigDecimal timestamp = coinbaseAdvancedTrade.getTime(Coinbase.CB_VERSION_VALUE).getData()
//        .getEpoch();
//
//    return coinbaseAdvancedTrade.getWithdrawals(Coinbase.CB_VERSION_VALUE, apiKey,
//        authTokenCreator, timestamp, accountId);
//  }

  /**
   * Authenticated resource that retrieves the current user's accounts.
   *
   * @see <a
   * href="https://docs.cdp.coinbase.com/coinbase-app/trade/reference/retailbrokerageapi_getaccounts">https://docs.cdp.coinbase.com/coinbase-app/trade/reference/retailbrokerageapi_getaccounts</a>
   */
  public List<CoinbaseAccount> getCoinbaseAccounts() throws IOException {
    List<CoinbaseAccount> returnList = new ArrayList<>();
    List<CoinbaseAccount> tmpList;

    String cursor = null;
    Boolean hasNext;
    do {
      CoinbaseAccountsResponse response = coinbaseAdvancedTrade.listAccounts(authTokenCreator, 10,
          cursor);
      cursor = response.getCursor();
      hasNext = response.getHasNext();
      tmpList = response.getAccounts();

      if (tmpList != null && !tmpList.isEmpty()) {
        returnList.addAll(tmpList);
      }

    } while (hasNext && cursor != null && !cursor.isEmpty());

    return returnList;
  }

  /**
   * Authenticated resource that retrieves the current user's account for the given currency.
   *
   * @see <a
   * href="https://docs.cdp.coinbase.com/coinbase-app/trade/reference/retailbrokerageapi_getaccount">https://docs.cdp.coinbase.com/coinbase-app/trade/reference/retailbrokerageapi_getaccount</a>
   */
  public CoinbaseAccount getCoinbaseAccount(String accountId) throws IOException {
    CoinbaseAccountResponse response = coinbaseAdvancedTrade.getAccount(authTokenCreator, accountId);
    return response.getAccount();
  }

  /**
   * Authenticated resource that shows the current user payment methods.
   *
   * @see <a
   * href="https://docs.cdp.coinbase.com/coinbase-app/trade/reference/retailbrokerageapi_getpaymentmethods">https://docs.cdp.coinbase.com/coinbase-app/trade/reference/retailbrokerageapi_getpaymentmethods</a>
   */
  public List<CoinbasePaymentMethod> getCoinbasePaymentMethods() throws IOException {
    return coinbaseAdvancedTrade.getPaymentMethods(authTokenCreator).getPaymentMethods();
  }

}
