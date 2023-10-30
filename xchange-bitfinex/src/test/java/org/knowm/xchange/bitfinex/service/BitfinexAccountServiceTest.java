package org.knowm.xchange.bitfinex.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.bitfinex.BitfinexExchangeWiremock;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.dto.account.Balance;
import org.knowm.xchange.service.account.AccountService;

class BitfinexAccountServiceTest extends BitfinexExchangeWiremock {

  AccountService accountService = exchange.getAccountService();


  @Test
  void account_info() throws IOException {
    AccountInfo actual = accountService.getAccountInfo();

    assertThat(actual.getWallet("exchange").getBalances()).hasSize(2);

    Balance expectedUsd = Balance.Builder.from(Balance.zero(Currency.USD))
        .total(new BigDecimal("14.93278618365196"))
        .available(new BigDecimal("8.93278618365196"))
        .frozen(new BigDecimal("6.00000000000000"))
        .build();

    assertThat(actual.getWallet("exchange").getBalance(Currency.USD)).usingRecursiveComparison().isEqualTo(expectedUsd);
  }

}