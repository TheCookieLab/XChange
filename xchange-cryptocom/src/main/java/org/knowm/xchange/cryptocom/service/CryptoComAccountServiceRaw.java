package org.knowm.xchange.cryptocom.service;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.knowm.xchange.client.ResilienceRegistries;
import org.knowm.xchange.cryptocom.CryptoComExchange;
import org.knowm.xchange.cryptocom.dto.CryptoComException;
import org.knowm.xchange.cryptocom.dto.CryptoComRequest;
import org.knowm.xchange.cryptocom.dto.CryptoComResponse;
import org.knowm.xchange.cryptocom.dto.account.CryptoComAccount;
import org.knowm.xchange.cryptocom.dto.account.CryptoComBalance;
import org.knowm.xchange.cryptocom.dto.account.CryptoComDepositAddress;
import org.knowm.xchange.cryptocom.dto.account.CryptoComDepositAddressResult;
import org.knowm.xchange.cryptocom.dto.account.CryptoComDepositHistoryResult;
import org.knowm.xchange.cryptocom.dto.account.CryptoComDepositRecord;
import org.knowm.xchange.cryptocom.dto.account.CryptoComFeeRate;
import org.knowm.xchange.cryptocom.dto.account.CryptoComPosition;
import org.knowm.xchange.cryptocom.dto.account.CryptoComUserBalanceHistoryRecord;
import org.knowm.xchange.cryptocom.dto.account.CryptoComWithdrawalHistoryResult;
import org.knowm.xchange.cryptocom.dto.account.CryptoComWithdrawalRecord;

public class CryptoComAccountServiceRaw extends CryptoComBaseService {

  /** Maximum provider pages fetched for one balance-history call regardless of caller limits. */
  public static final int MAX_HISTORY_PAGES = 10;

  /** Default rows per balance-history page when the caller does not specify a page size. */
  public static final int DEFAULT_HISTORY_PAGE_SIZE = 100;

  protected CryptoComAccountServiceRaw(
      CryptoComExchange exchange, ResilienceRegistries resilienceRegistries) {
    super(exchange, resilienceRegistries);
  }

  public List<CryptoComBalance> getCryptoComBalances() throws IOException, CryptoComException {
    CryptoComRequest request = buildRequest("private/user-balance", null);
    CryptoComResponse response = apiCall("private/user-balance", () -> cryptoCom.userBalance(request));
    return getDataList(response, CryptoComBalance.class);
  }

  /** Fee schedule for one instrument, or for all instruments when {@code instrumentName} is null. */
  public List<CryptoComFeeRate> getCryptoComFeeRate(String instrumentName)
      throws IOException, CryptoComException {
    Map<String, Object> params = new HashMap<>();
    if (instrumentName != null) {
      params.put("instrument_name", instrumentName);
    }
    CryptoComRequest request = buildRequest("private/get-fee-rate", params);
    CryptoComResponse response = apiCall("private/get-fee-rate", () -> cryptoCom.getFeeRate(request));
    return getDataList(response, CryptoComFeeRate.class);
  }

  /**
   * Derivative positions. {@code currency} filters the provider result when non-null; {@code null}
   * returns every position on the account.
   */
  public List<CryptoComPosition> getCryptoComPositions(String currency)
      throws IOException, CryptoComException {
    Map<String, Object> params = new HashMap<>();
    if (currency != null) {
      params.put("currency", currency);
    }
    CryptoComRequest request = buildRequest("private/get-positions", params);
    CryptoComResponse response = apiCall("private/get-positions", () -> cryptoCom.getPositions(request));
    return getDataList(response, CryptoComPosition.class);
  }

  /** Account/risk summary rows (margin risk model, account types) from {@code private/get-accounts}. */
  public List<CryptoComAccount> getCryptoComAccounts() throws IOException, CryptoComException {
    CryptoComRequest request = buildRequest("private/get-accounts", null);
    CryptoComResponse response = apiCall("private/get-accounts", () -> cryptoCom.getAccounts(request));
    return getDataList(response, CryptoComAccount.class);
  }

  /**
   * Wallet/history trail from {@code private/user-balance-history} with bounded pagination:
   * {@link #DEFAULT_HISTORY_PAGE_SIZE} rows per page, stopped at the caller cap, empty or repeated
   * pages, or {@link #MAX_HISTORY_PAGES} pages.
   */
  public List<CryptoComUserBalanceHistoryRecord> getCryptoComUserBalanceHistory(
      String currency, Long startTime, Long endTime, Integer limit)
      throws IOException, CryptoComException {
    return orEmpty(
        fetchPagesBounded(
            MAX_HISTORY_PAGES,
            DEFAULT_HISTORY_PAGE_SIZE,
            limit,
            (page, pageSize) ->
                userBalanceHistoryPage(currency, startTime, endTime, page, pageSize)));
  }

  private List<CryptoComUserBalanceHistoryRecord> userBalanceHistoryPage(
      String currency, Long startTime, Long endTime, Integer page, Integer pageSize)
      throws IOException {
    Map<String, Object> params = new HashMap<>();
    if (currency != null) {
      params.put("currency", currency);
    }
    if (startTime != null) {
      params.put("start_time", startTime);
    }
    if (endTime != null) {
      params.put("end_time", endTime);
    }
    params.put("page", page);
    params.put("page_size", pageSize);
    CryptoComRequest request = buildRequest("private/user-balance-history", params);
    CryptoComResponse response =
        apiCall("private/get-user-balance-history", () -> cryptoCom.getUserBalanceHistory(request));
    return getDataList(response, CryptoComUserBalanceHistoryRecord.class);
  }

  public List<CryptoComDepositAddress> getCryptoComDepositAddresses(String currency)
      throws IOException, CryptoComException {
    Map<String, Object> params = new HashMap<>();
    params.put("currency", currency);
    CryptoComRequest request = buildRequest("private/get-deposit-address", params);
    CryptoComResponse response = decorateApiCall(() -> cryptoCom.getDepositAddress(request)).call();
    CryptoComDepositAddressResult result =
        toObject(response.getResult(), CryptoComDepositAddressResult.class);
    return orEmpty(result == null ? null : result.getDepositAddressList());
  }

  public List<CryptoComDepositRecord> getCryptoComDepositHistory(
      String currency, Long startTime, Long endTime) throws IOException, CryptoComException {
    CryptoComRequest request =
        buildRequest(
            "private/get-deposit-history", currencyTimeRangeParams(currency, startTime, endTime));
    CryptoComResponse response = decorateApiCall(() -> cryptoCom.getDepositHistory(request)).call();
    CryptoComDepositHistoryResult result =
        toObject(response.getResult(), CryptoComDepositHistoryResult.class);
    return orEmpty(result == null ? null : result.getDepositList());
  }

  public List<CryptoComWithdrawalRecord> getCryptoComWithdrawalHistory(
      String currency, Long startTime, Long endTime) throws IOException, CryptoComException {
    CryptoComRequest request =
        buildRequest(
            "private/get-withdrawal-history",
            currencyTimeRangeParams(currency, startTime, endTime));
    CryptoComResponse response =
        decorateApiCall(() -> cryptoCom.getWithdrawalHistory(request)).call();
    CryptoComWithdrawalHistoryResult result =
        toObject(response.getResult(), CryptoComWithdrawalHistoryResult.class);
    return orEmpty(result == null ? null : result.getWithdrawalList());
  }

  public CryptoComWithdrawalRecord createCryptoComWithdrawal(
      String currency,
      String amount,
      String address,
      String networkId,
      String addressTag,
      String clientWid)
      throws IOException, CryptoComException {
    Map<String, Object> params = new HashMap<>();
    params.put("currency", currency);
    params.put("amount", amount);
    params.put("address", address);
    if (networkId != null) {
      params.put("network_id", networkId);
    }
    if (addressTag != null) {
      params.put("address_tag", addressTag);
    }
    if (clientWid != null) {
      params.put("client_wid", clientWid);
    }
    CryptoComRequest request = buildRequest("private/create-withdrawal", params);
    CryptoComResponse response = decorateApiCall(() -> cryptoCom.createWithdrawal(request)).call();
    return toObject(response.getResult(), CryptoComWithdrawalRecord.class);
  }

  private Map<String, Object> currencyTimeRangeParams(
      String currency, Long startTime, Long endTime) {
    Map<String, Object> params = new HashMap<>();
    if (currency != null) {
      params.put("currency", currency);
    }
    if (startTime != null) {
      params.put("start_ts", startTime);
    }
    if (endTime != null) {
      params.put("end_ts", endTime);
    }
    return params;
  }
}
