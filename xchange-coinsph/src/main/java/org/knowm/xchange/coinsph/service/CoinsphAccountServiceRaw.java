package org.knowm.xchange.coinsph.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.knowm.xchange.client.ResilienceRegistries;
import org.knowm.xchange.coinsph.CoinsphExchange;
import org.knowm.xchange.coinsph.dto.CoinsphException;
import org.knowm.xchange.coinsph.dto.account.CoinsphAccount;
import org.knowm.xchange.coinsph.dto.account.CoinsphDepositAddress;
import org.knowm.xchange.coinsph.dto.account.CoinsphDepositRecord;
import org.knowm.xchange.coinsph.dto.account.CoinsphFundingRecord;
import org.knowm.xchange.coinsph.dto.account.CoinsphListenKey;
import org.knowm.xchange.coinsph.dto.account.CoinsphTradeFee;
import org.knowm.xchange.coinsph.dto.account.CoinsphWithdrawal;
import org.knowm.xchange.coinsph.dto.account.CoinsphWithdrawalRecord;

public class CoinsphAccountServiceRaw extends CoinsphBaseService {

  protected CoinsphAccountServiceRaw(
      CoinsphExchange exchange, ResilienceRegistries resilienceRegistries) {
    super(exchange, resilienceRegistries);
  }

  public CoinsphAccount getCoinsphAccount() throws IOException, CoinsphException {
    return decorateApiCall(
            () ->
                coinsphAuthenticated.getAccount(
                    apiKey, timestampFactory, signatureCreator, exchange.getRecvWindow()))
        .call();
  }

  public List<CoinsphTradeFee> getCoinsphTradeFees(String symbol)
      throws IOException, CoinsphException {
    return decorateApiCall(
            () ->
                coinsphAuthenticated.getTradeFee(
                    apiKey, timestampFactory, signatureCreator, symbol, exchange.getRecvWindow()))
        .call();
  }

  public List<CoinsphTradeFee> getCoinsphTradeFees() throws IOException, CoinsphException {
    return getCoinsphTradeFees(null); // Call with null symbol to get all
  }

  // User Data Stream methods
  public CoinsphListenKey createCoinsphListenKey() throws IOException, CoinsphException {
    return decorateApiCall(() -> coinsphAuthenticated.createListenKey(apiKey)).call();
  }

  public void keepAliveCoinsphListenKey(String listenKey) throws IOException, CoinsphException {
    decorateApiCall(() -> coinsphAuthenticated.keepAliveListenKey(apiKey, listenKey)).call();
  }

  public void closeCoinsphListenKey(String listenKey) throws IOException, CoinsphException {
    decorateApiCall(() -> coinsphAuthenticated.closeListenKey(apiKey, listenKey)).call();
  }

  /**
   * Request a withdrawal from Coins.ph
   *
   * @param coin Currency code (e.g., "BTC")
   * @param network Network to use for withdrawal (e.g., "BTC", "ETH", etc.)
   * @param address Destination address
   * @param amount Amount to withdraw
   * @param addressTag Address tag/memo for currencies that require it (optional)
   * @return Withdrawal ID
   * @throws IOException
   * @throws CoinsphException
   */
  public CoinsphWithdrawal withdraw(
      String coin, String network, String address, BigDecimal amount, String addressTag)
      throws IOException, CoinsphException {
    return decorateApiCall(
            () ->
                coinsphAuthenticated.withdraw(
                    apiKey,
                    timestampFactory,
                    signatureCreator,
                    coin,
                    network,
                    address,
                    amount,
                    addressTag,
                    exchange.getRecvWindow()))
        .call();
  }

  /**
   * Request a deposit address for a specific currency
   *
   * @param coin Currency code (e.g., "BTC")
   * @param network Network to use (optional, if not specified, default network will be used)
   * @return Deposit address
   * @throws IOException
   * @throws CoinsphException
   */
  public CoinsphDepositAddress requestDepositAddress(String coin, String network)
      throws IOException, CoinsphException {
    return decorateApiCall(
            () ->
                coinsphAuthenticated.getDepositAddress(
                    apiKey,
                    timestampFactory,
                    signatureCreator,
                    coin,
                    network,
                    exchange.getRecvWindow()))
        .call();
  }

  /**
   * Get deposit history
   *
   * @param coin Filter by currency (optional)
   * @param startTime Start time in milliseconds (optional)
   * @param endTime End time in milliseconds (optional)
   * @param limit Maximum number of records to return (optional)
   * @return List of deposit records
   * @throws IOException
   * @throws CoinsphException
   */
  public List<CoinsphDepositRecord> getDepositHistory(
      String coin, Long startTime, Long endTime, Integer limit)
      throws IOException, CoinsphException {
    return decorateApiCall(
            () ->
                coinsphAuthenticated.getDepositHistory(
                    apiKey,
                    timestampFactory,
                    signatureCreator,
                    coin,
                    startTime,
                    endTime,
                    limit,
                    exchange.getRecvWindow()))
        .call();
  }

  /**
   * Get withdrawal history
   *
   * @param coin Filter by currency (optional)
   * @param withdrawOrderId Filter by client order ID (optional)
   * @param startTime Start time in milliseconds (optional)
   * @param endTime End time in milliseconds (optional)
   * @param limit Maximum number of records to return (optional)
   * @return List of withdrawal records
   * @throws IOException
   * @throws CoinsphException
   */
  public List<CoinsphWithdrawalRecord> getWithdrawalHistory(
      String coin, String withdrawOrderId, Long startTime, Long endTime, Integer limit)
      throws IOException, CoinsphException {
    return decorateApiCall(
            () ->
                coinsphAuthenticated.getWithdrawalHistory(
                    apiKey,
                    timestampFactory,
                    signatureCreator,
                    coin,
                    withdrawOrderId,
                    startTime,
                    endTime,
                    limit,
                    exchange.getRecvWindow()))
        .call();
  }

  /**
   * Get combined funding history (deposits and withdrawals)
   *
   * @param params Funding history parameters
   * @return List of funding records
   * @throws IOException
   * @throws CoinsphException
   */
  public List<CoinsphFundingRecord> getFundingHistory(CoinsphFundingHistoryParams params)
      throws IOException, CoinsphException {
    List<CoinsphFundingRecord> fundingRecords = new ArrayList<>();

    // Convert parameters
    String coin = params.getCurrency() != null ? params.getCurrency().getCurrencyCode() : null;
    Long startTime = params.getStartTime() != null ? params.getStartTime().getTime() : null;
    Long endTime = params.getEndTime() != null ? params.getEndTime().getTime() : null;
    Integer limit = params.getLimit();

    // Get deposit history if requested
    if (params.isIncludeDeposits()) {
      List<CoinsphDepositRecord> depositRecords =
          getDepositHistory(coin, startTime, endTime, limit);
      for (CoinsphDepositRecord depositRecord : depositRecords) {
        fundingRecords.add(new CoinsphFundingRecord(depositRecord));
      }
    }

    // Get withdrawal history if requested
    if (params.isIncludeWithdrawals()) {
      List<CoinsphWithdrawalRecord> withdrawalRecords =
          getWithdrawalHistory(coin, null, startTime, endTime, limit);
      for (CoinsphWithdrawalRecord withdrawalRecord : withdrawalRecords) {
        fundingRecords.add(new CoinsphFundingRecord(withdrawalRecord));
      }
    }

    // Sort by timestamp (newest first)
    fundingRecords.sort((r1, r2) -> r2.getTimestamp().compareTo(r1.getTimestamp()));

    // Apply limit if specified
    if (limit != null && fundingRecords.size() > limit) {
      return fundingRecords.subList(0, limit);
    }

    return fundingRecords;
  }
}
