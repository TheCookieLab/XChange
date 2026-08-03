package org.knowm.xchange.kalshi;

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
import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;
import java.util.Base64;
import java.util.List;
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
 * Wire-level tests for {@link KalshiTradeService}. Placement serialization and auth-header signing
 * are asserted end to end; side/price semantics themselves live in {@link KalshiAdaptersTest}.
 */
class KalshiTradeServiceTest {

  private static final String CREATE_PATH = "/trade-api/v2/portfolio/events/orders";
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
            .withRequestBody(matchingJsonPath("$.price", equalTo("0.5600")))
            .withRequestBody(matchingJsonPath("$.count", equalTo("10.00")))
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
        delete(urlEqualTo("/trade-api/v2/portfolio/orders/ord-1"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"order\":{\"order_id\":\"ord-1\",\"ticker\":\"KXBTC-25DEC31-T90000\","
                            + "\"action\":\"buy\",\"side\":\"yes\",\"status\":\"canceled\","
                            + "\"yes_price\":53,\"initial_count\":10,\"fill_count\":0,"
                            + "\"remaining_count\":0,\"created_time\":\"2026-01-01T00:00:00Z\"}}")));

    assertTrue(service.cancelOrder(new DefaultCancelOrderParamId("ord-1")));
    LoggedRequest request = server.getAllServeEvents().get(0).getRequest();
    assertSignatureValid(request, "DELETE");
  }

  @Test
  void getOpenOrdersAdaptsRestingOrders() throws Exception {
    server.stubFor(
        get(urlPathEqualTo("/trade-api/v2/portfolio/orders"))
            .withQueryParam("status", equalTo("resting"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"orders\":[{\"order_id\":\"ord-1\",\"client_order_id\":\"ref-1\","
                            + "\"ticker\":\"KXBTC-25DEC31-T90000\",\"action\":\"buy\","
                            + "\"side\":\"yes\",\"status\":\"resting\",\"yes_price\":53,"
                            + "\"initial_count\":10,\"fill_count\":0,\"remaining_count\":10,"
                            + "\"created_time\":\"2026-01-01T00:00:00Z\"}],\"cursor\":\"\"}")));

    OpenOrders openOrders = service.getOpenOrders();
    assertEquals(1, openOrders.getOpenOrders().size());
    LimitOrder order = openOrders.getOpenOrders().get(0);
    assertEquals(OrderType.BID, order.getType());
    assertEquals(new BigDecimal("0.53"), order.getLimitPrice());
    assertEquals("ord-1", order.getId());
  }

  @Test
  void getTradeHistoryAdaptsFills() throws Exception {
    server.stubFor(
        get(urlPathEqualTo("/trade-api/v2/portfolio/fills"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"fills\":[{\"fill_id\":\"fill-1\",\"order_id\":\"ord-1\","
                            + "\"ticker\":\"KXBTC-25DEC31-T90000\",\"action\":\"buy\","
                            + "\"side\":\"yes\",\"count\":3,\"yes_price\":53,\"no_price\":47,"
                            + "\"created_time\":\"2026-01-01T00:00:00Z\"}],\"cursor\":\"\"}")));

    var history = service.getTradeHistory(null);
    assertEquals(1, history.getUserTrades().size());
    assertEquals("fill-1", history.getUserTrades().get(0).getId());
    assertEquals(new BigDecimal("0.53"), history.getUserTrades().get(0).getPrice());
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
