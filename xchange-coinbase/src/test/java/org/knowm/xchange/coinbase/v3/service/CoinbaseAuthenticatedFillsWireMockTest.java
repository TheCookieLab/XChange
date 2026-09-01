package org.knowm.xchange.coinbase.v3.service;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.client.ExchangeRestProxyBuilder;
import org.knowm.xchange.coinbase.v3.CoinbaseAuthenticated;
import org.knowm.xchange.coinbase.v3.CoinbaseExchange;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseOrdersResponse;
import org.knowm.xchange.coinbase.v3.dto.pricebook.CoinbaseBestBidAsksResponse;
import si.mazi.rescu.ParamsDigest;

/**
 * Exercises Coinbase's authenticated fills proxy against a local HTTP endpoint.
 *
 * <p>The endpoint assertions intentionally inspect the received URL rather than mocked proxy
 * arguments. This protects the wire contract for both the complete and legacy overloads.
 */
public class CoinbaseAuthenticatedFillsWireMockTest {

  private static final String FILLS_PATH = "/api/v3/brokerage/orders/historical/fills";
  private static final String BEST_BID_ASK_PATH = "/api/v3/brokerage/best_bid_ask";
  private static final String CURRENT_MARGIN_WINDOW_PATH =
      "/api/v3/brokerage/cfm/intraday/current_margin_window";

  private WireMockServer server;
  private CoinbaseAuthenticated api;
  private ParamsDigest digest;

  @Before
  public void setUp() {
    server = new WireMockServer(options().dynamicPort());
    server.start();

    ExchangeSpecification specification = new ExchangeSpecification(CoinbaseExchange.class);
    specification.setSslUri(server.baseUrl());
    specification.setHost("localhost");
    api = ExchangeRestProxyBuilder.forInterface(CoinbaseAuthenticated.class, specification).build();
    digest = invocation -> "Bearer deterministic-test-token";

    server.stubFor(
        get(urlPathEqualTo(FILLS_PATH))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"fills\":[],\"cursor\":\"\"}")));
    server.stubFor(
        get(urlPathEqualTo(BEST_BID_ASK_PATH))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"pricebooks\":[]}")));
    server.stubFor(
        get(urlPathEqualTo(CURRENT_MARGIN_WINDOW_PATH))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"margin_window\":{\"margin_window_type\":"
                            + "\"MARGIN_WINDOW_TYPE_INTRADAY\","
                            + "\"end_time\":\"2026-08-31T20:00:00Z\"}}")));
  }

  @After
  public void tearDown() {
    server.stop();
  }

  @Test
  public void completeListFillsSerializesAllFiltersAndCollectionsOnTheWire() throws Exception {
    CoinbaseOrdersResponse response = api.listFills(
        digest,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        Arrays.asList("ETH-USD", "BTC-USD"),
        Arrays.asList("MARKET_MARKET_IOC", "LIMIT_LIMIT_FOK"),
        "SELL",
        Arrays.asList("FUTURE", "SPOT"));

    assertNotNull(response);
    assertEquals(1, server.getAllServeEvents().size());
    LoggedRequest request = server.getAllServeEvents().get(0).getRequest();
    assertEquals(
        FILLS_PATH
            + "?asset_filters=ETH-USD%2CBTC-USD"
            + "&order_types=MARKET_MARKET_IOC%2CLIMIT_LIMIT_FOK"
            + "&order_side=SELL"
            + "&product_types=FUTURE%2CSPOT",
        request.getUrl());
  }

  @Test
  public void deprecatedListFillsDoesNotSerializeCompleteFilterParameters() throws Exception {
    CoinbaseOrdersResponse response =
        invokeLegacyApi(
            "listFills",
            CoinbaseOrdersResponse.class,
            new Class<?>[] {
              ParamsDigest.class,
              List.class,
              List.class,
              List.class,
              String.class,
              String.class,
              String.class,
              Integer.class,
              String.class,
              String.class
            },
            digest,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);

    assertNotNull(response);
    assertEquals(1, server.getAllServeEvents().size());
    LoggedRequest request = server.getAllServeEvents().get(0).getRequest();
    assertFalse(request.queryParameter("asset_filters").isPresent());
    assertFalse(request.queryParameter("order_types").isPresent());
    assertFalse(request.queryParameter("order_side").isPresent());
    assertFalse(request.queryParameter("product_types").isPresent());
  }

  @Test
  public void currentMarginWindowSerializesOptionalProfileSelector() throws Exception {
    assertNotNull(
        api.getCurrentMarginWindow(digest, "MARGIN_PROFILE_TYPE_RETAIL_REGULATED"));

    assertEquals(1, server.getAllServeEvents().size());
    LoggedRequest selected = server.getAllServeEvents().get(0).getRequest();
    assertEquals(
        CURRENT_MARGIN_WINDOW_PATH
            + "?margin_profile_type=MARGIN_PROFILE_TYPE_RETAIL_REGULATED",
        selected.getUrl());

    server.resetRequests();
    assertNotNull(api.getCurrentMarginWindow(digest));
    assertEquals(CURRENT_MARGIN_WINDOW_PATH, server.getAllServeEvents().get(0).getRequest().getUrl());
  }

  @Test
  public void deprecatedBestBidAskRetainsItsProxyBinding() throws Exception {
    assertNotNull(
        invokeLegacyApi(
            "getBestBidAsk",
            CoinbaseBestBidAsksResponse.class,
            new Class<?>[] {ParamsDigest.class, String.class},
            digest,
            "ETP-20DEC30-CDE"));

    assertEquals(1, server.getAllServeEvents().size());
    LoggedRequest request = server.getAllServeEvents().get(0).getRequest();
    assertEquals(
        BEST_BID_ASK_PATH + "?product_ids=ETP-20DEC30-CDE",
        request.getUrl());
  }

  private <T> T invokeLegacyApi(
      String methodName, Class<T> returnType, Class<?>[] parameterTypes, Object... arguments)
      throws ReflectiveOperationException {
    Method method = CoinbaseAuthenticated.class.getMethod(methodName, parameterTypes);
    return returnType.cast(method.invoke(api, arguments));
  }

}
