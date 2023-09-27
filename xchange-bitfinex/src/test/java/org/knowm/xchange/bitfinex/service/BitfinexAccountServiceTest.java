package org.knowm.xchange.bitfinex.service;

import java.io.IOException;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.bitfinex.BitfinexExchangeWiremock;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.service.account.AccountService;

@Disabled
class BitfinexAccountServiceTest extends BitfinexExchangeWiremock {

  AccountService accountService = exchange.getAccountService();


  @Test
  void account_info() throws IOException {
    AccountInfo accountInfo = accountService.getAccountInfo();

    System.out.println(accountInfo);
  }

}