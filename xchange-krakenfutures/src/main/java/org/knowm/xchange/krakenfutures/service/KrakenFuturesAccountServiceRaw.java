package org.knowm.xchange.krakenfutures.service;

import java.io.IOException;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.krakenfutures.dto.account.KrakenFuturesAccountLog;
import org.knowm.xchange.krakenfutures.dto.account.KrakenFuturesAccounts;
import org.knowm.xchange.krakenfutures.dto.account.KrakenFuturesFundingHistory;

/**
 * @author Jean-Christophe Laruelle
 */
public class KrakenFuturesAccountServiceRaw extends KrakenFuturesBaseService {

  /**
   * Constructor
   *
   * @param exchange
   */
  public KrakenFuturesAccountServiceRaw(Exchange exchange) {

    super(exchange);
  }

  public KrakenFuturesAccounts getKrakenFuturesAccounts() throws IOException {

    KrakenFuturesAccounts krakenFuturesAccounts =
        krakenFuturesAuthenticated.accounts(
            exchange.getExchangeSpecification().getApiKey(),
            signatureCreator,
            exchange.getNonceFactory());

    checkSuccess(krakenFuturesAccounts, "getKrakenFuturesAccounts");
    return krakenFuturesAccounts;
  }

  /**
   * Fetches the account activity log (deposits, withdrawals, transfers, fee payments, funding).
   *
   * <p>Entries are returned newest first and are ordered by the provider-generated id, which is
   * the stable cursor for incremental pulls.
   *
   * @param since only return entries on or after this RFC3339 time, or {@code null}
   * @param maxCount maximum number of entries, or {@code null} for the provider default
   * @param before only return entries older than this log id, or {@code null}
   * @param after only return entries newer than this log id, or {@code null}
   * @return typed account log with per-entry id/time/type/amount/wallet/balance
   */
  public KrakenFuturesAccountLog getKrakenFuturesAccountLog(
      String since, Integer maxCount, String before, String after) throws IOException {

    KrakenFuturesAccountLog accountLog =
        krakenFuturesAuthenticated.accountLog(
            since,
            maxCount,
            before,
            after,
            exchange.getExchangeSpecification().getApiKey(),
            signatureCreator,
            exchange.getNonceFactory());

    checkSuccess(accountLog, "getKrakenFuturesAccountLog");
    return accountLog;
  }

  /**
   * Fetches funding payments applied to the account.
   *
   * <p>Passing the most recently seen funding time makes incremental pulls cheap: only payments
   * after that time are returned.
   *
   * @param lastFundingTime RFC3339 time of the last funding payment already processed, or {@code
   *     null} for the full history
   * @return typed funding history with per-payment instrument/rate/amount
   */
  public KrakenFuturesFundingHistory getKrakenFuturesFundingHistory(String lastFundingTime)
      throws IOException {

    KrakenFuturesFundingHistory fundingHistory =
        krakenFuturesAuthenticated.fundingHistory(
            lastFundingTime,
            exchange.getExchangeSpecification().getApiKey(),
            signatureCreator,
            exchange.getNonceFactory());

    checkSuccess(fundingHistory, "getKrakenFuturesFundingHistory");
    return fundingHistory;
  }
}
