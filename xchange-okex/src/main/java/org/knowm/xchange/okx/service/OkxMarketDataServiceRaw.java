package org.knowm.xchange.okx.service;

import static org.knowm.xchange.okx.OkxExchange.PARAM_PASSPHRASE;
import static org.knowm.xchange.okx.OkxExchange.PARAM_SIMULATED;

import java.io.IOException;
import java.util.Date;
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
import org.knowm.xchange.okx.dto.marketdata.OkxInstrument;
import org.knowm.xchange.okx.dto.marketdata.OkxOrderbook;
import org.knowm.xchange.okx.dto.marketdata.OkxTicker;
import org.knowm.xchange.okx.dto.marketdata.OkxTrade;
import org.knowm.xchange.okx.dto.marketdata.OkxFundingRateHistory;
import org.knowm.xchange.utils.DateUtils;

/** Author: Max Gao (gaamox@tutanota.com) Created: 08-06-2021 */
public class OkxMarketDataServiceRaw extends OkxBaseService {

  public OkxMarketDataServiceRaw(
      OkxExchange exchange, ResilienceRegistries resilienceRegistries) {
    super(exchange, resilienceRegistries);
  }

  public OkxResponse<List<OkxInstrument>> getOkxInstruments(
      String instrumentType, String underlying, String instrumentId)
      throws OkxException, IOException {
    try {
      return decorateApiCall(
              () ->
                  okx.getInstruments(
                      instrumentType,
                      underlying,
                      instrumentId,
                      (String)
                          exchange
                              .getExchangeSpecification()
                              .getExchangeSpecificParametersItem(PARAM_SIMULATED)))
          .withRateLimiter(rateLimiter(Okx.instrumentsPath))
          .call();
    } catch (OkxException e) {
      throw handleError(e);
    }
  }

  public OkxResponse<List<OkxTicker>> getOkxTicker(String instrumentId)
      throws OkxException, IOException {
    try {
      return decorateApiCall(
              () ->
                  okx.getTicker(
                      instrumentId,
                      (String)
                          exchange
                              .getExchangeSpecification()
                              .getExchangeSpecificParametersItem(PARAM_SIMULATED)))
          .withRateLimiter(rateLimiter(Okx.tickerPath))
          .call();
    } catch (OkxException e) {
      throw handleError(e);
    }
  }

  public OkxResponse<List<OkxTicker>> getOkxTickers(OkxInstType instType)
      throws OkxException, IOException {
    try {
      return decorateApiCall(
              () ->
                  okx.getTickers(
                      instType.toString(),
                      (String)
                          exchange
                              .getExchangeSpecification()
                              .getExchangeSpecificParametersItem(PARAM_SIMULATED)))
          .withRateLimiter(rateLimiter(Okx.tickersPath))
          .call();
    } catch (OkxException e) {
      throw handleError(e);
    }
  }

  public OkxResponse<List<OkxFundingRate>> getOkxFundingRate(String instrumentId)
      throws OkxException, IOException {
    try {
      return decorateApiCall(
              () ->
                  okx.getFundingRate(
                      instrumentId,
                      (String)
                          exchange
                              .getExchangeSpecification()
                              .getExchangeSpecificParametersItem(PARAM_SIMULATED)))
          .withRateLimiter(rateLimiter(Okx.instrumentsPath))
          .call();
    } catch (OkxException e) {
      throw handleError(e);
    }
  }

  public OkxResponse<List<OkxCurrency>> getOkxCurrencies() throws OkxException, IOException {
    try {
      return decorateApiCall(
              () ->
                  okxAuthenticated.getCurrencies(
                      exchange.getExchangeSpecification().getApiKey(),
                      signatureCreator,
                      DateUtils.toUTCISODateString(new Date()),
                      (String)
                          exchange
                              .getExchangeSpecification()
                              .getExchangeSpecificParametersItem(PARAM_PASSPHRASE),
                      (String)
                          exchange
                              .getExchangeSpecification()
                              .getExchangeSpecificParametersItem(PARAM_SIMULATED)))
          .withRateLimiter(rateLimiter(OkxAuthenticated.currenciesPath))
          .call();
    } catch (OkxException e) {
      throw handleError(e);
    }
  }

  public OkxResponse<List<OkxTrade>> getOkxTrades(String instrument, int limit)
      throws OkxException, IOException {

    return okx.getTrades(
        instrument,
        limit,
        (String)
            exchange.getExchangeSpecification().getExchangeSpecificParametersItem(PARAM_SIMULATED));
  }

  public OkxResponse<List<OkxOrderbook>> getOkxOrderbook(String instrument)
      throws OkxException, IOException {
    return okx.getOrderbook(
        instrument,
        20,
        (String)
            exchange.getExchangeSpecification().getExchangeSpecificParametersItem(PARAM_SIMULATED));
  }

  public OkxResponse<List<OkxCandleStick>> getHistoryCandle(
      String instrument, String after, String before, String bar, String limit)
      throws OkxException, IOException {
    return decorateApiCall(
            () ->
                okx.getHistoryCandles(
                    instrument,
                    after,
                    before,
                    bar,
                    limit,
                    (String)
                        exchange
                            .getExchangeSpecification()
                            .getExchangeSpecificParametersItem(PARAM_SIMULATED)))
        .withRateLimiter(rateLimiter(Okx.candlesHistoryPath))
        .call();
  }

  public OkxResponse<List<OkxCandleStick>> getCandle(
      String instrument, String after, String before, String bar, String limit)
      throws OkxException, IOException {
    return okx.getCandles(
        instrument,
        after,
        before,
        bar,
        limit,
        (String)
            exchange.getExchangeSpecification().getExchangeSpecificParametersItem(PARAM_SIMULATED));
  }

  public List<OkxFundingRateHistory> getOkxFundingRateHistoryRaw(
      String instrument, Long startTime, Long endTime, Integer limit) throws IOException {
    return decorateApiCall(
            () ->
                okx.getFundingRateHistory(
                        instrument,
                        endTime,
                        startTime,
                        limit,
                        (String)
                            exchange
                                .getExchangeSpecification()
                                .getExchangeSpecificParametersItem(PARAM_SIMULATED))
                    .getData())
        .withRateLimiter(rateLimiter(Okx.fundingRateHistoryPath))
        .call();
  }
}
