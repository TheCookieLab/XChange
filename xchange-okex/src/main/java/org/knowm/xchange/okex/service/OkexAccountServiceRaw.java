package org.knowm.xchange.okex.service;

import java.io.IOException;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.knowm.xchange.client.ResilienceRegistries;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.okex.OkexExchange;
import org.knowm.xchange.okex.dto.OkexException;
import org.knowm.xchange.okex.dto.OkexResponse;
import org.knowm.xchange.okex.dto.account.OkexAccountConfig;
import org.knowm.xchange.okex.dto.account.OkexAccountPositionRisk;
import org.knowm.xchange.okex.dto.account.OkexAssetBalance;
import org.knowm.xchange.okex.dto.account.OkexBillDetails;
import org.knowm.xchange.okex.dto.account.OkexChangeMarginResponse;
import org.knowm.xchange.okex.dto.account.OkexDepositAddress;
import org.knowm.xchange.okex.dto.account.OkexSetLeverageResponse;
import org.knowm.xchange.okex.dto.account.OkexTradeFee;
import org.knowm.xchange.okex.dto.account.OkexWalletBalance;
import org.knowm.xchange.okex.dto.account.OkexWithdrawalResponse;
import org.knowm.xchange.okex.dto.account.PiggyBalance;
import org.knowm.xchange.okex.dto.subaccount.OkexSubAccountDetails;
import org.knowm.xchange.okx.dto.OkxException;
import org.knowm.xchange.okx.dto.OkxResponse;
import org.knowm.xchange.okx.service.OkxAccountServiceRaw;
import org.knowm.xchange.okx.service.OkxBaseService;

/**
 * @deprecated use {@link org.knowm.xchange.okx.service.OkxAccountServiceRaw} instead.
 */
@Deprecated
public class OkexAccountServiceRaw extends OkxBaseService {

  public static final String INTERNAL_METHOD = "3";
  public static final String ON_CHAIN_METHOD = "4";

  private final OkxAccountServiceRaw delegate;

  public OkexAccountServiceRaw(OkexExchange exchange, ResilienceRegistries resilienceRegistries) {
    super(exchange, resilienceRegistries);
    this.delegate = new OkxAccountServiceRaw(exchange, resilienceRegistries);
  }

  private static <S, T> OkexResponse<List<T>> wrap(
      OkxResponse<List<S>> response, Function<S, T> mapper) {
    return new OkexResponse<>(
        new OkxResponse<>(
            response.getId(),
            response.getCode(),
            response.getMsg(),
            response.getData().stream().map(mapper).collect(Collectors.toList())));
  }

  public OkexResponse<List<OkexAssetBalance>> getAssetBalances(List<Currency> currencies)
      throws OkexException, IOException {
    try {
      return wrap(delegate.getAssetBalances(currencies), OkexAssetBalance::new);
    } catch (OkxException e) {
      throw new OkexException(e);
    }
  }

  public OkexResponse<List<OkexWalletBalance>> getWalletBalances(List<Currency> currencies)
      throws OkexException, IOException {
    try {
      return wrap(delegate.getWalletBalances(currencies), OkexWalletBalance::new);
    } catch (OkxException e) {
      throw new OkexException(e);
    }
  }

  public OkexResponse<List<OkexAccountPositionRisk>> getAccountPositionRisk()
      throws OkexException, IOException {
    try {
      return wrap(delegate.getAccountPositionRisk(), OkexAccountPositionRisk::new);
    } catch (OkxException e) {
      throw new OkexException(e);
    }
  }

  public OkexResponse<List<OkexWithdrawalResponse>> assetWithdrawal(
      String currency,
      String amount,
      String method,
      String address,
      String fee,
      String chain,
      String clientId)
      throws OkexException, IOException {
    try {
      return wrap(
          delegate.assetWithdrawal(currency, amount, method, address, fee, chain, clientId),
          OkexWithdrawalResponse::new);
    } catch (OkxException e) {
      throw new OkexException(e);
    }
  }

  public OkexResponse<List<OkexSetLeverageResponse>> setLeverage(
      String instrumentId, String currency, String leverage, String marginMode, String positionSide)
      throws OkexException, IOException {
    try {
      return wrap(
          delegate.setLeverage(instrumentId, currency, leverage, marginMode, positionSide),
          OkexSetLeverageResponse::new);
    } catch (OkxException e) {
      throw new OkexException(e);
    }
  }

  public OkexResponse<List<OkexDepositAddress>> getDepositAddress(String currency)
      throws OkexException, IOException {
    try {
      return wrap(delegate.getDepositAddress(currency), OkexDepositAddress::new);
    } catch (OkxException e) {
      throw new OkexException(e);
    }
  }

  public OkexResponse<List<OkexTradeFee>> getTradeFee(
      String instrumentType, String instrumentId, String underlying, String instFamily)
      throws IOException, OkexException {
    try {
      return wrap(
          delegate.getTradeFee(instrumentType, instrumentId, underlying, instFamily),
          OkexTradeFee::new);
    } catch (OkxException e) {
      throw new OkexException(e);
    }
  }

  public OkexResponse<List<OkexAccountConfig>> getOkexAccountConfiguration()
      throws OkexException, IOException {
    try {
      return wrap(delegate.getOkxAccountConfiguration(), OkexAccountConfig::new);
    } catch (OkxException e) {
      throw new OkexException(e);
    }
  }

  public OkexResponse<List<OkexBillDetails>> getBills(
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
      throws OkexException, IOException {
    try {
      return wrap(
          delegate.getBills(
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
              maxNumberOfResults),
          OkexBillDetails::new);
    } catch (OkxException e) {
      throw new OkexException(e);
    }
  }

  public OkexResponse<List<OkexChangeMarginResponse>> changeMargin(
      String instrumentId,
      String positionSide,
      String type,
      String amount,
      String currency,
      boolean auto,
      boolean loadTrans)
      throws OkexException, IOException {
    try {
      return wrap(
          delegate.changeMargin(instrumentId, positionSide, type, amount, currency, auto, loadTrans),
          OkexChangeMarginResponse::new);
    } catch (OkxException e) {
      throw new OkexException(e);
    }
  }

  public OkexResponse<List<OkexSubAccountDetails>> getSubAccounts(Boolean enable, String subAcct)
      throws IOException {
    return wrap(delegate.getSubAccounts(enable, subAcct), OkexSubAccountDetails::new);
  }

  public OkexResponse<List<OkexWalletBalance>> getSubAccountBalance(String subAcct)
      throws IOException {
    return wrap(delegate.getSubAccountBalance(subAcct), OkexWalletBalance::new);
  }

  public OkexResponse<List<PiggyBalance>> getPiggyBalance(String ccy) throws IOException {
    return wrap(delegate.getPiggyBalance(ccy), PiggyBalance::new);
  }
}
