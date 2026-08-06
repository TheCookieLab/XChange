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
import org.knowm.xchange.polymarket.dto.gamma.PolymarketGammaMarket;
import org.knowm.xchange.polymarket.service.PolymarketTradeService;
import org.knowm.xchange.prediction.PredictionMarketContract;
import org.knowm.xchange.service.trade.params.DefaultCancelOrderParamId;

/**
 * Wire-level tests for {@link PolymarketTradeService} against the current CLOB V2 contracts:
 * paginated {@code {limit, next_cursor, count, data}} envelopes, {@code ORDER_STATUS_*} names, and
 * 6-decimal fixed-point quantities. L1/L2 auth headers and the EIP-712 order signature are
 * verified against the captured requests; side/amount semantics live in {@link
 * PolymarketAdaptersTest}.
 */
class PolymarketTradeServiceTest {

  private static final String CONDITION_ID = "0xdd22472e";
  private static final String TOKEN_ID = "713210456792522125";
  private static final PredictionMarketContract CONTRACT =
      new PredictionMarketContract("polymarket", null, CONDITION_ID, TOKEN_ID, Currency.PUSD);

  private WireMockServer server;
  private PolymarketTradeService service;

  @BeforeEach
  void setUp() {
    server = new WireMockServer(options().dynamicPort());
    server.start();
    service = (PolymarketTradeService) exchange(true).getTradeService();
    // Discovery normally records the market type; the wire tests hand-build the contract, so seed
    // the negative-risk registry the same way remoteInit() would.
    PolymarketAdapters.resetNegRiskRegistry();
    PolymarketAdapters.adaptContract(
        new PolymarketGammaMarket(
            "1", CONDITION_ID, "q?", "[\"Yes\",\"No\"]", "[\"0.5\",\"0.5\"]",
            "[\"" + TOKEN_ID + "\",\"1041735572147445375\"]", true, false, true,
            new BigDecimal("5"), new BigDecimal("0.001"), "1000", false),
        0);
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
  void placementWithoutKnownMarketTypeFailsFastBeforeAnyHttpCall() {
    PolymarketAdapters.resetNegRiskRegistry();
    LimitOrder order =
        new LimitOrder.Builder(OrderType.BID, CONTRACT)
            .originalAmount(new BigDecimal("10"))
            .limitPrice(new BigDecimal("0.56"))
            .build();
    NotAvailableFromExchangeException e =
        assertThrows(
            NotAvailableFromExchangeException.class, () -> service.placeLimitOrder(order));
    assertTrue(e.getMessage().contains("remoteInit"), "message must name the remediation");
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
  void getOpenOrdersFollowsEnvelopePaginationAndDecodesFixedMath() throws Exception {
    // Two pages, copied from the current /data/orders contract: envelope, ORDER_STATUS_* names,
    // and fixed-math quantities. The first-page order is repeated on the second page to prove
    // deduplication.
    server.stubFor(
        get(urlPathEqualTo("/data/orders"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(ordersPage("MTAw", orderJson("ord-1", "100000000", "50000000")))));
    server.stubFor(
        get(urlPathEqualTo("/data/orders"))
            .withQueryParam("next_cursor", equalTo("MTAw"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        ordersPage(
                            "",
                            orderJson("ord-1", "100000000", "50000000"),
                            orderJson("ord-2", "200000000", "0")))));

    OpenOrders openOrders = service.getOpenOrders();
    assertEquals(2, openOrders.getOpenOrders().size(), "both pages aggregated, deduped");
    LimitOrder partial = openOrders.getOpenOrders().stream()
        .filter(o -> "ord-1".equals(o.getId()))
        .findFirst()
        .orElseThrow();
    assertEquals(
        new BigDecimal("100"),
        partial.getOriginalAmount(),
        "100000000 micro-units is 100 shares");
    assertEquals(
        new BigDecimal("50"),
        partial.getCumulativeAmount(),
        "50000000 micro-units is a 50-share partial fill");
    assertEquals(new BigDecimal("0.56"), partial.getLimitPrice(), "price stays decimal dollars");
    LimitOrder resting = openOrders.getOpenOrders().stream()
        .filter(o -> "ord-2".equals(o.getId()))
        .findFirst()
        .orElseThrow();
    assertEquals(new BigDecimal("200"), resting.getOriginalAmount());

    assertEquals(2, server.getAllServeEvents().size(), "two pages were fetched");
    PolymarketTestCredentials.assertL2Signature(
        server.getAllServeEvents().get(0).getRequest(), "GET");
  }

  @Test
  void getTradeHistoryReadsTheDataTradesEnvelopeWithMakerAddress() throws Exception {
    server.stubFor(
        get(urlPathEqualTo("/data/trades"))
            .withQueryParam("maker_address", equalTo(PolymarketTestCredentials.WALLET_ADDRESS))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        tradesPage(
                            "LTE=",
                            "{\"id\":\"fill-1\",\"taker_order_id\":\"ord-1\",\"market\":\""
                                + CONDITION_ID
                                + "\",\"asset_id\":\""
                                + TOKEN_ID
                                + "\",\"outcome\":\"Yes\",\"side\":\"SELL\",\"size\":\"100000000\","
                                + "\"fee_rate_bps\":\"30\",\"price\":\"0.56\","
                                + "\"status\":\"TRADE_STATUS_CONFIRMED\","
                                + "\"match_time\":\"1754230000\",\"trader_side\":\"TAKER\","
                                + "\"owner\":\"o\",\"maker_address\":\""
                                + PolymarketTestCredentials.WALLET_ADDRESS
                                + "\",\"maker_orders\":[]}"))));

    var history = service.getTradeHistory(null);
    assertEquals(1, history.getUserTrades().size());
    var trade = history.getUserTrades().get(0);
    assertEquals("fill-1", trade.getId());
    assertEquals("ord-1", trade.getOrderId(), "taker fill is the user's taker order");
    assertEquals(OrderType.ASK, trade.getType());
    assertEquals(
        new BigDecimal("100"),
        trade.getOriginalAmount(),
        "100000000 micro-units is 100 shares");
    assertEquals(new BigDecimal("0.56"), trade.getPrice());
  }

  @Test
  void getTradeHistoryEmitsOneFillPerOwnedMakerOrder() throws Exception {
    server.stubFor(
        get(urlPathEqualTo("/data/trades"))
            .withQueryParam("maker_address", equalTo(PolymarketTestCredentials.WALLET_ADDRESS))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        tradesPage(
                            "",
                            "{\"id\":\"fill-2\",\"taker_order_id\":\"taker-ord\",\"market\":\""
                                + CONDITION_ID
                                + "\",\"asset_id\":\""
                                + TOKEN_ID
                                + "\",\"outcome\":\"Yes\",\"side\":\"SELL\",\"size\":\"60000000\","
                                + "\"price\":\"0.56\",\"status\":\"TRADE_STATUS_CONFIRMED\","
                                + "\"match_time\":\"1754230000\",\"trader_side\":\"MAKER\","
                                + "\"owner\":\"o\",\"maker_address\":\""
                                + PolymarketTestCredentials.WALLET_ADDRESS
                                + "\",\"maker_orders\":["
                                + "{\"order_id\":\"maker-ord-1\",\"owner\":\"o\",\"maker_address\":\""
                                + PolymarketTestCredentials.WALLET_ADDRESS
                                + "\",\"matched_amount\":\"40000000\",\"price\":\"0.55\","
                                + "\"fee_rate_bps\":\"0\",\"asset_id\":\""
                                + TOKEN_ID
                                + "\",\"outcome\":\"Yes\",\"side\":\"BUY\"},"
                                + "{\"order_id\":\"foreign-ord\",\"owner\":\"x\",\"maker_address\":\""
                                + "0x"
                                + "22".repeat(20)
                                + "\",\"matched_amount\":\"20000000\",\"price\":\"0.57\","
                                + "\"side\":\"BUY\"}]}"))));

    var history = service.getTradeHistory(null);
    assertEquals(
        1,
        history.getUserTrades().size(),
        "only maker_orders entries owned by the account are attributed");
    var trade = history.getUserTrades().get(0);
    assertEquals("maker-ord-1", trade.getOrderId());
    assertEquals(OrderType.BID, trade.getType(), "maker order side, not the row side");
    assertEquals(new BigDecimal("40"), trade.getOriginalAmount());
    assertEquals(new BigDecimal("0.55"), trade.getPrice(), "maker order price");
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
    assertEquals(
        CONDITION_ID,
        ((PredictionMarketContract) positions.getOpenPositions().get(0).getInstrument())
            .getMarketId());
  }

  private static String ordersPage(String nextCursor, String... orders) {
    StringBuilder data = new StringBuilder();
    for (String order : orders) {
      if (data.length() > 0) {
        data.append(',');
      }
      data.append(order);
    }
    return "{\"limit\":100,\"next_cursor\":\"" + nextCursor + "\",\"count\":" + orders.length
        + ",\"data\":[" + data + "]}";
  }

  private static String orderJson(String id, String originalSize, String sizeMatched) {
    return "{\"id\":\""
        + id
        + "\",\"status\":\"ORDER_STATUS_LIVE\",\"owner\":\"o\",\"maker_address\":\""
        + PolymarketTestCredentials.WALLET_ADDRESS
        + "\",\"market\":\""
        + CONDITION_ID
        + "\",\"asset_id\":\""
        + TOKEN_ID
        + "\",\"outcome\":\"Yes\",\"side\":\"BUY\",\"original_size\":\""
        + originalSize
        + "\",\"size_matched\":\""
        + sizeMatched
        + "\",\"price\":\"0.56\",\"expiration\":\"0\",\"order_type\":\"GTC\","
        + "\"created_at\":1754230000}";
  }

  private static String tradesPage(String nextCursor, String... trades) {
    StringBuilder data = new StringBuilder();
    for (String trade : trades) {
      if (data.length() > 0) {
        data.append(',');
      }
      data.append(trade);
    }
    return "{\"limit\":100,\"next_cursor\":\"" + nextCursor + "\",\"count\":" + trades.length
        + ",\"data\":[" + data + "]}";
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
