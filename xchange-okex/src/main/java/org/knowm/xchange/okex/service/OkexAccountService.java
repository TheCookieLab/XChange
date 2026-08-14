package org.knowm.xchange.okex.service;

import java.io.IOException;
import java.util.Map;
import org.knowm.xchange.client.ResilienceRegistries;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.dto.account.Fee;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.okex.OkexExchange;
import org.knowm.xchange.okx.service.OkxAccountService;
import org.knowm.xchange.service.account.AccountService;
import org.knowm.xchange.service.trade.params.WithdrawFundsParams;

/**
 * @deprecated use {@link org.knowm.xchange.okx.service.OkxAccountService} instead.
 */
@Deprecated
public class OkexAccountService extends OkexAccountServiceRaw implements AccountService {

  private final OkxAccountService delegate;

  public OkexAccountService(OkexExchange exchange, ResilienceRegistries resilienceRegistries) {
    super(exchange, resilienceRegistries);
    this.delegate = new OkxAccountService(exchange, resilienceRegistries);
  }

  public AccountInfo getAccountInfo() throws IOException {
    return delegate.getAccountInfo();
  }

  @Override
  public String withdrawFunds(WithdrawFundsParams params) throws IOException {
    return delegate.withdrawFunds(params);
  }

  @Override
  public Map<Instrument, Fee> getDynamicTradingFeesByInstrument(String... category)
      throws IOException {
    return delegate.getDynamicTradingFeesByInstrument(category);
  }

  @Override
  public boolean setLeverage(Instrument instrument, int leverage) throws IOException {
    return delegate.setLeverage(instrument, leverage);
  }
}
