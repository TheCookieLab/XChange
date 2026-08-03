package org.knowm.xchange.polymarket;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.account.OpenPositions;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.dto.trade.OpenOrders;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.exceptions.ExchangeSecurityException;
import org.knowm.xchange.exceptions.InstrumentNotValidException;
import org.knowm.xchange.exceptions.NotAvailableFromExchangeException;
import org.knowm.xchange.polymarket.client.PolymarketTestCredentials;
import org.knowm.xchange.polymarket.dto.account.PolymarketApiCredentials;
import org.knowm.xchange.polymarket.service.PolymarketTradeService;
import org.knowm.xchange.prediction.PredictionMarketContract;
import org.knowm.xchange.service.trade.params.DefaultCancelOrderParamId;

/**
 * Wire-level tests for {@link PolymarketTradeService}. L1/L2 auth headers and the EIP-712 order
 * signature are verified against the captured requests; side/amount semantics live in {@link
 * PolymarketAdaptersTest}.
 */
class PolymarketTradeServiceTest {

  private static final String CONDITION_ID = "0xdd22472e";
  private static final String TOKEN_ID = "713210456792522125";
  private static final PredictionMarketContract CONTRACT =
      new PredictionMarketContract("polymarket", null, CONDITION_ID, TOKEN_ID, Currency.USD);

  private WireMockServer server;
  private PolymarketTradeService service;

  @BeforeEach
  void setUp() {
    server = new WireMockServer(options().dynamicPort());
    server.start();
    service = (PolymarketTradeService) exchange(true).getTradeService();
  }

  @AfterEach
  void tearDown() {
    server.stop();
  }

  @Test
  void placeLimitOrderSerializesAndSignsEndToEnd() throws Exception {
    server.stubFor(
        post(urlEqualTo("/order"))
            .withRequestBody(matchingJsonPath("$.order.tokenId", equalTo(TOKEN_ID)))
            .withRequestBody(matchingJsonPath("$.order.makerAmount", equalTo("5600000")))
            .withRequestBody(matchingJsonPath("$.order.takerAmount", equalTo("10000000")))
            .withRequestBody(matchingJsonPath("$.order.side", equalTo("BUY")))
            .withRequestBody(matchingJsonPath("$.order.signatureType", equalTo("0")))
            .withRequestBody(matchingJsonPath("$.orderType", equalTo("GTC")))
            .withRequestBody(
                matchingJsonPath("$.owner", equalTo(PolymarketTestCredentials.API_KEY)))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"success\":true,\"errorMsg\":\"\",\"orderID\":\"0xabc123\","
                            + "\"status\":\"live\",\"makingAmount\":\"5600000\","
                            + "\"takingAmount\":\"10000000\",\"transactionsHashes\":[],"
                            + "\"tradeIDs\":[]}")));

    LimitOrder order =
        new LimitOrder.Builder(OrderType.BID, CONTRACT)
            .originalAmount(new BigDecimal("10"))
            .limitPrice(new BigDecimal("0.56"))
            .build();
    assertEquals("0xabc123", service.placeLimitOrder(order));

    assertEquals(1, server.getAllServeEvents().size());
    LoggedRequest request = server.getAllServeEvents().get(0).getRequest();
    PolymarketTestCredentials.assertL2Signature(request, "POST");
    PolymarketTestCredentials.assertOrderSignature(request.getBodyAsString());
  }

  @Test
  void providerRejectionSurfacesErrorMessage() {
    server.stubFor(
        post(urlEqualTo("/order"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"success\":false,\"errorMsg\":\"not enough balance\"}")));

    LimitOrder order =
        new LimitOrder.Builder(OrderType.BID, CONTRACT)
            .originalAmount(new BigDecimal("10"))
            .limitPrice(new BigDecimal("0.56"))
            .build();
    ExchangeException e =
        assertThrows(ExchangeException.class, () -> service.placeLimitOrder(order));
    assertTrue(e.getMessage().contains("not enough balance"));
  }

  @Test
  void placementWithoutPrivateKeyFailsBeforeAnyHttpCall() {
    PolymarketTradeService keyless =
        (PolymarketTradeService) exchange(false).getTradeService();
    LimitOrder order =
        new LimitOrder.Builder(OrderType.BID, CONTRACT)
            .originalAmount(new BigDecimal("10"))
            .limitPrice(new BigDecimal("0.56"))
            .build();
    assertThrows(ExchangeSecurityException.class, () -> keyless.placeLimitOrder(order));
    assertEquals(0, server.getAllServeEvents().size());
  }

  @Test
  void placeMarketOrderIsRejectedWithoutHttpCall() {
    MarketOrder order =
        new MarketOrder.Builder(OrderType.BID, CONTRACT).originalAmount(BigDecimal.ONE).build();
    assertThrows(NotAvailableFromExchangeException.class, () -> service.placeMarketOrder(order));
    assertEquals(0, server.getAllServeEvents().size());
  }

  @Test
  void currencyPairPlacementIsRejectedWithoutHttpCall() {
    LimitOrder order =
        new LimitOrder.Builder(OrderType.BID, CurrencyPair.BTC_USD)
            .originalAmount(BigDecimal.ONE)
            .limitPrice(new BigDecimal("0.5"))
            .build();
    assertThrows(InstrumentNotValidException.class, () -> service.placeLimitOrder(order));
    assertEquals(0, server.getAllServeEvents().size());
  }

  @Test
  void deriveApiCredentialsProvesL1Ownership() throws Exception {
    server.stubFor(
        get(urlPathEqualTo("/auth/derive-api-key"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"apiKey\":\"k\",\"secret\":\"s\",\"passphrase\":\"p\"}")));

    PolymarketApiCredentials credentials = service.deriveApiCredentials();
    assertEquals("k", credentials.apiKey());

    assertEquals(1, server.getAllServeEvents().size());
    PolymarketTestCredentials.assertL1Signature(
        server.getAllServeEvents().get(0).getRequest());
  }

  @Test
  void cancelOrderPostsOrderIdAndReadsCanceledList() throws Exception {
    server.stubFor(
        delete(urlEqualTo("/order"))
            .withRequestBody(matchingJsonPath("$.orderID", equalTo("ord-1")))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"canceled\":[\"ord-1\"],\"not_canceled\":{}}")));

    assertTrue(service.cancelOrder(new DefaultCancelOrderParamId("ord-1")));
    PolymarketTestCredentials.assertL2Signature(
        server.getAllServeEvents().get(0).getRequest(), "DELETE");
  }

  @Test
  void getOpenOrdersKeepsOnlyLiveOrders() throws Exception {
    server.stubFor(
        get(urlPathEqualTo("/data/orders"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "[{\"id\":\"ord-1\",\"status\":\"live\",\"owner\":\"o\","
                            + "\"maker_address\":\"0x11\",\"market\":\""
                            + CONDITION_ID
                            + "\",\"asset_id\":\""
                            + TOKEN_ID
                            + "\",\"outcome\":\"Yes\",\"side\":\"BUY\",\"original_size\":\"10\","
                            + "\"size_matched\":\"0\",\"price\":\"0.56\",\"expiration\":\"0\","
                            + "\"order_type\":\"GTC\",\"created_at\":\"1754230000\"},"
                            + "{\"id\":\"ord-2\",\"status\":\"matched\",\"owner\":\"o\","
                            + "\"maker_address\":\"0x11\",\"market\":\""
                            + CONDITION_ID
                            + "\",\"asset_id\":\""
                            + TOKEN_ID
                            + "\",\"outcome\":\"Yes\",\"side\":\"BUY\",\"original_size\":\"5\","
                            + "\"size_matched\":\"5\",\"price\":\"0.50\",\"expiration\":\"0\","
                            + "\"order_type\":\"GTC\",\"created_at\":\"1754230000\"}]")));

    OpenOrders openOrders = service.getOpenOrders();
    assertEquals(1, openOrders.getOpenOrders().size());
    LimitOrder order = openOrders.getOpenOrders().get(0);
    assertEquals("ord-1", order.getId());
    assertEquals(OrderType.BID, order.getType());
    assertEquals(new BigDecimal("0.56"), order.getLimitPrice());
    PolymarketTestCredentials.assertL2Signature(
        server.getAllServeEvents().get(0).getRequest(), "GET");
  }

  @Test
  void getTradeHistoryAdaptsUserFills() throws Exception {
    server.stubFor(
        get(urlPathEqualTo("/trades"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "[{\"id\":\"fill-1\",\"taker_order_id\":\"ord-1\",\"market\":\""
                            + CONDITION_ID
                            + "\",\"asset_id\":\""
                            + TOKEN_ID
                            + "\",\"outcome\":\"Yes\",\"side\":\"SELL\",\"size\":\"3\","
                            + "\"price\":\"0.56\",\"status\":\"MATCHED\","
                            + "\"match_time\":\"1754230000\",\"trader_side\":\"TAKER\","
                            + "\"owner\":\"o\"}]")));

    var history = service.getTradeHistory(null);
    assertEquals(1, history.getUserTrades().size());
    assertEquals("fill-1", history.getUserTrades().get(0).getId());
    assertEquals(OrderType.ASK, history.getUserTrades().get(0).getType());
    assertEquals(new BigDecimal("0.56"), history.getUserTrades().get(0).getPrice());
  }

  @Test
  void getOpenPositionsReadsThePublicDataApi() throws Exception {
    server.stubFor(
        get(urlPathEqualTo("/positions"))
            .withQueryParam(
                "user", equalTo(PolymarketTestCredentials.WALLET_ADDRESS))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "[{\"proxyWallet\":\""
                            + PolymarketTestCredentials.WALLET_ADDRESS
                            + "\",\"asset\":\""
                            + TOKEN_ID
                            + "\",\"conditionId\":\""
                            + CONDITION_ID
                            + "\",\"size\":5,\"avgPrice\":0.4,\"curPrice\":0.55,"
                            + "\"currentValue\":2.75,\"outcome\":\"Yes\",\"outcomeIndex\":0,"
                            + "\"oppositeAsset\":\"999\",\"eventId\":\"123\",\"title\":\"t\","
                            + "\"negativeRisk\":false,\"redeemable\":false}]")));

    OpenPositions positions = service.getOpenPositions();
    assertEquals(1, positions.getOpenPositions().size());
    assertEquals(CONDITION_ID,
        ((PredictionMarketContract) positions.getOpenPositions().get(0).getInstrument())
            .getMarketId());
  }

  private org.knowm.xchange.Exchange exchange(boolean withPrivateKey) {
    PolymarketExchange exchange = new PolymarketExchange();
    ExchangeSpecification spec = exchange.getDefaultExchangeSpecification();
    spec.setSslUri(server.baseUrl());
    spec.setUserName(PolymarketTestCredentials.WALLET_ADDRESS);
    spec.setApiKey(PolymarketTestCredentials.API_KEY);
    spec.setSecretKey(PolymarketTestCredentials.L2_SECRET_BASE64);
    spec.setPassword(PolymarketTestCredentials.PASSPHRASE);
    spec.setExchangeSpecificParametersItem(
        PolymarketExchange.PARAM_GAMMA_URI, server.baseUrl());
    spec.setExchangeSpecificParametersItem(
        PolymarketExchange.PARAM_DATA_URI, server.baseUrl());
    if (withPrivateKey) {
      spec.setExchangeSpecificParametersItem(
          PolymarketExchange.PARAM_PRIVATE_KEY, PolymarketTestCredentials.PRIVATE_KEY_HEX);
    }
    spec.setShouldLoadRemoteMetaData(false);
    exchange.applySpecification(spec);
    return exchange;
  }
}
