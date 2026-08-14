package org.knowm.xchange.okx.service;

import java.io.IOException;
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
import org.knowm.xchange.okx.dto.account.OkxPosition;
import org.knowm.xchange.okx.dto.account.OkxSetLeverageRequest;
import org.knowm.xchange.okx.dto.account.OkxSetLeverageResponse;
import org.knowm.xchange.okx.dto.account.OkxSetPositionModeRequest;
import org.knowm.xchange.okx.dto.account.OkxSetPositionModeResponse;
import org.knowm.xchange.okx.dto.account.OkxTradeFee;
import org.knowm.xchange.okx.dto.account.OkxTransferRequest;
import org.knowm.xchange.okx.dto.account.OkxTransferResponse;
import org.knowm.xchange.okx.dto.account.OkxWalletBalance;
import org.knowm.xchange.okx.dto.account.OkxWithdrawalRequest;
import org.knowm.xchange.okx.dto.account.OkxWithdrawalResponse;
import org.knowm.xchange.okx.dto.account.PiggyBalance;
import org.knowm.xchange.okx.dto.subaccount.OkxSubAccountDetails;

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
      OkxAuthParams auth = authParams();
      return decorateApiCall(
              () ->
                  okxAuthenticated.getAssetBalances(
                      currencies,
                      auth.apiKey(),
                      auth.signature(),
                      auth.timestamp(),
                      auth.passphrase(),
                      auth.simulatedTrading()))
          .withRateLimiter(rateLimiter(OkxAuthenticated.assetBalancesPath))
          .call();
    } catch (OkxException e) {
      throw handleError(e);
    }
  }

  public OkxResponse<List<OkxWalletBalance>> getWalletBalances(List<Currency> currencies)
      throws OkxException, IOException {
    try {
      OkxAuthParams auth = authParams();
      return decorateApiCall(
              () ->
                  okxAuthenticated.getWalletBalances(
                      currencies,
                      auth.apiKey(),
                      auth.signature(),
                      auth.timestamp(),
                      auth.passphrase(),
                      auth.simulatedTrading()))
          .withRateLimiter(rateLimiter(OkxAuthenticated.balancePath))
          .call();
    } catch (OkxException e) {
      throw handleError(e);
    }
  }

  public OkxResponse<List<OkxAccountPositionRisk>> getAccountPositionRisk()
      throws OkxException, IOException {
    try {
      OkxAuthParams auth = authParams();
      return decorateApiCall(
              () ->
                  okxAuthenticated.getAccountPositionRisk(
                      auth.apiKey(),
                      auth.signature(),
                      auth.timestamp(),
                      auth.passphrase(),
                      auth.simulatedTrading()))
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
      OkxAuthParams auth = authParams();
      return decorateApiCall(
              () ->
                  okxAuthenticated.assetWithdrawal(
                      auth.apiKey(),
                      auth.signature(),
                      auth.timestamp(),
                      auth.passphrase(),
                      auth.simulatedTrading(),
                      requestPayload))
          .withRateLimiter(rateLimiter(OkxAuthenticated.assetWithdrawalPath))
          .call();
    } catch (OkxException e) {
      throw handleError(e);
    }
  }

  public OkxResponse<List<OkxTransferResponse>> assetTransfer(
      String currency,
      String amount,
      String fromAccount,
      String toAccount,
      String type,
      String instrumentId,
      String toInstrumentId)
      throws OkxException, IOException {
    try {
      OkxTransferRequest requestPayload =
          OkxTransferRequest.builder()
              .currency(currency)
              .amount(amount)
              .fromAccount(fromAccount)
              .toAccount(toAccount)
              .type(type)
              .instrumentId(instrumentId)
              .toInstrumentId(toInstrumentId)
              .build();
      OkxAuthParams auth = authParams();
      return decorateApiCall(
              () ->
                  okxAuthenticated.assetTransfer(
                      auth.apiKey(),
                      auth.signature(),
                      auth.timestamp(),
                      auth.passphrase(),
                      auth.simulatedTrading(),
                      requestPayload))
          .withRateLimiter(rateLimiter(OkxAuthenticated.assetTransferPath))
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
      OkxAuthParams auth = authParams();
      return decorateApiCall(
              () ->
                  okxAuthenticated.setLeverage(
                      auth.apiKey(),
                      auth.signature(),
                      auth.timestamp(),
                      auth.passphrase(),
                      auth.simulatedTrading(),
                      requestPayload))
          .withRateLimiter(rateLimiter(OkxAuthenticated.setLeveragePath))
          .call();
    } catch (OkxException e) {
      throw handleError(e);
    }
  }

  public OkxResponse<List<OkxSetPositionModeResponse>> setPositionMode(
      String positionMode, String accountLevel) throws OkxException, IOException {
    try {
      OkxSetPositionModeRequest requestPayload =
          OkxSetPositionModeRequest.builder()
              .positionMode(positionMode)
              .accountLevel(accountLevel)
              .build();
      OkxAuthParams auth = authParams();
      return decorateApiCall(
              () ->
                  okxAuthenticated.setPositionMode(
                      auth.apiKey(),
                      auth.signature(),
                      auth.timestamp(),
                      auth.passphrase(),
                      auth.simulatedTrading(),
                      requestPayload))
          .withRateLimiter(rateLimiter(OkxAuthenticated.setPositionModePath))
          .call();
    } catch (OkxException e) {
      throw handleError(e);
    }
  }

  public OkxResponse<List<OkxDepositAddress>> getDepositAddress(String currency)
      throws OkxException, IOException {
    try {
      OkxAuthParams auth = authParams();
      return decorateApiCall(
              () ->
                  okxAuthenticated.getDepositAddress(
                      currency,
                      auth.apiKey(),
                      auth.signature(),
                      auth.timestamp(),
                      auth.passphrase(),
                      auth.simulatedTrading()))
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
      OkxAuthParams auth = authParams();
      return decorateApiCall(
              () ->
                  okxAuthenticated.getTradeFee(
                      instrumentType,
                      instrumentId,
                      underlying,
                      instFamily,
                      "normal",
                      auth.apiKey(),
                      auth.signature(),
                      auth.timestamp(),
                      auth.passphrase(),
                      auth.simulatedTrading()))
          .withRateLimiter(rateLimiter(OkxAuthenticated.tradeFeePath))
          .call();
    } catch (OkxException e) {
      throw handleError(e);
    }
  }

  public OkxResponse<List<OkxAccountConfig>> getOkxAccountConfiguration()
      throws OkxException, IOException {
    try {
      OkxAuthParams auth = authParams();
      return decorateApiCall(
              () ->
                  okxAuthenticated.getAccountConfiguration(
                      auth.apiKey(),
                      auth.signature(),
                      auth.timestamp(),
                      auth.passphrase(),
                      auth.simulatedTrading()))
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
      OkxAuthParams auth = authParams();
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
                      auth.apiKey(),
                      auth.signature(),
                      auth.timestamp(),
                      auth.passphrase(),
                      auth.simulatedTrading()))
          .withRateLimiter(rateLimiter(okxAuthenticated.currenciesPath))
          .call();
    } catch (OkxException e) {
      throw handleError(e);
    }
  }

  public OkxResponse<List<OkxBillDetails>> getBillsArchive(
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
      OkxAuthParams auth = authParams();
      return decorateApiCall(
              () ->
                  okxAuthenticated.getBillsArchive(
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
                      auth.apiKey(),
                      auth.signature(),
                      auth.timestamp(),
                      auth.passphrase(),
                      auth.simulatedTrading()))
          .withRateLimiter(rateLimiter(OkxAuthenticated.billsArchivePath))
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
      OkxAuthParams auth = authParams();
      return decorateApiCall(
              () ->
                  okxAuthenticated.changeMargin(
                      auth.apiKey(),
                      auth.signature(),
                      auth.timestamp(),
                      auth.passphrase(),
                      auth.simulatedTrading(),
                      requestPayload))
          .withRateLimiter(rateLimiter(okxAuthenticated.currenciesPath))
          .call();
    } catch (OkxException e) {
      throw handleError(e);
    }
  }

  public OkxResponse<List<OkxPosition>> getPositionsHistory(
      String instrumentType,
      String instrumentId,
      String marginMode,
      String type,
      String after,
      String before,
      String limit)
      throws OkxException, IOException {
    try {
      OkxAuthParams auth = authParams();
      return decorateApiCall(
              () ->
                  okxAuthenticated.getPositionsHistory(
                      instrumentType,
                      instrumentId,
                      marginMode,
                      type,
                      after,
                      before,
                      limit,
                      auth.apiKey(),
                      auth.signature(),
                      auth.timestamp(),
                      auth.passphrase(),
                      auth.simulatedTrading()))
          .withRateLimiter(rateLimiter(OkxAuthenticated.positionsHistoryPath))
          .call();
    } catch (OkxException e) {
      throw handleError(e);
    }
  }

  public OkxResponse<List<OkxSubAccountDetails>> getSubAccounts(Boolean enable, String subAcct)
      throws IOException {
    OkxAuthParams auth = authParams();
    return decorateApiCall(
            () ->
                this.okxAuthenticated.getSubAccountList(
                    auth.apiKey(),
                    auth.signature(),
                    auth.timestamp(),
                    auth.passphrase(),
                    auth.simulatedTrading(),
                    enable == null ? null : enable.toString(),
                    subAcct))
        .withRateLimiter(rateLimiter(OkxAuthenticated.subAccountList))
        .call();
  }

  public OkxResponse<List<OkxWalletBalance>> getSubAccountBalance(String subAcct)
      throws IOException {
    OkxAuthParams auth = authParams();
    return decorateApiCall(
            () ->
                this.okxAuthenticated.getSubAccountBalance(
                    auth.apiKey(),
                    auth.signature(),
                    auth.timestamp(),
                    auth.passphrase(),
                    auth.simulatedTrading(),
                    subAcct))
        .withRateLimiter(rateLimiter(OkxAuthenticated.subAccountList))
        .call();
  }

  public OkxResponse<List<PiggyBalance>> getPiggyBalance(String ccy) throws IOException {
    OkxAuthParams auth = authParams();
    return decorateApiCall(
            () ->
                this.okxAuthenticated.getPiggyBalance(
                    auth.apiKey(),
                    auth.signature(),
                    auth.timestamp(),
                    auth.passphrase(),
                    auth.simulatedTrading(),
                    ccy))
        .withRateLimiter(rateLimiter(OkxAuthenticated.subAccountList))
        .call();
  }
}
