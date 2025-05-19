package org.knowm.xchange.coinbase.v2.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.coinbase.v2.Coinbase;
import org.knowm.xchange.coinbase.v2.dto.account.CoinbaseAccountData.CoinbaseAccount;
import org.knowm.xchange.coinbase.v2.dto.account.CoinbaseTransactionsResponse;
import org.knowm.xchange.currency.Currency;

public class CoinbaseAccountServiceRaw extends CoinbaseBaseService {

  public CoinbaseAccountServiceRaw(Exchange exchange) {
    super(exchange);
  }

  public CoinbaseTransactionsResponse getTransactions(String accountId) throws IOException {
    String apiKey = exchange.getExchangeSpecification().getApiKey();
    BigDecimal timestamp = coinbase.getTime(Coinbase.CB_VERSION_VALUE).getData().getEpoch();

    return coinbase.getTransactions(Coinbase.CB_VERSION_VALUE, apiKey, authTokenGenerator,
        timestamp, accountId);
  }

  public Map getDeposits(String accountId) throws IOException {
    String apiKey = exchange.getExchangeSpecification().getApiKey();
    BigDecimal timestamp = coinbase.getTime(Coinbase.CB_VERSION_VALUE).getData().getEpoch();

    return coinbase.getDeposits(Coinbase.CB_VERSION_VALUE, apiKey, authTokenGenerator, timestamp,
        accountId);
  }

  public Map getWithdrawals(String accountId) throws IOException {
    String apiKey = exchange.getExchangeSpecification().getApiKey();
    BigDecimal timestamp = coinbase.getTime(Coinbase.CB_VERSION_VALUE).getData().getEpoch();

    return coinbase.getWithdrawals(Coinbase.CB_VERSION_VALUE, apiKey, authTokenGenerator, timestamp,
        accountId);
  }

  /**
   * Authenticated resource that shows the current user accounts.
   *
   * @see <a
   * href="https://developers.coinbase.com/api/v2#list-accounts">developers.coinbase.com/api/v2#list-accounts</a>
   */
  public List<CoinbaseAccount> getCoinbaseAccounts() throws IOException {
    List<CoinbaseAccount> returnList = new ArrayList<>();
    List<CoinbaseAccount> tmpList = null;

    String lastAccount = null;
    do {
      tmpList = coinbase.getAccounts(authTokenGenerator, 100, lastAccount).getData();

      lastAccount = null;
      if (tmpList != null && tmpList.size() > 0) {
        returnList.addAll(tmpList);
        lastAccount = tmpList.get(tmpList.size() - 1).getId();
      }

    } while (lastAccount != null && isValidUUID(lastAccount));

    return returnList;
  }

  private boolean isValidUUID(String uuid) {
    try {
      UUID.fromString(uuid);
      return true;
    } catch (IllegalArgumentException exception) {
      return false;
    }
  }

  /**
   * Authenticated resource that shows the current user account for the give currency.
   *
   * @see <a
   * href="https://developers.coinbase.com/api/v2#show-an-account">developers.coinbase.com/api/v2#show-an-account</a>
   */
  public CoinbaseAccount getCoinbaseAccount(Currency currency) throws IOException {
    return coinbase.getAccount(authTokenGenerator, currency.getCurrencyCode()).getData();
  }

}
