package org.knowm.xchange.mexc.v3.service;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import org.junit.Test;
import org.knowm.xchange.exceptions.ExchangeSecurityException;
import org.knowm.xchange.mexc.v3.BaseMexcV3WiremockTest;
import org.knowm.xchange.mexc.v3.dto.account.MexcV3ListenKey;
import org.knowm.xchange.mexc.v3.dto.account.MexcV3ListenKeyList;

/** User-data stream lifecycle: create, list, keepalive and close (WireMock). */
public class MexcV3AccountServiceTest extends BaseMexcV3WiremockTest {

  private static final String LISTEN_KEY_PATH = "/api/v3/userDataStream";

  @Test
  public void createListenKeySendsApiKeyHeader() throws IOException {
    stubFor(
        post(urlEqualTo(LISTEN_KEY_PATH))
            .withHeader("X-MEXC-APIKEY", com.github.tomakehurst.wiremock.client.WireMock.equalTo("test_api_key"))
            .willReturn(aResponse().withBody("{\"listenKey\":\"header-checked\"}")));

    assertThat(accountService().createListenKey().getListenKey()).isEqualTo("header-checked");
  }

  @Test
  public void createListenKeyPostsAndReturnsKey() throws IOException {
    stubFor(
        post(urlEqualTo(LISTEN_KEY_PATH))
            .willReturn(aResponse().withBody("{\"listenKey\":\"abc-123\"}")));

    MexcV3ListenKey key = accountService().createListenKey();

    assertThat(key.getListenKey()).isEqualTo("abc-123");
    verify(1, postRequestedFor(urlEqualTo(LISTEN_KEY_PATH)));
  }

  @Test
  public void listListenKeysReturnsKeys() throws IOException {
    stubFor(
        get(urlEqualTo(LISTEN_KEY_PATH))
            .willReturn(aResponse().withBody("{\"listenKey\":[\"abc-123\",\"def-456\"]}")));

    MexcV3ListenKeyList keys = accountService().listListenKeys();

    assertThat(keys.getListenKey()).containsExactly("abc-123", "def-456");
  }

  @Test
  public void keepAliveListenKeyPutsKey() throws IOException {
    stubFor(
        put(urlEqualTo(LISTEN_KEY_PATH + "?listenKey=abc-123"))
            .willReturn(aResponse().withBody("{\"listenKey\":\"abc-123\"}")));

    MexcV3ListenKey key = accountService().keepAliveListenKey("abc-123");

    assertThat(key.getListenKey()).isEqualTo("abc-123");
    verify(1, putRequestedFor(urlEqualTo(LISTEN_KEY_PATH + "?listenKey=abc-123")));
  }

  @Test
  public void closeListenKeyDeletesKey() throws IOException {
    stubFor(
        delete(urlEqualTo(LISTEN_KEY_PATH + "?listenKey=abc-123"))
            .willReturn(aResponse().withBody("{\"listenKey\":\"abc-123\"}")));

    MexcV3ListenKey key = accountService().closeListenKey("abc-123");

    assertThat(key.getListenKey()).isEqualTo("abc-123");
    verify(1, deleteRequestedFor(urlEqualTo(LISTEN_KEY_PATH + "?listenKey=abc-123")));
  }

  @Test
  public void createListenKeyAdaptsProviderError() throws IOException {
    stubFor(
        post(urlEqualTo(LISTEN_KEY_PATH))
            .willReturn(
                aResponse()
                    .withStatus(401)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"code\":401,\"msg\":\"Invalid ApiKey\"}")));

    assertThatThrownBy(() -> accountService().createListenKey())
        .isInstanceOf(ExchangeSecurityException.class)
        .hasMessageContaining("Invalid ApiKey");
  }

  private MexcV3AccountService accountService() throws IOException {
    return (MexcV3AccountService) createExchange().getAccountService();
  }
}
