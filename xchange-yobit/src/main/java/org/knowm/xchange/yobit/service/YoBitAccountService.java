package org.knowm.xchange.yobit.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.dto.account.FundingRecord;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.exceptions.NotAvailableFromExchangeException;
import org.knowm.xchange.service.trade.params.DefaultWithdrawFundsParams;
import org.knowm.xchange.service.trade.params.TradeHistoryParams;
import org.knowm.xchange.service.trade.params.WithdrawFundsParams;
import org.knowm.xchange.yobit.YoBitExchange;
import org.knowm.xchange.yobit.dto.BaseYoBitResponse;

public class YoBitAccountService extends YoBitAccountServiceRaw {
  public YoBitAccountService(YoBitExchange exchange) {
    super(exchange);
  }

  @Override
  public AccountInfo getAccountInfo() throws IOException {
    return getInfo();
  }

  @Override
  public String withdrawFunds(Currency currency, BigDecimal amount, String address)
      throws IOException {
    return withdrawFunds(new DefaultWithdrawFundsParams(address, currency, amount));
  }

  @Override
  public String withdrawFunds(WithdrawFundsParams params) throws IOException {
    if (params instanceof DefaultWithdrawFundsParams) {
      BaseYoBitResponse response = withdrawCoinsToAddress((DefaultWithdrawFundsParams) params);

      return response.returnData.toString();
    }

    throw new IllegalStateException("Don't understand " + params);
  }

  /**
   * Requests the existing deposit address by default. Pass {@code "true"} as the first extra
   * argument to request a new address, or {@code "false"} to request the existing one.
   */
  @Override
  public String requestDepositAddress(Currency currency, String... args) throws IOException {
    boolean needNew = false;
    if (args.length > 0) {
      if (!"true".equalsIgnoreCase(args[0]) && !"false".equalsIgnoreCase(args[0])) {
        throw new IllegalArgumentException("needNew must be true or false");
      }
      needNew = Boolean.parseBoolean(args[0]);
    }
    BaseYoBitResponse response = getDepositAddress(currency, needNew);

    if (!response.success) throw new ExchangeException("failed to get deposit address");

    return response.returnData.get("address").toString();
  }

  @Override
  public List<FundingRecord> getFundingHistory(TradeHistoryParams params) throws IOException {
    throw new NotAvailableFromExchangeException();
  }
}
