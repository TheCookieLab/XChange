package org.knowm.xchange.gateio.service;

import java.util.ArrayList;
import org.knowm.xchange.client.ResilienceRegistries;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.gateio.GateioErrorAdapter;
import org.knowm.xchange.gateio.GateioExchange;
import org.knowm.xchange.gateio.dto.GateioContinuation;
import org.knowm.xchange.gateio.dto.GateioException;
import org.knowm.xchange.gateio.dto.GateioPage;
import org.knowm.xchange.gateio.dto.GateioPageCursor;
import org.knowm.xchange.gateio.dto.account.*;
import org.knowm.xchange.gateio.dto.account.params.GateioSubAccountTransfersParams;
import org.knowm.xchange.gateio.service.params.GateioDepositsParams;
import org.knowm.xchange.gateio.service.params.GateioFundingHistoryParams;
import org.knowm.xchange.gateio.service.params.GateioWithdrawalsParams;
import org.knowm.xchange.service.trade.params.TradeHistoryParamPaging;
import org.knowm.xchange.service.trade.params.TradeHistoryParams;
import org.knowm.xchange.service.trade.params.TradeHistoryParamsTimeSpan;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.knowm.xchange.gateio.GateioResilience.DYNAMIC_TRADING_FEE_RATE_LIMITER;
import static org.knowm.xchange.gateio.GateioResilience.LEVERAGE_RATE_LIMITER;

public class GateioAccountServiceRaw extends GateioBaseService {

  public GateioAccountServiceRaw(GateioExchange exchange, ResilienceRegistries resilienceRegistries) {
    super(exchange, resilienceRegistries);
  }

  public GateioDepositAddress getDepositAddress(Currency currency) throws IOException {
    String currencyCode = currency == null ? null : currency.getCurrencyCode();

    try {
      return gateioV4Authenticated.getDepositAddress(
          apiKey, exchange.getNonceFactory(), gateioV4ParamsDigest, currencyCode);
    } catch (GateioException e) {
      throw GateioErrorAdapter.adapt(e);
    }
  }

  public List<GateioWithdrawStatus> getWithdrawStatus(Currency currency) throws IOException {
    String currencyCode = currency == null ? null : currency.getCurrencyCode();

    try {
      return gateioV4Authenticated.getWithdrawStatus(
          apiKey, exchange.getNonceFactory(), gateioV4ParamsDigest, currencyCode);
    } catch (GateioException e) {
      throw GateioErrorAdapter.adapt(e);
    }
  }

  public List<GateioCurrencyBalance> getSpotBalances(Currency currency) throws IOException {
    String currencyCode = currency == null ? null : currency.getCurrencyCode();
    return gateioV4Authenticated.getSpotAccounts(
        apiKey, exchange.getNonceFactory(), gateioV4ParamsDigest, currencyCode);
  }

  public GateioSpotFee getSpotFee(String currencyPair) throws IOException {
    return decorateApiCall(
        () ->
            gateioV4Authenticated.getSpotFee(
                apiKey, exchange.getNonceFactory(), gateioV4ParamsDigest, currencyPair))
        .withRateLimiter(rateLimiter(DYNAMIC_TRADING_FEE_RATE_LIMITER))
        .call();
  }

  public Map<String, GateioFuturesFee> getFuturesFee(String settle, String contract) throws IOException {
    return decorateApiCall(
        () ->
            gateioV4Authenticated.getFuturesFee(
                apiKey, exchange.getNonceFactory(), gateioV4ParamsDigest, settle, contract))
        .withRateLimiter(rateLimiter(DYNAMIC_TRADING_FEE_RATE_LIMITER))
        .call();
  }

  public List<GateioWithdrawalRecord> getWithdrawals(GateioWithdrawalsParams params)
      throws IOException {
    String currency = params.getCurrency() != null ? params.getCurrency().toString() : null;
    Long from = params.getStartTime() != null ? params.getStartTime().getEpochSecond() : null;
    Long to = params.getEndTime() != null ? params.getEndTime().getEpochSecond() : null;
    return gateioV4Authenticated.getWithdrawals(
        apiKey,
        exchange.getNonceFactory(),
        gateioV4ParamsDigest,
        currency,
        from,
        to,
        params.getPageLength(),
        params.getZeroBasedPageNumber());
  }

  public List<GateioDepositRecord> getDeposits(GateioDepositsParams params) throws IOException {
    String currency = params.getCurrency() != null ? params.getCurrency().toString() : null;
    Long from = params.getStartTime() != null ? params.getStartTime().getEpochSecond() : null;
    Long to = params.getEndTime() != null ? params.getEndTime().getEpochSecond() : null;
    return gateioV4Authenticated.getDeposits(
        apiKey,
        exchange.getNonceFactory(),
        gateioV4ParamsDigest,
        currency,
        from,
        to,
        params.getPageLength(),
        params.getZeroBasedPageNumber());
  }

  public GateioWithdrawalRecord withdraw(GateioWithdrawalRequest gateioWithdrawalRequest)
      throws IOException {
    return gateioV4Authenticated.withdraw(
        apiKey, exchange.getNonceFactory(), gateioV4ParamsDigest, gateioWithdrawalRequest);
  }

  public List<GateioAddressRecord> getSavedAddresses(Currency currency) throws IOException {
    Objects.requireNonNull(currency);
    return gateioV4Authenticated.getSavedAddresses(
        apiKey, exchange.getNonceFactory(), gateioV4ParamsDigest, currency.getCurrencyCode());
  }

  public List<GateioAccountBookRecord> getAccountBookRecords(TradeHistoryParams params)
      throws IOException {
    // get arguments
    Currency currency =
        params instanceof GateioFundingHistoryParams
            ? ((GateioFundingHistoryParams) params).getCurrency()
            : null;
    String currencyCode = currency != null ? currency.toString() : null;
    String type =
        params instanceof GateioFundingHistoryParams
            ? ((GateioFundingHistoryParams) params).getType()
            : null;
    Integer pageLength =
        params instanceof TradeHistoryParamPaging
            ? ((TradeHistoryParamPaging) params).getPageLength()
            : null;
    Integer pageNumber =
        params instanceof TradeHistoryParamPaging
            ? ((TradeHistoryParamPaging) params).getPageNumber()
            : null;
    Long from = null;
    Long to = null;
    if (params instanceof TradeHistoryParamsTimeSpan) {
      TradeHistoryParamsTimeSpan paramsTimeSpan = ((TradeHistoryParamsTimeSpan) params);
      from =
          paramsTimeSpan.getStartTime() != null
              ? paramsTimeSpan.getStartTime().getTime() / 1000
              : null;
      to =
          paramsTimeSpan.getEndTime() != null ? paramsTimeSpan.getEndTime().getTime() / 1000 : null;
    }

    return gateioV4Authenticated.getAccountBookRecords(
        apiKey,
        exchange.getNonceFactory(),
        gateioV4ParamsDigest,
        currencyCode,
        from,
        to,
        pageLength,
        pageNumber,
        type);
  }

  /** Fetches one page of account book records; {@code null} cursor = first page. */
  public GateioPage<GateioAccountBookRecord> getAccountBookRecordsPage(
      GateioPageCursor cursor, TradeHistoryParams params) throws IOException {
    return fetchAccountBookPage(cursor, params, 1000);
  }

  private GateioPage<GateioAccountBookRecord> fetchAccountBookPage(
      GateioPageCursor cursor, TradeHistoryParams params, int limit) throws IOException {
    Currency currency =
        params instanceof GateioFundingHistoryParams
            ? ((GateioFundingHistoryParams) params).getCurrency()
            : null;
    String currencyCode = currency != null ? currency.toString() : null;
    String type =
        params instanceof GateioFundingHistoryParams
            ? ((GateioFundingHistoryParams) params).getType()
            : null;
    Long from = null;
    Long to = null;
    if (params instanceof TradeHistoryParamsTimeSpan) {
      TradeHistoryParamsTimeSpan timeSpan = (TradeHistoryParamsTimeSpan) params;
      from = timeSpan.getStartTime() != null ? timeSpan.getStartTime().getTime() / 1000 : null;
      to = timeSpan.getEndTime() != null ? timeSpan.getEndTime().getTime() / 1000 : null;
    }

    int page = cursor == null ? 1 : cursor.getPage();
    int skip = cursor == null ? 0 : cursor.getSkip();
    List<GateioAccountBookRecord> providerItems =
        gateioV4Authenticated.getAccountBookRecords(
            apiKey,
            exchange.getNonceFactory(),
            gateioV4ParamsDigest,
            currencyCode,
            from,
            to,
            limit,
            page,
            type);
    // resume state: drop the prefix already consumed by a previous bounded run
    List<GateioAccountBookRecord> items =
        skip == 0
            ? providerItems
            : providerItems.size() <= skip
                ? List.of()
                : new ArrayList<>(providerItems.subList(skip, providerItems.size()));
    GateioPageCursor next = providerItems.size() < limit ? null : GateioPageCursor.page(page + 1);
    return GateioPage.<GateioAccountBookRecord>builder()
        .items(items)
        .nextCursor(next)
        .build();
  }

  /**
   * Iterates account book records up to {@code maxResults}; see {@link GateioPagination#iterate}
   * for stop semantics.
   */
  public GateioContinuation<GateioAccountBookRecord> getAccountBookRecordsBounded(
      TradeHistoryParams params, int maxResults) throws IOException {
    return GateioPagination.iterate(
        cursor -> fetchAccountBookPage(cursor, params, 1000), maxResults);
  }

  public List<GateioSubAccountTransfer> getSubAccountTransfers(
      GateioSubAccountTransfersParams params) throws IOException {
    Long from = params.getStartTime() != null ? params.getStartTime().getEpochSecond() : null;
    Long to = params.getEndTime() != null ? params.getEndTime().getEpochSecond() : null;

    return gateioV4Authenticated.getSubAccountTransfers(
        apiKey,
        exchange.getNonceFactory(),
        gateioV4ParamsDigest,
        params.getSubAccountId(),
        from,
        to,
        params.getPageLength(),
        params.getZeroBasedPageNumber());
  }

  public GateioPositionLeverageUpdate setLeverage(String settle, String contract, String leverage, String cross_leverage) throws IOException {
    try {
      return decorateApiCall(
          () -> gateioV4Authenticated.updatePositionLeverage(
              apiKey,
              exchange.getNonceFactory(),
              gateioV4ParamsDigest,
              settle,
              contract,
              leverage,
              cross_leverage)).withRateLimiter(rateLimiter(LEVERAGE_RATE_LIMITER))
          .call();
    } catch (GateioException e) {
      throw GateioErrorAdapter.adapt(e);
    }
  }
}
