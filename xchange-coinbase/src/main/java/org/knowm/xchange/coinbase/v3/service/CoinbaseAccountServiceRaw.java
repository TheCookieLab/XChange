package org.knowm.xchange.coinbase.v3.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.coinbase.v3.dto.accounts.CoinbaseAccount;
import org.knowm.xchange.coinbase.v3.dto.accounts.CoinbaseAccountsResponse;

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
   * Authenticated resource that shows the current user account for the give currency.
   *
   * @see <a
   * href="https://developers.coinbase.com/api/v2#show-an-account">developers.coinbase.com/api/v2#show-an-account</a>
   */
//  public CoinbaseAccount getCoinbaseAccount(Currency currency) throws IOException {
//    String apiKey = exchange.getExchangeSpecification().getApiKey();
//    BigDecimal timestamp = coinbaseAdvancedTrade.getTime(Coinbase.CB_VERSION_VALUE).getData()
//        .getEpoch();
//
//    return coinbaseAdvancedTrade.getAccount(Coinbase.CB_VERSION_VALUE, apiKey, authTokenCreator,
//        timestamp, currency.getCurrencyCode()).getData();
//  }

  /**
   * Authenticated resource that creates a new BTC account for the current user.
   *
   * @see <a
   * href="https://developers.coinbase.com/api/v2#create-account">developers.coinbase.com/api/v2#create-account</a>
   */
//  public CoinbaseAccount createCoinbaseAccount(String name) throws IOException {
//
//    CreateCoinbaseAccountPayload payload = new CreateCoinbaseAccountPayload(name);
//
//    String path = "/v2/accounts";
//    String apiKey = exchange.getExchangeSpecification().getApiKey();
//    BigDecimal timestamp = coinbaseAdvancedTrade.getTime(Coinbase.CB_VERSION_VALUE).getData()
//        .getEpoch();
//    String body = new ObjectMapper().writeValueAsString(payload);
//    String signature = getSignature(timestamp, HttpMethod.POST, path, body);
//    showCurl(HttpMethod.POST, apiKey, timestamp, signature, path, body);
//
//    return coinbaseAdvancedTrade.createAccount(MediaType.APPLICATION_JSON,
//        Coinbase.CB_VERSION_VALUE, apiKey, signature, timestamp, payload).getData();
//  }

  /**
   * Authenticated resource that shows the current user payment methods.
   *
   * @see <a
   * href="https://developers.coinbase.com/api/v2#list-payment-methods">developers.coinbase.com/api/v2?shell#list-payment-methods</a>
   */
//  public List<CoinbasePaymentMethod> getCoinbasePaymentMethods() throws IOException {
//    String apiKey = exchange.getExchangeSpecification().getApiKey();
//    BigDecimal timestamp = coinbaseAdvancedTrade.getTime(Coinbase.CB_VERSION_VALUE).getData()
//        .getEpoch();
//
//    return coinbaseAdvancedTrade.getPaymentMethods(Coinbase.CB_VERSION_VALUE, apiKey,
//        authTokenCreator, timestamp).getData();
//  }

  public static class CreateCoinbaseAccountPayload {

    @JsonProperty
    String name;

    CreateCoinbaseAccountPayload(String name) {
      this.name = name;
    }
  }
}
