package org.knowm.xchange.polymarket;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.WireMockServer;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.polymarket.client.PolymarketTestCredentials;
import org.knowm.xchange.polymarket.service.PolymarketAccountService;

/**
 * Wire-level test for {@link PolymarketAccountService}: micro-pUSD becomes a pUSD wallet.
 */
class PolymarketAccountServiceTest {

  private WireMockServer server;
  private PolymarketAccountService service;

  @BeforeEach
  void setUp() {
    server = new WireMockServer(options().dynamicPort());
    server.start();
    PolymarketExchange exchange = new PolymarketExchange();
    ExchangeSpecification spec = exchange.getDefaultExchangeSpecification();
    spec.setSslUri(server.baseUrl());
    spec.setUserName(PolymarketTestCredentials.WALLET_ADDRESS);
    spec.setApiKey(PolymarketTestCredentials.API_KEY);
    spec.setSecretKey(PolymarketTestCredentials.L2_SECRET_BASE64);
    spec.setPassword(PolymarketTestCredentials.PASSPHRASE);
    spec.setShouldLoadRemoteMetaData(false);
    exchange.applySpecification(spec);
    service = (PolymarketAccountService) exchange.getAccountService();
  }

  @AfterEach
  void tearDown() {
    server.stop();
  }

  @Test
  void accountInfoConvertsTheCollateralBalance() throws Exception {
    server.stubFor(
        get(urlPathEqualTo("/balance-allowance"))
            .withQueryParam("asset_type", equalTo("COLLATERAL"))
            .withQueryParam("signature_type", equalTo("0"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"balance\":\"1234567\",\"allowances\":{}}")));

    AccountInfo accountInfo = service.getAccountInfo();
    assertEquals(
        new BigDecimal("1.234567"),
        accountInfo.getWallet().getBalance(Currency.PUSD).getAvailable());
    assertTrue(
        !accountInfo.getWallet().getBalances().containsKey(Currency.USD),
        "collateral is pUSD, not USD");

    assertEquals(1, server.getAllServeEvents().size());
    PolymarketTestCredentials.assertL2Signature(
        server.getAllServeEvents().get(0).getRequest(), "GET");
  }
}
