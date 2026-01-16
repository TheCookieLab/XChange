package org.knowm.xchange.coinbase.v3.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.coinbase.v3.dto.accounts.CoinbaseAccount;
import org.knowm.xchange.coinbase.v3.dto.accounts.CoinbaseAccountResponse;
import org.knowm.xchange.coinbase.v3.dto.accounts.CoinbaseAccountsResponse;
import org.knowm.xchange.coinbase.v3.dto.futures.CoinbaseCurrentMarginWindowResponse;
import org.knowm.xchange.coinbase.v3.dto.futures.CoinbaseFuturesBalanceSummaryResponse;
import org.knowm.xchange.coinbase.v3.dto.futures.CoinbaseFuturesSweepRequest;
import org.knowm.xchange.coinbase.v3.dto.futures.CoinbaseFuturesSweepResponse;
import org.knowm.xchange.coinbase.v3.dto.futures.CoinbaseFuturesSweepsResponse;
import org.knowm.xchange.coinbase.v3.dto.futures.CoinbaseIntradayMarginSettingRequest;
import org.knowm.xchange.coinbase.v3.dto.futures.CoinbaseIntradayMarginSettingResponse;
import org.knowm.xchange.coinbase.v3.dto.paymentmethods.CoinbasePaymentMethod;
import org.knowm.xchange.coinbase.v3.dto.paymentmethods.CoinbasePaymentMethodResponse;
import org.knowm.xchange.coinbase.v3.dto.permissions.CoinbaseKeyPermissionsResponse;
import org.knowm.xchange.coinbase.v3.dto.perpetuals.CoinbaseAllocatePortfolioRequest;
import org.knowm.xchange.coinbase.v3.dto.perpetuals.CoinbaseAllocatePortfolioResponse;
import org.knowm.xchange.coinbase.v3.dto.perpetuals.CoinbaseMultiAssetCollateralRequest;
import org.knowm.xchange.coinbase.v3.dto.perpetuals.CoinbaseMultiAssetCollateralResponse;
import org.knowm.xchange.coinbase.v3.dto.perpetuals.CoinbasePerpetualsBalancesResponse;
import org.knowm.xchange.coinbase.v3.dto.perpetuals.CoinbasePerpetualsPortfolioSummaryResponse;
import org.knowm.xchange.coinbase.v3.dto.portfolios.CoinbaseMovePortfolioFundsRequest;
import org.knowm.xchange.coinbase.v3.dto.portfolios.CoinbasePortfolioResponse;
import org.knowm.xchange.coinbase.v3.dto.portfolios.CoinbasePortfolioRequest;
import org.knowm.xchange.coinbase.v3.dto.portfolios.CoinbasePortfoliosResponse;
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
   * Retrieves a single payment method by ID.
   *
   * @param paymentMethodId Coinbase payment method id.
   * @return The payment method details.
   * @throws IOException If there is an error communicating with the Coinbase API.
   */
  public CoinbasePaymentMethod getCoinbasePaymentMethod(String paymentMethodId) throws IOException {
    CoinbasePaymentMethodResponse response =
        coinbaseAdvancedTrade.getPaymentMethod(authTokenCreator, paymentMethodId);
    return response == null ? null : response.getPaymentMethod();
  }

  /**
   * Retrieves API key permissions for the current user.
   *
   * @return The key permissions response.
   * @throws IOException If there is an error communicating with the Coinbase API.
   */
  public CoinbaseKeyPermissionsResponse getKeyPermissions() throws IOException {
    return coinbaseAdvancedTrade.getKeyPermissions(authTokenCreator);
  }

  /**
   * Lists portfolios for the authenticated user.
   *
   * @param portfolioType Optional portfolio type filter.
   * @return The portfolios response.
   * @throws IOException If there is an error communicating with the Coinbase API.
   */
  public CoinbasePortfoliosResponse listPortfolios(String portfolioType) throws IOException {
    return coinbaseAdvancedTrade.listPortfolios(authTokenCreator, portfolioType);
  }

  /**
   * Retrieves a portfolio breakdown by portfolio UUID.
   *
   * @param portfolioUuid Portfolio UUID.
   * @return The portfolio breakdown response.
   * @throws IOException If there is an error communicating with the Coinbase API.
   */
  public CoinbasePortfolioResponse getPortfolioBreakdown(String portfolioUuid) throws IOException {
    return coinbaseAdvancedTrade.getPortfolioBreakdown(authTokenCreator, portfolioUuid);
  }

  /**
   * Creates a new portfolio.
   *
   * @param request Request payload.
   * @return The create portfolio response.
   * @throws IOException If there is an error communicating with the Coinbase API.
   */
  public CoinbasePortfolioResponse createPortfolio(CoinbasePortfolioRequest request)
      throws IOException {
    return coinbaseAdvancedTrade.createPortfolio(authTokenCreator, request);
  }

  /**
   * Edits an existing portfolio.
   *
   * @param portfolioUuid Portfolio UUID.
   * @param request Request payload.
   * @return The edit portfolio response.
   * @throws IOException If there is an error communicating with the Coinbase API.
   */
  public CoinbasePortfolioResponse editPortfolio(String portfolioUuid,
      CoinbasePortfolioRequest request) throws IOException {
    return coinbaseAdvancedTrade.editPortfolio(authTokenCreator, portfolioUuid, request);
  }

  /**
   * Deletes an existing portfolio.
   *
   * @param portfolioUuid Portfolio UUID.
   * @return The delete portfolio response.
   * @throws IOException If there is an error communicating with the Coinbase API.
   */
  public CoinbasePortfolioResponse deletePortfolio(String portfolioUuid) throws IOException {
    return coinbaseAdvancedTrade.deletePortfolio(authTokenCreator, portfolioUuid);
  }

  /**
   * Moves funds between portfolios.
   *
   * @param request Request payload.
   * @return The move portfolio funds response.
   * @throws IOException If there is an error communicating with the Coinbase API.
   */
  public CoinbasePortfolioResponse movePortfolioFunds(CoinbaseMovePortfolioFundsRequest request)
      throws IOException {
    return coinbaseAdvancedTrade.movePortfolioFunds(authTokenCreator, request);
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

  /**
   * Retrieves futures (CFM) balance summary information.
   *
   * @return The futures balance summary response.
   * @throws IOException If there is an error communicating with the Coinbase API.
   */
  public CoinbaseFuturesBalanceSummaryResponse getFuturesBalanceSummary() throws IOException {
    return coinbaseAdvancedTrade.getFuturesBalanceSummary(authTokenCreator);
  }

  /**
   * Schedules a futures sweep.
   *
   * @param request Request payload.
   * @return The sweep response.
   * @throws IOException If there is an error communicating with the Coinbase API.
   */
  public CoinbaseFuturesSweepResponse scheduleFuturesSweep(CoinbaseFuturesSweepRequest request)
      throws IOException {
    return coinbaseAdvancedTrade.scheduleFuturesSweep(authTokenCreator, request);
  }

  /**
   * Lists futures sweeps.
   *
   * @return The sweeps response.
   * @throws IOException If there is an error communicating with the Coinbase API.
   */
  public CoinbaseFuturesSweepsResponse listFuturesSweeps() throws IOException {
    return coinbaseAdvancedTrade.listFuturesSweeps(authTokenCreator);
  }

  /**
   * Cancels a futures sweep.
   *
   * @return The sweep response.
   * @throws IOException If there is an error communicating with the Coinbase API.
   */
  public CoinbaseFuturesSweepResponse cancelFuturesSweep() throws IOException {
    return coinbaseAdvancedTrade.cancelFuturesSweep(authTokenCreator);
  }

  /**
   * Retrieves the intraday margin setting.
   *
   * @return The intraday margin setting response.
   * @throws IOException If there is an error communicating with the Coinbase API.
   */
  public CoinbaseIntradayMarginSettingResponse getIntradayMarginSetting() throws IOException {
    return coinbaseAdvancedTrade.getIntradayMarginSetting(authTokenCreator);
  }

  /**
   * Updates the intraday margin setting.
   *
   * @param request Request payload.
   * @return The intraday margin setting response.
   * @throws IOException If there is an error communicating with the Coinbase API.
   */
  public CoinbaseIntradayMarginSettingResponse setIntradayMarginSetting(
      CoinbaseIntradayMarginSettingRequest request)
      throws IOException {
    return coinbaseAdvancedTrade.setIntradayMarginSetting(authTokenCreator, request);
  }

  /**
   * Retrieves the current margin window.
   *
   * @return The current margin window response.
   * @throws IOException If there is an error communicating with the Coinbase API.
   */
  public CoinbaseCurrentMarginWindowResponse getCurrentMarginWindow() throws IOException {
    return coinbaseAdvancedTrade.getCurrentMarginWindow(authTokenCreator);
  }

  /**
   * Retrieves a perpetuals portfolio summary.
   *
   * @param portfolioUuid Portfolio UUID.
   * @return The portfolio summary response.
   * @throws IOException If there is an error communicating with the Coinbase API.
   */
  public CoinbasePerpetualsPortfolioSummaryResponse getPerpetualsPortfolioSummary(
      String portfolioUuid) throws IOException {
    return coinbaseAdvancedTrade.getPerpetualsPortfolioSummary(authTokenCreator, portfolioUuid);
  }

  /**
   * Retrieves perpetuals portfolio balances.
   *
   * @param portfolioUuid Portfolio UUID.
   * @return The perpetuals balances response.
   * @throws IOException If there is an error communicating with the Coinbase API.
   */
  public CoinbasePerpetualsBalancesResponse getPerpetualsPortfolioBalances(String portfolioUuid)
      throws IOException {
    return coinbaseAdvancedTrade.getPerpetualsPortfolioBalances(authTokenCreator, portfolioUuid);
  }

  /**
   * Opts in to multi-asset collateral for perpetuals.
   *
   * @param request Request payload.
   * @return The multi-asset collateral response.
   * @throws IOException If there is an error communicating with the Coinbase API.
   */
  public CoinbaseMultiAssetCollateralResponse optInMultiAssetCollateral(
      CoinbaseMultiAssetCollateralRequest request)
      throws IOException {
    return coinbaseAdvancedTrade.optInMultiAssetCollateral(authTokenCreator, request);
  }

  /**
   * Allocates a portfolio for perpetuals.
   *
   * @param request Request payload.
   * @return The allocation response.
   * @throws IOException If there is an error communicating with the Coinbase API.
   */
  public CoinbaseAllocatePortfolioResponse allocatePortfolio(CoinbaseAllocatePortfolioRequest request)
      throws IOException {
    return coinbaseAdvancedTrade.allocatePortfolio(authTokenCreator, request);
  }

}
