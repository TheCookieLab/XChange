package org.knowm.xchange.bitget.uta.v3.service;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.bitget.uta.v3.BitgetUtaV3ExchangeWiremock;
import org.knowm.xchange.bitget.uta.v3.account.BitgetUtaV3TransferOutcomeUnknownException;
import org.knowm.xchange.bitget.uta.v3.account.BitgetUtaV3TransferRequest;
import org.knowm.xchange.bitget.uta.v3.account.BitgetUtaV3TransferResult;
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

  @Test
  void account_info_includes_locked_funds_in_frozen_balance() throws Exception {
    wireMockServer.stubFor(
        get(urlPathEqualTo("/api/v3/account/assets"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"code\":\"00000\",\"msg\":\"success\",\"requestTime\":1725040472073,"
                            + "\"data\":[{\"coin\":\"USDT\",\"available\":\"900\",\"locked\":\"30\","
                            + "\"frozen\":\"50\",\"margin\":\"50\",\"debts\":\"0\",\"bonus\":\"0\","
                            + "\"equity\":\"1000\",\"usdValue\":\"1000\",\"unrealizedPnl\":\"50\"}]}")));

    Balance usdt = accountService.getAccountInfo().getWallet().getBalance(Currency.USDT);
    // spot-order locked funds are committed to open orders; they must count as frozen, not vanish
    assertThat(usdt.getFrozen()).isEqualByComparingTo("80");
    assertThat(usdt.getAvailable()).isEqualByComparingTo("900");
    assertThat(usdt.getTotal()).isEqualByComparingTo("1000");
  }

  @Test
  void transfer_injects_client_oid_when_absent() throws Exception {
    wireMockServer.stubFor(
        post(urlPathEqualTo("/api/v3/account/transfer"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"code\":\"00000\",\"msg\":\"success\",\"requestTime\":1725040472073,"
                            + "\"data\":{\"transferId\":\"t-1\",\"clientOid\":\"injected\"}}")));

    BitgetUtaV3AccountService utaAccountService = (BitgetUtaV3AccountService) accountService;
    BitgetUtaV3TransferResult result =
        utaAccountService.transfer(
            BitgetUtaV3TransferRequest.builder()
                .fromType("uta")
                .toType("spot")
                .coin("USDT")
                .amount(new BigDecimal("10"))
                .build());

    assertThat(result.getTransferId()).isEqualTo("t-1");
    List<LoggedRequest> events =
        wireMockServer.findAll(postRequestedFor(urlPathEqualTo("/api/v3/account/transfer")));
    assertThat(events).hasSize(1);
    JsonNode body = new ObjectMapper().readTree(events.get(0).getBodyAsString());
    assertThat(body.get("clientOid").asText())
        .as("the transfer must always carry an idempotency key")
        .isNotBlank();
  }

  @Test
  void transfer_preserves_caller_supplied_client_oid() throws Exception {
    wireMockServer.stubFor(
        post(urlPathEqualTo("/api/v3/account/transfer"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"code\":\"00000\",\"msg\":\"success\",\"requestTime\":1725040472073,"
                            + "\"data\":{\"transferId\":\"t-2\",\"clientOid\":\"caller-oid\"}}")));

    BitgetUtaV3AccountService utaAccountService = (BitgetUtaV3AccountService) accountService;
    utaAccountService.transfer(
        BitgetUtaV3TransferRequest.builder()
            .fromType("uta")
            .toType("spot")
            .coin("USDT")
            .amount(new BigDecimal("10"))
            .clientOid("caller-oid")
            .build());

    List<LoggedRequest> events =
        wireMockServer.findAll(postRequestedFor(urlPathEqualTo("/api/v3/account/transfer")));
    JsonNode body = new ObjectMapper().readTree(events.get(0).getBodyAsString());
    assertThat(body.get("clientOid").asText())
        .as("a caller-supplied idempotency key must be preserved so retries stay idempotent")
        .isEqualTo("caller-oid");
  }

  @Test
  void transfer_transport_failure_surfaces_unknown_outcome() throws Exception {
    wireMockServer.stubFor(
        post(urlPathEqualTo("/api/v3/account/transfer"))
            .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

    BitgetUtaV3AccountService utaAccountService = (BitgetUtaV3AccountService) accountService;
    BitgetUtaV3TransferRequest request =
        BitgetUtaV3TransferRequest.builder()
            .fromType("uta")
            .toType("spot")
            .coin("USDT")
            .amount(new BigDecimal("10"))
            .clientOid("retry-me")
            .build();

    Throwable thrown = catchThrowable(() -> utaAccountService.transfer(request));

    assertThat(thrown)
        .isInstanceOf(BitgetUtaV3TransferOutcomeUnknownException.class)
        .hasMessageContaining("outcome is unknown")
        .hasMessageContaining("retry-me");
    assertThat(((BitgetUtaV3TransferOutcomeUnknownException) thrown).getClientOid())
        .as("the idempotency key must be recoverable for an idempotent retry")
        .isEqualTo("retry-me");
  }

  @Test
  void transfer_http_rejection_is_not_mislabeled_unknown_outcome() throws Exception {
    wireMockServer.stubFor(
        post(urlPathEqualTo("/api/v3/account/transfer"))
            .willReturn(
                aResponse()
                    .withStatus(400)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"code\":\"40002\",\"msg\":\"invalid parameter\"}")));

    BitgetUtaV3AccountService utaAccountService = (BitgetUtaV3AccountService) accountService;
    Throwable thrown =
        catchThrowable(
            () ->
                utaAccountService.transfer(
                    BitgetUtaV3TransferRequest.builder()
                        .fromType("uta")
                        .toType("spot")
                        .coin("USDT")
                        .amount(new BigDecimal("10"))
                        .build()));

    assertThat(thrown)
        .as("an HTTP-level rejection is a definitive failure, not an unknown outcome")
        .isNotInstanceOf(BitgetUtaV3TransferOutcomeUnknownException.class);
  }
}
