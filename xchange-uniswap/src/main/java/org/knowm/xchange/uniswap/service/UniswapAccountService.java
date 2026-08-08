package org.knowm.xchange.uniswap.service;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.dto.account.Fee;
import org.knowm.xchange.dto.account.Wallet;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.exceptions.NotAvailableFromExchangeException;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.service.account.AccountService;
import org.knowm.xchange.uniswap.UniswapExchange;

/**
 * Standard XChange account service over the raw balance reads: a single wallet holding the
 * configured tokens plus the native currency.
 */
public class UniswapAccountService extends UniswapAccountServiceRaw implements AccountService {

  public UniswapAccountService(UniswapExchange exchange) {
    super(exchange);
  }

  @Override
  public AccountInfo getAccountInfo() throws IOException {
    try {
      List<org.knowm.xchange.dto.account.Balance> balances = getBalances();
      Wallet wallet =
          new Wallet.Builder()
              .id(exchange.getConfig().walletAddress())
              .balances(balances)
              .features(java.util.Set.of(Wallet.WalletFeature.TRADING, Wallet.WalletFeature.FUNDING))
              .build();
      return new AccountInfo(exchange.getConfig().walletAddress(), List.of(wallet));
    } catch (IOException e) {
      throw new ExchangeException("failed to read balances: " + e.getMessage(), e);
    }
  }

  @Override
  public String withdrawFunds(org.knowm.xchange.service.trade.params.WithdrawFundsParams params) {
    throw new NotAvailableFromExchangeException("withdrawFunds");
  }

  @Override
  public String requestDepositAddress(org.knowm.xchange.currency.Currency currency, String... args) {
    return exchange.getConfig().walletAddress();
  }

  @Override
  public Map<Instrument, Fee> getDynamicTradingFeesByInstrument(String... category) {
    throw new NotAvailableFromExchangeException("getDynamicTradingFeesByInstrument");
  }

  @Override
  public boolean setLeverage(Instrument instrument, int leverage) {
    throw new NotAvailableFromExchangeException("setLeverage");
  }
}
