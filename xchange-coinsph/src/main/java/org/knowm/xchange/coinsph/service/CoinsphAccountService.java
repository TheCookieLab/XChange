package org.knowm.xchange.coinsph.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.knowm.xchange.client.ResilienceRegistries;
import org.knowm.xchange.coinsph.CoinsphAdapters;
import org.knowm.xchange.coinsph.CoinsphExchange;
import org.knowm.xchange.coinsph.dto.CoinsphException;
import org.knowm.xchange.coinsph.dto.account.CoinsphAccount;
import org.knowm.xchange.coinsph.dto.account.CoinsphDepositAddress;
import org.knowm.xchange.coinsph.dto.account.CoinsphFundingRecord;
import org.knowm.xchange.coinsph.dto.account.CoinsphTradeFee;
import org.knowm.xchange.coinsph.dto.account.CoinsphWithdrawal;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.dto.account.Fee;
import org.knowm.xchange.dto.account.FundingRecord;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.service.account.AccountService;
import org.knowm.xchange.service.account.params.RequestDepositAddressParams;
import org.knowm.xchange.service.trade.params.DefaultWithdrawFundsParams;
import org.knowm.xchange.service.trade.params.NetworkWithdrawFundsParams;
import org.knowm.xchange.service.trade.params.TradeHistoryParams;
import org.knowm.xchange.service.trade.params.WithdrawFundsParams;

public class CoinsphAccountService extends CoinsphAccountServiceRaw implements AccountService {

  public CoinsphAccountService(
      CoinsphExchange exchange, ResilienceRegistries resilienceRegistries) {
    super(exchange, resilienceRegistries);
  }

  @Override
  public AccountInfo getAccountInfo() throws IOException, CoinsphException {
    CoinsphAccount coinsphAccount = super.getCoinsphAccount();
    return CoinsphAdapters.adaptAccountInfo(
        coinsphAccount, exchange.getExchangeSpecification().getUserName());
  }

  @Override
  public String withdrawFunds(Currency currency, BigDecimal amount, String address)
      throws IOException, CoinsphException {
    // Get the default network for the currency
    String network = currency.getCurrencyCode();

    // For some currencies, the network might be different from the currency code
    // For example, USDT could be on different networks like ETH, BSC, etc.
    // This would need to be expanded based on Coins.ph's supported networks

    CoinsphWithdrawal withdrawal =
        withdraw(currency.getCurrencyCode(), network, address, amount, null);

    return withdrawal.getId();
  }

  @Override
  public String withdrawFunds(WithdrawFundsParams params) throws IOException, CoinsphException {
    if (params instanceof NetworkWithdrawFundsParams) {
      NetworkWithdrawFundsParams coinsphParams = (NetworkWithdrawFundsParams) params;
      CoinsphWithdrawal withdrawal =
          withdraw(
              coinsphParams.getCurrency().getCurrencyCode(),
              coinsphParams.getNetwork(),
              coinsphParams.getAddress(),
              coinsphParams.getAmount(),
              coinsphParams.getAddressTag());

      return withdrawal.getId();
    } else if (params instanceof DefaultWithdrawFundsParams) {
      DefaultWithdrawFundsParams defaultParams = (DefaultWithdrawFundsParams) params;
      return withdrawFunds(
          defaultParams.getCurrency(), defaultParams.getAmount(), defaultParams.getAddress());
    }

    throw new IllegalArgumentException(
        "WithdrawFundsParams must be either DefaultWithdrawFundsParams or CoinsphWithdrawFundsParams");
  }

  @Override
  public String requestDepositAddress(Currency currency, String... args)
      throws IOException, CoinsphException {
    String network = null;

    // If network is provided as an argument, use it
    if (args != null && args.length > 0) {
      network = args[0];
    }

    CoinsphDepositAddress depositAddress =
        requestDepositAddress(currency.getCurrencyCode(), network);

    return depositAddress.getAddress();
  }

  @Override
  public String requestDepositAddress(RequestDepositAddressParams params) throws IOException {
    return requestDepositAddress(params.getCurrency(), params.getNetwork());
  }

  @Override
  public TradeHistoryParams createFundingHistoryParams() {
    return new CoinsphFundingHistoryParams();
  }

  @Override
  public List<FundingRecord> getFundingHistory(TradeHistoryParams params)
      throws IOException, CoinsphException {
    CoinsphFundingHistoryParams coinsphParams;

    if (params instanceof CoinsphFundingHistoryParams) {
      coinsphParams = (CoinsphFundingHistoryParams) params;
    } else {
      coinsphParams = new CoinsphFundingHistoryParams();
      // Set default values: include both deposits and withdrawals
      coinsphParams.setIncludeDeposits(true);
      coinsphParams.setIncludeWithdrawals(true);
    }

    List<CoinsphFundingRecord> fundingRecords = getFundingHistory(coinsphParams);
    return CoinsphAdapters.adaptFundingRecords(fundingRecords);
  }

  @Override
  public Map<Instrument, Fee> getDynamicTradingFeesByInstrument()
      throws IOException, CoinsphException {
    List<CoinsphTradeFee> fees = super.getCoinsphTradeFees();
    return CoinsphAdapters.adaptTradeFees(fees);
  }
}
