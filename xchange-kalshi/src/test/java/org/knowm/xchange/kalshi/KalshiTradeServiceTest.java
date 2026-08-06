package org.knowm.xchange.kalshi;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;
import java.util.Base64;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.dto.trade.OpenOrders;
import org.knowm.xchange.exceptions.NotAvailableFromExchangeException;
import org.knowm.xchange.kalshi.dto.trade.KalshiOrderFlags;
import org.knowm.xchange.kalshi.service.KalshiTradeService;
import org.knowm.xchange.prediction.PredictionMarketContract;
import org.knowm.xchange.service.trade.params.DefaultCancelOrderParamId;

/**
 * Wire-level tests for {@link KalshiTradeService}. Placement serialization and auth-header
 * signing are asserted end to end; side/price semantics themselves live in {@link
 * KalshiAdaptersTest}. Fixtures use the provider-current fixed-point schema ({@code *_dollars}
 * and {@code *_fp} strings, canonical {@code book_side}) and exercise cursor-following
 * pagination, de-duplication, and additive unknown-field tolerance.
 */
class KalshiTradeServiceTest {

  private static final String CREATE_PATH = "/trade-api/v2/portfolio/events/orders";
  private static final String ORDERS_PATH = "/trade-api/v2/portfolio/orders";
  private static final String FILLS_PATH = "/trade-api/v2/portfolio/fills";
  private static final String POSITIONS_PATH = "/trade-api/v2/portfolio/positions";
  private static final PredictionMarketContract CONTRACT =
      new PredictionMarketContract(
          "kalshi", "KXBTC-25DEC31", "KXBTC-25DEC31-T90000", "YES", Currency.USD);

  private WireMockServer server;
  private KalshiTradeService service;

  @BeforeEach
  void setUp() {
    server = new WireMockServer(options().dynamicPort());
    server.start();
    KalshiExchange exchange = new KalshiExchange();
    ExchangeSpecification spec = exchange.getDefaultExchangeSpecification();
    spec.setSslUri(server.baseUrl());
    spec.setApiKey("test-key-id");
    spec.setSecretKey(KalshiTestKeys.privateKeyPem());
    spec.setShouldLoadRemoteMetaData(false);
    exchange.applySpecification(spec);
    service = (KalshiTradeService) exchange.getTradeService();
  }

  @AfterEach
  void tearDown() {
    server.stop();
  }

  @Test
  void placeLimitOrderSerializesV2RequestAndSignsHeaders() throws Exception {
    server.stubFor(
        post(urlEqualTo(CREATE_PATH))
            .withRequestBody(matchingJsonPath("$.ticker", equalTo("KXBTC-25DEC31-T90000")))
            .withRequestBody(matchingJsonPath("$.side", equalTo("bid")))
            .withRequestBody(matchingJsonPath("$.price", equalTo("0.56")))
            .withRequestBody(matchingJsonPath("$.count", equalTo("10")))
            .withRequestBody(
                matchingJsonPath("$.time_in_force", equalTo("good_till_canceled")))
            .withRequestBody(
                matchingJsonPath("$.self_trade_prevention_type", equalTo("taker_at_cross")))
            .withRequestBody(matchingJsonPath("$.client_order_id", equalTo("ref-1")))
            .willReturn(
                aResponse()
                    .withStatus(201)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"order_id\":\"ord-1\",\"client_order_id\":\"ref-1\","
                            + "\"fill_count\":\"0.00\",\"remaining_count\":\"10.00\","
                            + "\"average_fill_price\":null,\"ts_ms\":1754230000000}")));

    LimitOrder order =
        new LimitOrder.Builder(OrderType.BID, CONTRACT)
            .originalAmount(new BigDecimal("10"))
            .limitPrice(new BigDecimal("0.56"))
            .userReference("ref-1")
            .build();
    String orderId = service.placeLimitOrder(order);
    assertEquals("ord-1", orderId);

    assertEquals(1, server.getAllServeEvents().size());
    LoggedRequest request = server.getAllServeEvents().get(0).getRequest();
    assertEquals("test-key-id", request.getHeader("KALSHI-ACCESS-KEY"));
    assertSignatureValid(request, "POST");
  }

  @Test
  void placeMarketOrderIsRejectedWithoutHttpCall() {
    MarketOrder order =
        new MarketOrder.Builder(OrderType.BID, CONTRACT).originalAmount(BigDecimal.ONE).build();
    assertThrows(NotAvailableFromExchangeException.class, () -> service.placeMarketOrder(order));
    assertEquals(0, server.getAllServeEvents().size());
  }

  /** Rule guard: {@link KalshiAdapters#RULE_SIDE_NO_REJECTED} must fire before any HTTP call. */
  @Test
  void sideNoPlacementIsRejectedWithoutHttpCall() {
    LimitOrder order =
        new LimitOrder.Builder(OrderType.BID, CONTRACT)
            .originalAmount(BigDecimal.ONE)
            .limitPrice(new BigDecimal("0.5"))
            .flag(KalshiOrderFlags.SIDE_NO)
            .build();
    assertThrows(NotAvailableFromExchangeException.class, () -> service.placeLimitOrder(order));
    assertEquals(0, server.getAllServeEvents().size());
  }

  @Test
  void cancelOrderReturnsTrueOnCanceledStatus() throws Exception {
    server.stubFor(
        delete(urlEqualTo(ORDERS_PATH + "/ord-1"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"order\":{\"order_id\":\"ord-1\",\"ticker\":\"KXBTC-25DEC31-T90000\","
                            + "\"book_side\":\"bid\",\"status\":\"canceled\","
                            + "\"yes_price_dollars\":\"0.5300\",\"initial_count_fp\":\"10.00\","
                            + "\"fill_count_fp\":\"0.00\",\"remaining_count_fp\":\"0.00\","
                            + "\"created_time\":\"2026-01-01T00:00:00Z\"}}")));

    assertTrue(service.cancelOrder(new DefaultCancelOrderParamId("ord-1")));
    LoggedRequest request = server.getAllServeEvents().get(0).getRequest();
    assertSignatureValid(request, "DELETE");
  }

  @Test
  void getOpenOrdersAdaptsRestingOrders() throws Exception {
    server.stubFor(
        get(urlPathEqualTo(ORDERS_PATH))
            .withQueryParam("status", equalTo("resting"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"orders\":["
                            + orderJson("ord-1", "bid", "0.5300", "10.00", "0.00", "10.00", "ref-1")
                            + "],\"cursor\":\"\"}")));

    OpenOrders openOrders = service.getOpenOrders();
    assertEquals(1, openOrders.getOpenOrders().size());
    LimitOrder order = openOrders.getOpenOrders().get(0);
    assertEquals(OrderType.BID, order.getType());
    assertThat(order.getLimitPrice()).isEqualByComparingTo("0.5300");
    assertEquals("ord-1", order.getId());
  }

  /** Generic reads must follow cursors to exhaustion and de-duplicate across page boundaries. */
  @Test
  void getOpenOrdersFollowsCursorPagesAndDeduplicates() throws Exception {
    // Page 1: no cursor query parameter; carries a next-page cursor. ord-2 reappears on page 2.
    server.stubFor(
        get(urlPathEqualTo(ORDERS_PATH))
            .withQueryParam("status", equalTo("resting"))
            .withQueryParam("cursor", absent())
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"orders\":["
                            + orderJson("ord-1", "bid", "0.5300", "10.00", "0.00", "10.00", "ref-1")
                            + ","
                            + orderJson("ord-2", "ask", "0.4400", "4.50", "1.50", "3.00", "ref-2")
                            + "],\"cursor\":\"page-2\"}")));
    // Page 2: mutually exclusive with page 1 via the cursor query parameter.
    server.stubFor(
        get(urlPathEqualTo(ORDERS_PATH))
            .withQueryParam("status", equalTo("resting"))
            .withQueryParam("cursor", equalTo("page-2"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"orders\":["
                            + orderJson("ord-2", "ask", "0.4400", "4.50", "1.50", "3.00", "ref-2")
                            + ","
                            + orderJson("ord-3", "bid", "0.4217", "2.00", "0.00", "2.00", "ref-3")
                            + "],\"cursor\":\"\"}")));

    OpenOrders openOrders = service.getOpenOrders();
    assertEquals(3, openOrders.getOpenOrders().size());
    assertEquals("ord-1", openOrders.getOpenOrders().get(0).getId());
    assertEquals("ord-2", openOrders.getOpenOrders().get(1).getId());
    assertThat(openOrders.getOpenOrders().get(1).getCumulativeAmount())
        .isEqualByComparingTo("1.50");
    assertEquals("ord-3", openOrders.getOpenOrders().get(2).getId());
    assertEquals(2, server.getAllServeEvents().size());
  }

  @Test
  void getTradeHistoryAdaptsFills() throws Exception {
    server.stubFor(
        get(urlPathEqualTo(FILLS_PATH))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"fills\":["
                            + fillJson("fill-1", "ord-1", "bid", "3.00", "0.5300")
                            + "],\"cursor\":\"\"}")));

    var history = service.getTradeHistory(null);
    assertEquals(1, history.getUserTrades().size());
    assertEquals("fill-1", history.getUserTrades().get(0).getId());
    assertThat(history.getUserTrades().get(0).getPrice()).isEqualByComparingTo("0.5300");
  }

  @Test
  void getTradeHistoryFollowsCursorPages() throws Exception {
    server.stubFor(
        get(urlPathEqualTo(FILLS_PATH))
            .withQueryParam("cursor", absent())
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"fills\":["
                            + fillJson("fill-1", "ord-1", "bid", "3.00", "0.5300")
                            + "],\"cursor\":\"page-2\"}")));
    server.stubFor(
        get(urlPathEqualTo(FILLS_PATH))
            .withQueryParam("cursor", equalTo("page-2"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"fills\":["
                            + fillJson("fill-2", "ord-2", "ask", "2.50", "0.4217")
                            + "],\"cursor\":\"\"}")));

    var history = service.getTradeHistory(null);
    assertEquals(2, history.getUserTrades().size());
    assertEquals("fill-1", history.getUserTrades().get(0).getId());
    assertEquals("fill-2", history.getUserTrades().get(1).getId());
    assertThat(history.getUserTrades().get(1).getPrice()).isEqualByComparingTo("0.4217");
    assertEquals(2, server.getAllServeEvents().size());
  }

  @Test
  void getOpenPositionsFollowsCursorPagesAndDeduplicates() throws Exception {
    server.stubFor(
        get(urlPathEqualTo(POSITIONS_PATH))
            .withQueryParam("cursor", absent())
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"market_positions\":["
                            + "{\"ticker\":\"LONG-MKT\",\"position_fp\":\"5.50\","
                            + "\"market_exposure_dollars\":\"260.00\"},"
                            + "{\"ticker\":\"SHORT-MKT\",\"position_fp\":\"-3.25\","
                            + "\"market_exposure_dollars\":\"120.00\"}"
                            + "],\"cursor\":\"page-2\"}")));
    server.stubFor(
        get(urlPathEqualTo(POSITIONS_PATH))
            .withQueryParam("cursor", equalTo("page-2"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"market_positions\":["
                            + "{\"ticker\":\"SHORT-MKT\",\"position_fp\":\"-3.25\","
                            + "\"market_exposure_dollars\":\"120.00\"}"
                            + "],\"cursor\":\"\"}")));

    var positions = service.getOpenPositions().getOpenPositions();
    assertEquals(2, positions.size());
    assertEquals(
        "LONG-MKT",
        ((org.knowm.xchange.prediction.PredictionMarketContract)
                positions.get(0).getInstrument())
            .getMarketId());
    assertThat(positions.get(0).getSize()).isEqualByComparingTo("5.50");
    assertEquals(
        "SHORT-MKT",
        ((org.knowm.xchange.prediction.PredictionMarketContract)
                positions.get(1).getInstrument())
            .getMarketId());
    assertThat(positions.get(1).getSize()).isEqualByComparingTo("3.25");
    assertEquals(2, server.getAllServeEvents().size());
  }

  /** A cursor that never terminates must fail loudly, never truncate the collection. */
  @Test
  void nonTerminatingPaginationFailsLoudly() throws Exception {
    server.stubFor(
        get(urlPathEqualTo(ORDERS_PATH))
            .withQueryParam("status", equalTo("resting"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"orders\":["
                            + orderJson("ord-1", "bid", "0.5300", "10.00", "0.00", "10.00", "ref-1")
                            + "],\"cursor\":\"again\"}")));

    IllegalStateException exception =
        assertThrows(IllegalStateException.class, () -> service.getOpenOrders());
    assertTrue(exception.getMessage().contains("did not terminate"));
    assertEquals(100, server.getAllServeEvents().size());
  }

  private static String orderJson(
      String orderId,
      String bookSide,
      String yesPrice,
      String initial,
      String filled,
      String remaining,
      String clientOrderId) {
    return "{\"order_id\":\""
        + orderId
        + "\",\"client_order_id\":\""
        + clientOrderId
        + "\",\"ticker\":\"KXBTC-25DEC31-T90000\",\"book_side\":\""
        + bookSide
        + "\",\"status\":\"resting\",\"yes_price_dollars\":\""
        + yesPrice
        + "\",\"initial_count_fp\":\""
        + initial
        + "\",\"fill_count_fp\":\""
        + filled
        + "\",\"remaining_count_fp\":\""
        + remaining
        + "\",\"created_time\":\"2026-01-01T00:00:00Z\",\"future_field\":\"tolerated\"}";
  }

  private static String fillJson(
      String fillId, String orderId, String bookSide, String count, String yesPrice) {
    return "{\"fill_id\":\""
        + fillId
        + "\",\"order_id\":\""
        + orderId
        + "\",\"ticker\":\"KXBTC-25DEC31-T90000\",\"book_side\":\""
        + bookSide
        + "\",\"count_fp\":\""
        + count
        + "\",\"yes_price_dollars\":\""
        + yesPrice
        + "\",\"created_time\":\"2026-01-01T00:00:00Z\",\"future_field\":\"tolerated\"}";
  }

  private static void assertSignatureValid(LoggedRequest request, String method) throws Exception {
    String timestamp = request.getHeader("KALSHI-ACCESS-TIMESTAMP");
    String signatureHeader = request.getHeader("KALSHI-ACCESS-SIGNATURE");
    String path = request.getUrl();
    int queryStart = path.indexOf('?');
    if (queryStart >= 0) {
      path = path.substring(0, queryStart);
    }
    String payload = timestamp + method + path;

    Signature verifier = Signature.getInstance("RSASSA-PSS");
    verifier.setParameter(
        new PSSParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1));
    verifier.initVerify(KalshiTestKeys.publicKey());
    verifier.update(payload.getBytes(StandardCharsets.UTF_8));
    assertTrue(
        verifier.verify(Base64.getDecoder().decode(signatureHeader)),
        "KALSHI-ACCESS-SIGNATURE must verify against " + payload);
  }
}
