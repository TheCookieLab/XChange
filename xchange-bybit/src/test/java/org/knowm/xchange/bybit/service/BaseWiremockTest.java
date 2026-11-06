package org.knowm.xchange.bybit.service;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.matching.StringValuePattern;
import jakarta.ws.rs.core.Response.Status;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.bybit.BybitExchange;
import org.knowm.xchange.client.ratelimit.RateLimitPolicy;

public class BaseWiremockTest {

  @RegisterExtension
  public WireMockExtension wireMockRule =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  public BybitExchange createExchange() throws IOException {
    return createExchange(null);
  }

  public BybitExchange createExchange(RateLimitPolicy policy) throws IOException {
    BybitExchange exchange =
        ExchangeFactory.INSTANCE.createExchangeWithoutSpecification(BybitExchange.class);
    ExchangeSpecification specification = exchange.getDefaultExchangeSpecification();
    specification.setHost("localhost");
    specification.setSslUri("http://localhost:" + wireMockRule.getPort());
    specification.setPort(wireMockRule.getPort());
    specification.setApiKey("test_api_key");
    specification.setSecretKey("test_secret_key");
    specification.setShouldLoadRemoteMetaData(false);
    if (policy != null) {
      specification.getResilience().setRateLimitPolicy(policy);
    }
    exchange.applySpecification(specification);
    return exchange;
  }

  protected void initGetStub(String url, String responseBody) throws IOException {
    wireMockRule.stubFor(
        get(urlPathEqualTo(url))
            .willReturn(
                aResponse()
                    .withStatus(Status.OK.getStatusCode())
                    .withHeader("Content-Type", "application/json")
                    .withBody(IOUtils.resourceToString(responseBody, StandardCharsets.UTF_8))));
  }

  /**
   * @param baseUrl baseUrl
   * @param responseBody responseBody
   * @param queryParams queryParams
   * @param stringValuePattern stringValuePattern
   * @throws IOException IOException
   */
  protected void initGetStub(
      String baseUrl,
      String responseBody,
      String queryParams,
      StringValuePattern stringValuePattern)
      throws IOException {
    wireMockRule.stubFor(
        get(urlPathEqualTo(baseUrl))
            .withQueryParam(queryParams, stringValuePattern)
            .willReturn(
                aResponse()
                    .withStatus(Status.OK.getStatusCode())
                    .withHeader("Content-Type", "application/json")
                    .withBody(IOUtils.resourceToString(responseBody, StandardCharsets.UTF_8))));
  }

  /** Stub with multiple declared query params; request must contain all of them. */
  protected void initGetStub(
      String baseUrl, String responseBody, Map<String, StringValuePattern> queryParams)
      throws IOException {
    wireMockRule.stubFor(
        get(urlPathEqualTo(baseUrl))
            .withQueryParams(queryParams)
            .willReturn(
                aResponse()
                    .withStatus(Status.OK.getStatusCode())
                    .withHeader("Content-Type", "application/json")
                    .withBody(IOUtils.resourceToString(responseBody, StandardCharsets.UTF_8))));
  }

  protected void initPostStub(String url, String responseBody) throws IOException {
    wireMockRule.stubFor(
        post(urlPathEqualTo(url))
            .willReturn(
                aResponse()
                    .withStatus(Status.OK.getStatusCode())
                    .withHeader("Content-Type", "application/json")
                    .withBody(IOUtils.resourceToString(responseBody, StandardCharsets.UTF_8))));
  }
}
