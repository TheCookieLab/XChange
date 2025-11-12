package org.knowm.xchange.coinbase.v3.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.coinbase.v3.dto.accounts.CoinbaseAccount;
import org.knowm.xchange.coinbase.v3.dto.accounts.CoinbaseAccountResponse;
import org.knowm.xchange.coinbase.v3.dto.accounts.CoinbaseAccountsResponse;
import org.knowm.xchange.coinbase.v3.dto.paymentmethods.CoinbasePaymentMethod;
import org.knowm.xchange.coinbase.v3.dto.transactions.CoinbaseTransactionSummaryResponse;

/**
 * Raw account service implementation for Coinbase Advanced Trade (v3) API.
 * <p>
 * This service provides direct access to Coinbase account-related API endpoints, returning
 * Coinbase-specific DTOs without mapping to XChange objects. It extends {@link CoinbaseBaseService}
 * to provide authenticated access to account endpoints.
 * </p>
 * <p>
 * This is a "raw" service layer that returns Coinbase DTOs directly. For high-level XChange DTOs,
 * use {@link CoinbaseAccountService} which wraps this service and provides adapters.
 * </p>
 */
public class CoinbaseAccountServiceRaw extends CoinbaseBaseService {

  /**
   * Constructs a new raw account service using the exchange's default configuration.
   *
   * @param exchange The exchange instance containing API credentials and configuration.
   */
  public CoinbaseAccountServiceRaw(Exchange exchange) {
    super(exchange);
  }

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
      CoinbaseAccountsResponse response = coinbaseAdvancedTrade.listAccounts(authTokenCreator, 250,
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
    CoinbaseAccountResponse response = coinbaseAdvancedTrade.getAccount(authTokenCreator,
        accountId);
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

  /**
   * Retrieves transaction summary information including fee tiers and trading statistics.
   * <p>
   * This method authenticates the request using the stored API credentials and returns
   * a summary of transaction data filtered by the specified parameters. The response includes
   * fee tier information (maker and taker rates) and trading statistics.
   * </p>
   *
   * @param productType        Optional filter for product type (e.g., "SPOT", "FUTURE"). If null,
   *                           all product types are included.
   * @param contractExpiryType Optional filter for contract expiry type. Only applicable if
   *                           productType is "FUTURE". If null, all contract expiry types are included.
   * @param productVenue       Optional filter for product venue. If null, all venues are included.
   * @return A {@link CoinbaseTransactionSummaryResponse} containing fee tier information and
   *         trading statistics for the authenticated user, filtered by the specified parameters.
   * @throws IOException If there is an error communicating with the Coinbase API.
   */
  public CoinbaseTransactionSummaryResponse getTransactionSummary(String productType,
      String contractExpiryType, String productVenue) throws IOException {
    return coinbaseAdvancedTrade.getTransactionSummary(authTokenCreator, productType,
        contractExpiryType, productVenue);
  }

  /**
   * Retrieves transaction summary information for all products and venues.
   * <p>
   * This is a convenience method that calls {@link #getTransactionSummary(String, String, String)}
   * with all parameters set to null, returning a summary across all product types, contract expiry
   * types, and venues.
   * </p>
   *
   * @return A {@link CoinbaseTransactionSummaryResponse} containing fee tier information and
   *         trading statistics for the authenticated user across all products and venues.
   * @throws IOException If there is an error communicating with the Coinbase API.
   */
  public CoinbaseTransactionSummaryResponse getTransactionSummary() throws IOException {
    return this.getTransactionSummary(null, null, null);
  }

}
