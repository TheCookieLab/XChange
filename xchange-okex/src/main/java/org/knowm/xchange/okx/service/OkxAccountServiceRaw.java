package org.knowm.xchange.okx.service;

import static org.knowm.xchange.okx.OkxExchange.PARAM_PASSPHRASE;
import static org.knowm.xchange.okx.OkxExchange.PARAM_SIMULATED;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import org.knowm.xchange.client.ResilienceRegistries;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.okx.OkxAuthenticated;
import org.knowm.xchange.okx.OkxExchange;
import org.knowm.xchange.okx.dto.OkxException;
import org.knowm.xchange.okx.dto.OkxResponse;
import org.knowm.xchange.okx.dto.account.OkxAccountConfig;
import org.knowm.xchange.okx.dto.account.OkxAccountPositionRisk;
import org.knowm.xchange.okx.dto.account.OkxAssetBalance;
import org.knowm.xchange.okx.dto.account.OkxBillDetails;
import org.knowm.xchange.okx.dto.account.OkxChangeMarginRequest;
import org.knowm.xchange.okx.dto.account.OkxChangeMarginResponse;
import org.knowm.xchange.okx.dto.account.OkxDepositAddress;
import org.knowm.xchange.okx.dto.account.OkxSetLeverageRequest;
import org.knowm.xchange.okx.dto.account.OkxSetLeverageResponse;
import org.knowm.xchange.okx.dto.account.OkxTradeFee;
import org.knowm.xchange.okx.dto.account.OkxWalletBalance;
import org.knowm.xchange.okx.dto.account.OkxWithdrawalRequest;
import org.knowm.xchange.okx.dto.account.OkxWithdrawalResponse;
import org.knowm.xchange.okx.dto.account.PiggyBalance;
import org.knowm.xchange.okx.dto.subaccount.OkxSubAccountDetails;
import org.knowm.xchange.utils.DateUtils;

/** Author: Max Gao (gaamox@tutanota.com) Created: 08-06-2021 */
public class OkxAccountServiceRaw extends OkxBaseService {

  public static final String INTERNAL_METHOD = "3";
  public static final String ON_CHAIN_METHOD = "4";

  public OkxAccountServiceRaw(OkxExchange exchange, ResilienceRegistries resilienceRegistries) {
    super(exchange, resilienceRegistries);
  }

  public OkxResponse<List<OkxAssetBalance>> getAssetBalances(List<Currency> currencies)
      throws OkxException, IOException {
    try {
      return decorateApiCall(
              () ->
                  okxAuthenticated.getAssetBalances(
                      currencies,
                      exchange.getExchangeSpecification().getApiKey(),
                      signatureCreator,
                      DateUtils.toUTCISODateString(new Date()),
                      (String)
                          exchange
                              .getExchangeSpecification()
                              .getExchangeSpecificParametersItem("passphrase"),
                      (String)
                          exchange
                              .getExchangeSpecification()
                              .getExchangeSpecificParametersItem("simulated")))
          .withRateLimiter(rateLimiter(OkxAuthenticated.assetBalancesPath))
          .call();
    } catch (OkxException e) {
      throw handleError(e);
    }
  }

  public OkxResponse<List<OkxWalletBalance>> getWalletBalances(List<Currency> currencies)
      throws OkxException, IOException {
    try {
      return decorateApiCall(
              () ->
                  okxAuthenticated.getWalletBalances(
                      currencies,
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
          .withRateLimiter(rateLimiter(OkxAuthenticated.balancePath))
          .call();
    } catch (OkxException e) {
      throw handleError(e);
    }
  }

  public OkxResponse<List<OkxAccountPositionRisk>> getAccountPositionRisk()
      throws OkxException, IOException {
    try {
      return decorateApiCall(
              () ->
                  okxAuthenticated.getAccountPositionRisk(
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
          .withRateLimiter(rateLimiter(OkxAuthenticated.balancePath))
          .call();
    } catch (OkxException e) {
      throw handleError(e);
    }
  }

  public OkxResponse<List<OkxWithdrawalResponse>> assetWithdrawal(
      String currency,
      String amount,
      String method,
      String address,
      String fee,
      String chain,
      String clientId)
      throws OkxException, IOException {
    try {
      OkxWithdrawalRequest requestPayload =
          OkxWithdrawalRequest.builder()
              .currency(currency)
              .amount(amount)
              .method(method)
              .address(address)
              .fee(fee)
              .chain(chain)
              .clientId(clientId)
              .build();
      return decorateApiCall(
              () ->
                  okxAuthenticated.assetWithdrawal(
                      exchange.getExchangeSpecification().getApiKey(),
                      signatureCreator,
                      DateUtils.toUTCISODateString(new Date()),
                      (String)
                          exchange
                              .getExchangeSpecification()
                              .getExchangeSpecificParametersItem("passphrase"),
                      (String)
                          exchange
                              .getExchangeSpecification()
                              .getExchangeSpecificParametersItem("simulated"),
                      requestPayload))
          .withRateLimiter(rateLimiter(OkxAuthenticated.assetWithdrawalPath))
          .call();
    } catch (OkxException e) {
      throw handleError(e);
    }
  }

  public OkxResponse<List<OkxSetLeverageResponse>> setLeverage(
      String instrumentId, String currency, String leverage, String marginMode, String positionSide)
      throws OkxException, IOException {
    try {
      OkxSetLeverageRequest requestPayload =
          OkxSetLeverageRequest.builder()
              .instrumentId(instrumentId)
              .currency(currency)
              .leverage(leverage)
              .marginMode(marginMode)
              .positionSide(positionSide)
              .build();
      return decorateApiCall(
              () ->
                  okxAuthenticated.setLeverage(
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
                              .getExchangeSpecificParametersItem(PARAM_SIMULATED),
                      requestPayload))
          .withRateLimiter(rateLimiter(OkxAuthenticated.setLeveragePath))
          .call();
    } catch (OkxException e) {
      throw handleError(e);
    }
  }

  public OkxResponse<List<OkxDepositAddress>> getDepositAddress(String currency)
      throws OkxException, IOException {
    try {
      return decorateApiCall(
              () ->
                  okxAuthenticated.getDepositAddress(
                      currency,
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
          .withRateLimiter(rateLimiter(OkxAuthenticated.depositAddressPath))
          .call();
    } catch (OkxException e) {
      throw handleError(e);
    }
  }

  public OkxResponse<List<OkxTradeFee>> getTradeFee(
      String instrumentType, String instrumentId, String underlying, String instFamily)
      throws IOException, OkxException {
    try {
      return decorateApiCall(
              () ->
                  okxAuthenticated.getTradeFee(
                      instrumentType,
                      instrumentId,
                      underlying,
                      instFamily,
                      "normal",
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
          .withRateLimiter(rateLimiter(OkxAuthenticated.tradeFeePath))
          .call();
    } catch (OkxException e) {
      throw handleError(e);
    }
  }

  public OkxResponse<List<OkxAccountConfig>> getOkxAccountConfiguration()
      throws OkxException, IOException {
    try {
      return decorateApiCall(
              () ->
                  okxAuthenticated.getAccountConfiguration(
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
          .withRateLimiter(rateLimiter(okxAuthenticated.currenciesPath))
          .call();
    } catch (OkxException e) {
      throw handleError(e);
    }
  }

  public OkxResponse<List<OkxBillDetails>> getBills(
      String instrumentType,
      String currency,
      String marginMode,
      String contractType,
      String billType,
      String billSubType,
      String afterBillId,
      String beforeBillId,
      String beginTimestamp,
      String endTimestamp,
      String maxNumberOfResults)
      throws OkxException, IOException {
    try {
      return decorateApiCall(
              () ->
                  okxAuthenticated.getBills(
                      instrumentType,
                      currency,
                      marginMode,
                      contractType,
                      billType,
                      billSubType,
                      afterBillId,
                      beforeBillId,
                      beginTimestamp,
                      endTimestamp,
                      maxNumberOfResults,
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
          .withRateLimiter(rateLimiter(okxAuthenticated.currenciesPath))
          .call();
    } catch (OkxException e) {
      throw handleError(e);
    }
  }

  public OkxResponse<List<OkxChangeMarginResponse>> changeMargin(
      String instrumentId,
      String positionSide,
      String type,
      String amount,
      String currency,
      boolean auto,
      boolean loadTrans)
      throws OkxException, IOException {
    try {
      OkxChangeMarginRequest requestPayload =
          OkxChangeMarginRequest.builder()
              .instrumentId(instrumentId)
              .posSide(positionSide)
              .type(type)
              .amount(amount)
              .currency(currency)
              .auto(auto)
              .loanTrans(loadTrans)
              .build();
      return decorateApiCall(
              () ->
                  okxAuthenticated.changeMargin(
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
                              .getExchangeSpecificParametersItem(PARAM_SIMULATED),
                      requestPayload))
          .withRateLimiter(rateLimiter(okxAuthenticated.currenciesPath))
          .call();
    } catch (OkxException e) {
      throw handleError(e);
    }
  }

  public OkxResponse<List<OkxSubAccountDetails>> getSubAccounts(Boolean enable, String subAcct)
      throws IOException {
    return decorateApiCall(
            () ->
                this.okxAuthenticated.getSubAccountList(
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
                            .getExchangeSpecificParametersItem(PARAM_SIMULATED),
                    enable == null ? null : enable.toString(),
                    subAcct))
        .withRateLimiter(rateLimiter(OkxAuthenticated.subAccountList))
        .call();
  }

  public OkxResponse<List<OkxWalletBalance>> getSubAccountBalance(String subAcct)
      throws IOException {
    return decorateApiCall(
            () ->
                this.okxAuthenticated.getSubAccountBalance(
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
                            .getExchangeSpecificParametersItem(PARAM_SIMULATED),
                    subAcct))
        .withRateLimiter(rateLimiter(OkxAuthenticated.subAccountList))
        .call();
  }

  public OkxResponse<List<PiggyBalance>> getPiggyBalance(String ccy) throws IOException {
    return decorateApiCall(
            () ->
                this.okxAuthenticated.getPiggyBalance(
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
                            .getExchangeSpecificParametersItem(PARAM_SIMULATED),
                    ccy))
        .withRateLimiter(rateLimiter(OkxAuthenticated.subAccountList))
        .call();
  }
}
