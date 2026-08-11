package org.knowm.xchange.bitget.uta.v3.service;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.knowm.xchange.bitget.uta.v3.BitgetUtaV3ExchangeWiremock;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.dto.account.Balance;
import org.knowm.xchange.dto.account.Wallet;
import org.knowm.xchange.service.account.AccountService;

class BitgetUtaV3AccountServiceTest extends BitgetUtaV3ExchangeWiremock {

  private final AccountService accountService = exchange.getAccountService();

  @Test
  void account_info_maps_unified_balances() throws Exception {
    wireMockServer.stubFor(
        get(urlPathEqualTo("/api/v3/account/assets"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"code\":\"00000\",\"msg\":\"success\",\"requestTime\":1725040472073,"
                            + "\"data\":[{\"coin\":\"USDT\",\"available\":\"900\",\"locked\":\"0\","
                            + "\"frozen\":\"50\",\"margin\":\"50\",\"debts\":\"0\",\"bonus\":\"0\","
                            + "\"equity\":\"1000\",\"usdValue\":\"1000\",\"unrealizedPnl\":\"50\"},"
                            + "{\"coin\":\"BTC\",\"available\":\"0.5\",\"locked\":\"0\",\"frozen\":\"0\","
                            + "\"margin\":\"0\",\"debts\":\"0\",\"bonus\":\"0\",\"equity\":\"0.5\","
                            + "\"usdValue\":\"30000\",\"unrealizedPnl\":\"0\"}]}")));

    AccountInfo accountInfo = accountService.getAccountInfo();
    Wallet wallet = accountInfo.getWallet();

    assertThat(wallet).isNotNull();
    assertThat(wallet.getFeatures())
        .containsExactlyInAnyOrder(
            Wallet.WalletFeature.TRADING,
            Wallet.WalletFeature.MARGIN_TRADING,
            Wallet.WalletFeature.FUTURES_TRADING);
    assertThat(wallet.getBalances()).hasSize(2);

    Balance usdt = wallet.getBalance(Currency.USDT);
    assertThat(usdt.getTotal()).isEqualByComparingTo("1000");
    assertThat(usdt.getAvailable()).isEqualByComparingTo("900");
    assertThat(usdt.getFrozen()).isEqualByComparingTo("50");
    assertThat(usdt.getBorrowed()).isEqualByComparingTo("0");

    Balance btc = wallet.getBalance(Currency.BTC);
    assertThat(btc.getTotal()).isEqualByComparingTo("0.5");
    assertThat(btc.getAvailable()).isEqualByComparingTo("0.5");
  }
}
