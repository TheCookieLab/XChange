package org.knowm.xchange.bybit.service;

import static org.knowm.xchange.bybit.BybitAdapters.createBybitExceptionFromResult;
import static org.knowm.xchange.bybit.BybitResilience.GLOBAL_RATE_LIMITER;
import static org.knowm.xchange.bybit.BybitResilience.POSITION_SET_LEVERAGE_INVERSE_RATE_LIMITER;
import static org.knowm.xchange.bybit.BybitResilience.POSITION_SET_LEVERAGE_LINEAR_RATE_LIMITER;

import io.github.resilience4j.ratelimiter.RateLimiter;
import java.io.IOException;
import org.knowm.xchange.bybit.BybitExchange;
import org.knowm.xchange.bybit.dto.BybitCategorizedPayload;
import org.knowm.xchange.bybit.dto.BybitCategory;
import org.knowm.xchange.bybit.dto.BybitResult;
import org.knowm.xchange.bybit.dto.account.BybitAccountInfoResponse;
import org.knowm.xchange.bybit.dto.account.BybitBorrowHistory;
import org.knowm.xchange.bybit.dto.account.BybitBorrowableAmount;
import org.knowm.xchange.bybit.dto.account.BybitCoinInfos;
import org.knowm.xchange.bybit.dto.account.BybitCollateralInfos;
import org.knowm.xchange.bybit.dto.account.BybitTransactionLog;
import org.knowm.xchange.bybit.dto.account.BybitTransferPayload;
import org.knowm.xchange.bybit.dto.account.BybitTransferResponse;
import org.knowm.xchange.bybit.dto.account.allcoins.BybitAllCoinsBalance;
import org.knowm.xchange.bybit.dto.account.feerates.BybitFeeRates;
import org.knowm.xchange.bybit.dto.account.position.BybitSetLeveragePayload;
import org.knowm.xchange.bybit.dto.account.position.BybitSwitchModePayload;
import org.knowm.xchange.bybit.dto.account.walletbalance.BybitAccountType;
import org.knowm.xchange.bybit.dto.account.walletbalance.BybitWalletBalance;
import org.knowm.xchange.client.ResilienceRegistries;

public class BybitAccountServiceRaw extends BybitBaseService {

  protected BybitAccountServiceRaw(
      BybitExchange exchange, ResilienceRegistries resilienceRegistries) {
    super(exchange, resilienceRegistries);
  }

  BybitResult<BybitWalletBalance> getWalletBalances(BybitAccountType accountType)
      throws IOException {
    BybitResult<BybitWalletBalance> walletBalances =
        bybitAuthenticated.getWalletBalance(
            apiKey, signatureCreator, exchange.getTimeStampFactory(), accountType.name());
    if (!walletBalances.isSuccess()) {
      throw createBybitExceptionFromResult(walletBalances);
    }
    return walletBalances;
  }

  BybitResult<BybitAllCoinsBalance> getAllCoinsBalance(BybitAccountType accountType)
      throws IOException {
    BybitResult<BybitAllCoinsBalance> allCoinsBalance =
        bybitAuthenticated.getAllCoinsBalance(
            apiKey, signatureCreator, exchange.getTimeStampFactory(), accountType.name());
    if (!allCoinsBalance.isSuccess()) {
      throw createBybitExceptionFromResult(allCoinsBalance);
    }
    return allCoinsBalance;
  }

  BybitResult<BybitFeeRates> getFeeRatesRaw(BybitCategory category, String symbol)
      throws IOException {
    BybitResult<BybitFeeRates> bybitFeeRatesResult;
    bybitFeeRatesResult =
        bybitAuthenticated.getFeeRate(
            apiKey, signatureCreator, exchange.getTimeStampFactory(), category.getValue(), symbol);

    if (!bybitFeeRatesResult.isSuccess()) {
      throw createBybitExceptionFromResult(bybitFeeRatesResult);
    }
    return bybitFeeRatesResult;
  }

  BybitResult<Object> setLeverageRaw(BybitCategory category, String symbol, int leverage)
      throws IOException {
    String leverageString = Integer.toString(leverage);
    BybitSetLeveragePayload payload =
        new BybitSetLeveragePayload(category.getValue(), symbol, leverageString, leverageString);
    RateLimiter rateLimiter = null;
    switch (category) {
      case INVERSE:
        rateLimiter = rateLimiter(POSITION_SET_LEVERAGE_INVERSE_RATE_LIMITER);
        break;
      case LINEAR:
        rateLimiter = rateLimiter(POSITION_SET_LEVERAGE_LINEAR_RATE_LIMITER);
        break;
      default:
        throw new UnsupportedOperationException("Only Linear and Inverse category");
    }
    BybitResult<Object> setLeverageResult =
        decorateApiCall(
                () ->
                    bybitAuthenticated.setLeverage(
                        apiKey, signatureCreator, exchange.getTimeStampFactory(), payload))
            .withRateLimiter(rateLimiter)
            .withRateLimiter(rateLimiter(GLOBAL_RATE_LIMITER))
            .call();
    // retCode=110043, retMsg=leverage not modified - also is success
    if (!setLeverageResult.isSuccess() && setLeverageResult.getRetCode() != 110043) {
      throw createBybitExceptionFromResult(setLeverageResult);
    }
    return setLeverageResult;
  }

  BybitResult<Object> switchPositionModeRaw(
      BybitCategory category, String symbol, String coin, int mode) throws IOException {
    BybitSwitchModePayload payload =
        new BybitSwitchModePayload(category.getValue(), symbol, coin, mode);
    BybitResult<Object> switchModeResult =
        bybitAuthenticated.switchMode(
            apiKey, signatureCreator, exchange.getTimeStampFactory(), payload);
    // retCode=110025, retMsg=Position mode is not modified - also is success
    if (!switchModeResult.isSuccess() && switchModeResult.getRetCode() != 110025) {
      throw createBybitExceptionFromResult(switchModeResult);
    }
    return switchModeResult;
  }

  BybitResult<BybitAccountInfoResponse> accountInfoRaw() throws IOException {
    BybitResult<BybitAccountInfoResponse> accountInfo =
        bybitAuthenticated.getAccountInfo(apiKey, signatureCreator, exchange.getTimeStampFactory());
    if (!accountInfo.isSuccess()) {
      throw createBybitExceptionFromResult(accountInfo);
    }
    return accountInfo;
  }

  BybitResult<BybitCategorizedPayload<BybitTransactionLog>> getTransactionLog(
      BybitAccountType accountType,
      BybitCategory category,
      String symbol,
      String baseCoin,
      String type,
      String startTime,
      String endTime,
      String limit,
      String cursor)
      throws IOException {
    BybitResult<BybitCategorizedPayload<BybitTransactionLog>> transactionLog =
        bybitAuthenticated.getTransactionLog(
            apiKey,
            signatureCreator,
            exchange.getTimeStampFactory(),
            accountType.name(),
            category == null ? null : category.getValue(),
            symbol,
            baseCoin,
            type,
            startTime,
            endTime,
            limit,
            cursor);
    if (!transactionLog.isSuccess()) {
      throw createBybitExceptionFromResult(transactionLog);
    }
    return transactionLog;
  }

  BybitResult<BybitTransferResponse> interTransfer(
      String transferId, String coin, String amount, String fromAccountType, String toAccountType)
      throws IOException {
    BybitTransferPayload payload =
        BybitTransferPayload.builder()
            .transferId(transferId)
            .coin(coin)
            .amount(amount)
            .fromAccountType(fromAccountType)
            .toAccountType(toAccountType)
            .build();
    BybitResult<BybitTransferResponse> transfer =
        bybitAuthenticated.interTransfer(
            apiKey, signatureCreator, exchange.getTimeStampFactory(), payload);
    if (!transfer.isSuccess()) {
      throw createBybitExceptionFromResult(transfer);
    }
    return transfer;
  }

  BybitResult<BybitCollateralInfos> getCollateralInfo(String currency) throws IOException {
    BybitResult<BybitCollateralInfos> collateralInfo =
        bybitAuthenticated.getCollateralInfo(
            apiKey, signatureCreator, exchange.getTimeStampFactory(), currency);
    if (!collateralInfo.isSuccess()) {
      throw createBybitExceptionFromResult(collateralInfo);
    }
    return collateralInfo;
  }

  BybitResult<BybitCategorizedPayload<BybitBorrowHistory>> getBorrowHistory(
      String currency, String startTime, String endTime, String limit, String cursor)
      throws IOException {
    BybitResult<BybitCategorizedPayload<BybitBorrowHistory>> borrowHistory =
        bybitAuthenticated.getBorrowHistory(
            apiKey,
            signatureCreator,
            exchange.getTimeStampFactory(),
            currency,
            startTime,
            endTime,
            limit,
            cursor);
    if (!borrowHistory.isSuccess()) {
      throw createBybitExceptionFromResult(borrowHistory);
    }
    return borrowHistory;
  }

  BybitResult<BybitBorrowableAmount> getBorrowableAmount(String currency) throws IOException {
    BybitResult<BybitBorrowableAmount> borrowableAmount =
        bybitAuthenticated.getBorrowableAmount(
            apiKey, signatureCreator, exchange.getTimeStampFactory(), currency);
    if (!borrowableAmount.isSuccess()) {
      throw createBybitExceptionFromResult(borrowableAmount);
    }
    return borrowableAmount;
  }

  BybitResult<BybitCoinInfos> getCoinInfo(String coin) throws IOException {
    BybitResult<BybitCoinInfos> coinInfo =
        bybitAuthenticated.getCoinInfo(
            apiKey, signatureCreator, exchange.getTimeStampFactory(), coin);
    if (!coinInfo.isSuccess()) {
      throw createBybitExceptionFromResult(coinInfo);
    }
    return coinInfo;
  }
}
