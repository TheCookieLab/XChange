package org.knowm.xchange.krakenfutures.service;

import java.io.IOException;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.krakenfutures.KrakenFuturesAdapters;
import org.knowm.xchange.krakenfutures.dto.account.KrakenFuturesAccountLog;
import org.knowm.xchange.krakenfutures.dto.account.KrakenFuturesFundingHistory;
import org.knowm.xchange.service.account.AccountService;

/**
 * @author Jean-Christophe Laruelle
 */
public class KrakenFuturesAccountService extends KrakenFuturesAccountServiceRaw
    implements AccountService {

  /**
   * Constructor
   *
   * @param exchange
   */
  public KrakenFuturesAccountService(Exchange exchange) {

    super(exchange);
  }

  @Override
  public AccountInfo getAccountInfo() throws IOException {

    return KrakenFuturesAdapters.adaptAccounts(
        getKrakenFuturesAccounts(), getKrakenFuturesOpenPositions());
  }

  /**
   * Fetches the account activity log; see {@link
   * KrakenFuturesAccountServiceRaw#getKrakenFuturesAccountLog}.
   */
  public KrakenFuturesAccountLog getAccountLog(String since, Integer maxCount) throws IOException {

    return super.getKrakenFuturesAccountLog(since, maxCount, null, null);
  }

  /**
   * Fetches funding payments after the given time; see {@link
   * KrakenFuturesAccountServiceRaw#getKrakenFuturesFundingHistory}.
   */
  public KrakenFuturesFundingHistory getFundingHistory(String lastFundingTime) throws IOException {

    return super.getKrakenFuturesFundingHistory(lastFundingTime);
  }
}
