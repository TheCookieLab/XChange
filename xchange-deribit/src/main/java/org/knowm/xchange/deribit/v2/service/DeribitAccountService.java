package org.knowm.xchange.deribit.v2.service;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.deribit.v2.DeribitAdapters;
import org.knowm.xchange.deribit.v2.DeribitExchange;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.dto.account.Wallet;
import org.knowm.xchange.service.account.AccountService;

public class DeribitAccountService extends DeribitAccountServiceRaw implements AccountService {

  public DeribitAccountService(DeribitExchange exchange) {
    super(exchange);
  }

  @Override
  public AccountInfo getAccountInfo() throws IOException {
    var balances = getAccountSummaries(false).stream()
        .map(DeribitAdapters::adapt)
        .collect(Collectors.toList());
    Wallet wallet = Wallet.Builder.from(balances).id("main").build();

    var openPositions = exchange.getTradeService().getOpenPositions().getOpenPositions();

    return new AccountInfo(null, null, Collections.singleton(wallet), openPositions, null);
  }


  Collection<Currency> currencies() {
    return exchange.getExchangeMetaData().getCurrencies().keySet();
  }
}
