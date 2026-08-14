package org.knowm.xchange.okx.service;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.knowm.xchange.client.ResilienceRegistries;
import org.knowm.xchange.okx.Okx;
import org.knowm.xchange.okx.OkxAuthenticated;
import org.knowm.xchange.okx.OkxExchange;
import org.knowm.xchange.okx.dto.OkxException;
import org.knowm.xchange.okx.dto.OkxInstType;
import org.knowm.xchange.okx.dto.OkxResponse;
import org.knowm.xchange.okx.dto.marketdata.OkxCandleStick;
import org.knowm.xchange.okx.dto.marketdata.OkxCurrency;
import org.knowm.xchange.okx.dto.marketdata.OkxFundingRate;
import org.knowm.xchange.okx.dto.marketdata.OkxFundingRateHistory;
import org.knowm.xchange.okx.dto.marketdata.OkxInstrument;
import org.knowm.xchange.okx.dto.marketdata.OkxOrderbook;
import org.knowm.xchange.okx.dto.marketdata.OkxTicker;
import org.knowm.xchange.okx.dto.marketdata.OkxTrade;

/** Author: Max Gao (gaamox@tutanota.com) Created: 08-06-2021 */
public class OkxMarketDataServiceRaw extends OkxBaseService {

  public OkxMarketDataServiceRaw(OkxExchange exchange, ResilienceRegistries resilienceRegistries) {
    super(exchange, resilienceRegistries);
  }

  public OkxResponse<List<OkxInstrument>> getOkxInstruments(
      String instrumentType, String underlying, String instrumentId)
      throws OkxException, IOException {
    try {
      return decorateApiCall(
              () ->
                  okx.getInstruments(instrumentType, underlying, instrumentId, simulatedTrading()))
          .withRateLimiter(rateLimiter(Okx.instrumentsPath))
          .call();
    } catch (OkxException e) {
      throw handleError(e);
    }
  }

  /**
   * Fetches the underlyings that support trading for the given instrument type from <a
   * href="https://www.okx.com/docs-v5/en/#rest-api-public-data-get-underlying">GET
   * /api/v5/public/underlying</a>.
   *
   * <p>OKX returns the underlying list as a single nested array; this method flattens it so callers
   * receive the documented flat list shape. The returned values are required when querying OPTION
   * instruments, see {@link #getOkxInstruments(String, String, String)}.
   *
   * @param instType instrument type; OKX supports FUTURES, SWAP and OPTION
   * @return the list of underlyings, e.g. {@code BTC-USD}; empty when OKX reports none
   */
  public OkxResponse<List<String>> getOkxUnderlyings(OkxInstType instType)
      throws OkxException, IOException {
    OkxResponse<List<List<String>>> response;
    try {
      response =
          decorateApiCall(() -> okx.getUnderlyings(instType.name()))
              .withRateLimiter(rateLimiter(Okx.underlyingPath))
              .call();
    } catch (OkxException e) {
      throw handleError(e);
    }
    List<String> underlyings =
        response.getData() == null || response.getData().isEmpty()
            ? Collections.emptyList()
            : response.getData().get(0);
    return new OkxResponse<>(response.getId(), response.getCode(), response.getMsg(), underlyings);
  }

  public OkxResponse<List<OkxTicker>> getOkxTicker(String instrumentId)
      throws OkxException, IOException {
    try {
      return decorateApiCall(() -> okx.getTicker(instrumentId, simulatedTrading()))
          .withRateLimiter(rateLimiter(Okx.tickerPath))
          .call();
    } catch (OkxException e) {
      throw handleError(e);
    }
  }

  public OkxResponse<List<OkxTicker>> getOkxTickers(OkxInstType instType)
      throws OkxException, IOException {
    try {
      return decorateApiCall(() -> okx.getTickers(instType.toString(), simulatedTrading()))
          .withRateLimiter(rateLimiter(Okx.tickersPath))
          .call();
    } catch (OkxException e) {
      throw handleError(e);
    }
  }

  public OkxResponse<List<OkxFundingRate>> getOkxFundingRate(String instrumentId)
      throws OkxException, IOException {
    try {
      return decorateApiCall(() -> okx.getFundingRate(instrumentId, simulatedTrading()))
          .withRateLimiter(rateLimiter(Okx.instrumentsPath))
          .call();
    } catch (OkxException e) {
      throw handleError(e);
    }
  }

  public OkxResponse<List<OkxCurrency>> getOkxCurrencies() throws OkxException, IOException {
    try {
      OkxAuthParams auth = authParams();
      return decorateApiCall(
              () ->
                  okxAuthenticated.getCurrencies(
                      auth.apiKey(),
                      auth.signature(),
                      auth.timestamp(),
                      auth.passphrase(),
                      auth.simulatedTrading()))
          .withRateLimiter(rateLimiter(OkxAuthenticated.currenciesPath))
          .call();
    } catch (OkxException e) {
      throw handleError(e);
    }
  }

  public OkxResponse<List<OkxTrade>> getOkxTrades(String instrument, int limit)
      throws OkxException, IOException {

    return okx.getTrades(instrument, limit, simulatedTrading());
  }

  public OkxResponse<List<OkxOrderbook>> getOkxOrderbook(String instrument)
      throws OkxException, IOException {
    return okx.getOrderbook(instrument, 20, simulatedTrading());
  }

  public OkxResponse<List<OkxCandleStick>> getHistoryCandle(
      String instrument, String after, String before, String bar, String limit)
      throws OkxException, IOException {
    return decorateApiCall(
            () -> okx.getHistoryCandles(instrument, after, before, bar, limit, simulatedTrading()))
        .withRateLimiter(rateLimiter(Okx.candlesHistoryPath))
        .call();
  }

  public OkxResponse<List<OkxCandleStick>> getCandle(
      String instrument, String after, String before, String bar, String limit)
      throws OkxException, IOException {
    return okx.getCandles(instrument, after, before, bar, limit, simulatedTrading());
  }

  public List<OkxFundingRateHistory> getOkxFundingRateHistoryRaw(
      String instrument, Long startTime, Long endTime, Integer limit) throws IOException {
    return decorateApiCall(
            () -> {
              OkxResponse<List<OkxFundingRateHistory>> response =
                  okx.getFundingRateHistory(
                      instrument, endTime, startTime, limit, simulatedTrading());
              if (!response.isSuccess()) {
                throw OkxException.fromResponse(response, apiKey, secretKey, passphrase);
              }
              return response.getData();
            })
        .withRateLimiter(rateLimiter(Okx.fundingRateHistoryPath))
        .call();
  }
}
