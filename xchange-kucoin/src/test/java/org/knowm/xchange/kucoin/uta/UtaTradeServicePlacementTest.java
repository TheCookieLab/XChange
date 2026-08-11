package org.knowm.xchange.kucoin.uta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.kucoin.uta.dto.UtaOrderPlaceRequest;
import org.knowm.xchange.kucoin.uta.service.UtaApiException;
import org.knowm.xchange.kucoin.uta.service.UtaApiException.RetryClassification;

/**
 * No-blind-replay placement contract: a transmitted placement whose result is unknown is reconciled
 * by client order id; the provider is never re-hit with the same placement.
 */
class UtaTradeServicePlacementTest extends AbstractUtaResilienceTest {

  private static final String PLACE_PATH = "/api/ua/v1/unified/order/place";
  private static final String DETAIL_PATH = "/api/ua/v1/unified/order/detail";

  private static UtaOrderPlaceRequest request() {
    return UtaOrderPlaceRequest.builder()
        .tradeType("SPOT")
        .symbol("BTC-USDT")
        .clientOid("ord-123")
        .side("BUY")
        .orderType("LIMIT")
        .size("0.001")
        .sizeUnit("BASECCY")
        .price("65000")
        .build();
  }

  @Test
  void successfulPlacementReturnsProviderOrderId() throws Exception {
    wireMockRule.stubFor(
        WireMock.post(WireMock.urlPathEqualTo(PLACE_PATH))
            .willReturn(
                WireMock.aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"code\":\"200000\",\"data\":{\"tradeType\":\"SPOT\","
                            + "\"orderId\":\"385543068587233280\",\"clientOid\":\"ord-123\"}}")));

    var result =
        createUtaExchange()
            .getUtaTradeService()
            .placeOrderSafe(request(), CurrencyPair.BTC_USDT);

    assertEquals("385543068587233280", result.getOrderId());
    wireMockRule.verify(1, WireMock.postRequestedFor(WireMock.urlPathEqualTo(PLACE_PATH)));
    wireMockRule.verify(0, WireMock.getRequestedFor(WireMock.urlPathEqualTo(DETAIL_PATH)));
  }

  @Test
  void transportFailureReconcilesByClientOidAndReturnsFoundOrder() throws Exception {
    wireMockRule.stubFor(
        WireMock.post(WireMock.urlPathEqualTo(PLACE_PATH))
            .willReturn(
                WireMock.aResponse()
                    .withStatus(500)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"code\":\"500\",\"msg\":\"internal\"}")));
    wireMockRule.stubFor(
        WireMock.get(WireMock.urlPathEqualTo(DETAIL_PATH))
            .withQueryParam("clientOid", WireMock.equalTo("ord-123"))
            .willReturn(
                WireMock.aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"code\":\"200000\",\"data\":{\"orderId\":\"385543068587233280\","
                            + "\"clientOid\":\"ord-123\",\"symbol\":\"BTC-USDT\",\"status\":2}}")));

    var result =
        createUtaExchange()
            .getUtaTradeService()
            .placeOrderSafe(request(), CurrencyPair.BTC_USDT);

    // Reconcile found the order: the result carries the provider order id, and the placement was
    // never re-transmitted.
    assertEquals("385543068587233280", result.getOrderId());
    wireMockRule.verify(1, WireMock.postRequestedFor(WireMock.urlPathEqualTo(PLACE_PATH)));
    wireMockRule.verify(1, WireMock.getRequestedFor(WireMock.urlPathEqualTo(DETAIL_PATH)));
  }

  @Test
  void transportFailureWithAbsentOrderThrowsUnknownOutcomeWithoutReplay() throws Exception {
    wireMockRule.stubFor(
        WireMock.post(WireMock.urlPathEqualTo(PLACE_PATH))
            .willReturn(
                WireMock.aResponse()
                    .withStatus(500)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"code\":\"500\",\"msg\":\"internal\"}")));
    wireMockRule.stubFor(
        WireMock.get(WireMock.urlPathEqualTo(DETAIL_PATH))
            .willReturn(
                WireMock.aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"code\":\"116052\",\"msg\":\"Order not found\"}")));

    UtaApiException e =
        assertThrows(
            UtaApiException.class,
            () ->
                createUtaExchange()
                    .getUtaTradeService()
                    .placeOrderSafe(request(), CurrencyPair.BTC_USDT));

    assertEquals(RetryClassification.UNKNOWN_OUTCOME, e.getRetryClassification());
    wireMockRule.verify(1, WireMock.postRequestedFor(WireMock.urlPathEqualTo(PLACE_PATH)));
  }

  @Test
  void duplicateClientOidReconcilesInsteadOfResubmitting() throws Exception {
    wireMockRule.stubFor(
        WireMock.post(WireMock.urlPathEqualTo(PLACE_PATH))
            .willReturn(
                WireMock.aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"code\":\"116151\",\"msg\":\"ClientOrderId already exists\"}")));
    wireMockRule.stubFor(
        WireMock.get(WireMock.urlPathEqualTo(DETAIL_PATH))
            .willReturn(
                WireMock.aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"code\":\"200000\",\"data\":{\"orderId\":\"existing-42\","
                            + "\"clientOid\":\"ord-123\",\"symbol\":\"BTC-USDT\",\"status\":2}}")));

    var result =
        createUtaExchange()
            .getUtaTradeService()
            .placeOrderSafe(request(), CurrencyPair.BTC_USDT);

    assertEquals("existing-42", result.getOrderId());
    wireMockRule.verify(1, WireMock.postRequestedFor(WireMock.urlPathEqualTo(PLACE_PATH)));
  }

  @Test
  void definitiveValidationErrorIsNotReconciled() throws Exception {
    wireMockRule.stubFor(
        WireMock.post(WireMock.urlPathEqualTo(PLACE_PATH))
            .willReturn(
                WireMock.aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"code\":\"400003\",\"msg\":\"Invalid API key\"}")));

    UtaApiException e =
        assertThrows(
            UtaApiException.class,
            () ->
                createUtaExchange()
                    .getUtaTradeService()
                    .placeOrderSafe(request(), CurrencyPair.BTC_USDT));

    assertEquals("400003", e.getCode());
    assertEquals(RetryClassification.NON_RETRYABLE, e.getRetryClassification());
    wireMockRule.verify(1, WireMock.postRequestedFor(WireMock.urlPathEqualTo(PLACE_PATH)));
    wireMockRule.verify(0, WireMock.getRequestedFor(WireMock.urlPathEqualTo(DETAIL_PATH)));
  }

  @Test
  void placementBodyCarriesClientOidAndProviderSymbol() throws Exception {
    wireMockRule.stubFor(
        WireMock.post(WireMock.urlPathEqualTo(PLACE_PATH))
            .willReturn(
                WireMock.aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"code\":\"200000\",\"data\":{\"tradeType\":\"SPOT\","
                            + "\"orderId\":\"o1\",\"clientOid\":\"ord-123\"}}")));

    createUtaExchange().getUtaTradeService().placeOrderSafe(request(), CurrencyPair.BTC_USDT);

    wireMockRule.verify(
        WireMock.postRequestedFor(WireMock.urlPathEqualTo(PLACE_PATH))
            .withRequestBody(
                WireMock.equalToJson(
                    "{\"tradeType\":\"SPOT\",\"clientOid\":\"ord-123\",\"symbol\":\"BTC-USDT\","
                        + "\"side\":\"BUY\",\"orderType\":\"LIMIT\",\"size\":\"0.001\","
                        + "\"sizeUnit\":\"BASECCY\",\"price\":\"65000\"}")));
  }

  @Test
  void highLevelLimitOrderUsesUserReferenceAsClientOid() throws Exception {
    wireMockRule.stubFor(
        WireMock.post(WireMock.urlPathEqualTo(PLACE_PATH))
            .willReturn(
                WireMock.aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"code\":\"200000\",\"data\":{\"tradeType\":\"SPOT\","
                            + "\"orderId\":\"o1\",\"clientOid\":\"my-ref-1\"}}")));

    LimitOrder order =
        new LimitOrder.Builder(OrderType.BID, CurrencyPair.BTC_USDT)
            .userReference("my-ref-1")
            .originalAmount(new java.math.BigDecimal("0.001"))
            .limitPrice(new java.math.BigDecimal("65000"))
            .build();

    String orderId = createUtaExchange().getUtaTradeService().placeLimitOrder(order);
    assertEquals("o1", orderId);
    wireMockRule.verify(
        WireMock.postRequestedFor(WireMock.urlPathEqualTo(PLACE_PATH))
            .withRequestBody(WireMock.containing("\"clientOid\":\"my-ref-1\"")));
  }
}
