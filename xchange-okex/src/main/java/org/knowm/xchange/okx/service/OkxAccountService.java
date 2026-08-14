package org.knowm.xchange.okx.service;

import static org.knowm.xchange.okx.OkxAdapters.adaptInstrument;
import static org.knowm.xchange.okx.OkxAdapters.adaptTradeMode;
import static org.knowm.xchange.okx.OkxAdapters.adaptTradingFee;
import static org.knowm.xchange.okx.dto.OkxInstType.SPOT;
import static org.knowm.xchange.okx.dto.OkxInstType.SWAP;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.knowm.xchange.client.ResilienceRegistries;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.derivative.FuturesContract;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.dto.account.Fee;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.okx.OkxAdapters;
import org.knowm.xchange.okx.OkxExchange;
import org.knowm.xchange.okx.dto.OkxException;
import org.knowm.xchange.okx.dto.OkxInstType;
import org.knowm.xchange.okx.dto.OkxResponse;
import org.knowm.xchange.okx.dto.account.OkxAccountPositionRisk;
import org.knowm.xchange.okx.dto.account.OkxAssetBalance;
import org.knowm.xchange.okx.dto.account.OkxTradeFee;
import org.knowm.xchange.okx.dto.account.OkxWalletBalance;
import org.knowm.xchange.okx.dto.account.OkxWithdrawalResponse;
import org.knowm.xchange.service.account.AccountService;
import org.knowm.xchange.service.trade.params.DefaultWithdrawFundsParams;
import org.knowm.xchange.service.trade.params.WithdrawFundsParams;

/** Author: Max Gao (gaamox@tutanota.com) Created: 08-06-2021 */
public class OkxAccountService extends OkxAccountServiceRaw implements AccountService {

  public OkxAccountService(OkxExchange exchange, ResilienceRegistries resilienceRegistries) {
    super(exchange, resilienceRegistries);
  }

  public AccountInfo getAccountInfo() throws IOException {
    // null to get assets (with non-zero balance), remaining balance, and available amount in the
    // account.
    OkxResponse<List<OkxWalletBalance>> tradingBalances = getWalletBalances(null);
    OkxResponse<List<OkxAssetBalance>> assetBalances = getAssetBalances(null);
    OkxResponse<List<OkxAccountPositionRisk>> positionRis = getAccountPositionRisk();
    return new AccountInfo(
        OkxAdapters.adaptOkxBalances(tradingBalances.getData()),
        OkxAdapters.adaptOkxAssetBalances(assetBalances.getData()),
        OkxAdapters.adaptOkxAccountPositionRisk(positionRis.getData()));
  }

  @Override
  public String withdrawFunds(WithdrawFundsParams params) throws IOException {
    if (params instanceof DefaultWithdrawFundsParams) {
      DefaultWithdrawFundsParams defaultParams = (DefaultWithdrawFundsParams) params;
      String address =
          defaultParams.getAddressTag() != null
              ? defaultParams.getAddress() + ":" + defaultParams.getAddressTag()
              : defaultParams.getAddress();
      OkxResponse<List<OkxWithdrawalResponse>> okxResponse =
          assetWithdrawal(
              defaultParams.getCurrency().getCurrencyCode(),
              defaultParams.getAmount().toPlainString(),
              ON_CHAIN_METHOD,
              address,
              defaultParams.getCommission() != null
                  ? defaultParams.getCommission().toPlainString()
                  : null,
              null,
              null);
      if (!okxResponse.isSuccess()) {
        throw OkxException.fromResponse(okxResponse, apiKey, secretKey, passphrase);
      }

      return okxResponse.getData().get(0).getWithdrawalId();
    }
    throw new IllegalStateException("Don't know how to withdraw: " + params);
  }

  /**
   * @param category Optional, instrument category ("SPOT" or "SWAP"). If not specified, return all
   *     instruments trading fees.
   */
  @Override
  public Map<Instrument, Fee> getDynamicTradingFeesByInstrument(String... category)
      throws IOException {
    Map<Instrument, Fee> result = new HashMap<>();
    if (category != null && category.length > 0 && category[0] != null) {
      if (OkxInstType.SPOT.name().equals(category[0])) {
        return getTradeFeesSPOT();
      } else if (OkxInstType.SWAP.name().equals(category[0])) {
        return getTradeFeesSWAP();
      }
    } else {
      result.putAll(getTradeFeesSPOT());
      result.putAll(getTradeFeesSWAP());
    }
    return result;
  }

  @Override
  public boolean setLeverage(Instrument instrument, int leverage) throws IOException {
    return setLeverage(
            adaptInstrument(instrument),
            "",
            String.valueOf(leverage),
            adaptTradeMode(instrument, exchange.accountLevel),
            "")
        .isSuccess();
  }

  private Map<Instrument, Fee> getTradeFeesSPOT() throws IOException {
    Map<Instrument, Fee> result = new HashMap<>();
    OkxTradeFee okxTradeFee = getTradeFee(SPOT.name(), null, null, null).getData().get(0);
    for (Instrument instrument : exchange.getExchangeMetaData().getInstruments().keySet()) {
      if (instrument instanceof CurrencyPair) {
        result.put(instrument, adaptTradingFee(okxTradeFee, SPOT, instrument));
      }
    }
    return result;
  }

  private Map<Instrument, Fee> getTradeFeesSWAP() throws IOException {
    Map<Instrument, Fee> result = new HashMap<>();
    OkxTradeFee okxTradeFee = getTradeFee(SWAP.name(), null, null, null).getData().get(0);
    for (Instrument instrument : exchange.getExchangeMetaData().getInstruments().keySet()) {
      if (instrument instanceof FuturesContract && ((FuturesContract) instrument).isPerpetual()) {
        result.put(instrument, adaptTradingFee(okxTradeFee, SWAP, instrument));
      }
    }
    return result;
  }
}
