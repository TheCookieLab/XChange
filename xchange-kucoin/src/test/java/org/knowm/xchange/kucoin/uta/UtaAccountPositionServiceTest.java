package org.knowm.xchange.kucoin.uta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.client.WireMock;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.kucoin.uta.dto.UtaBatchCancelRequest;
import org.knowm.xchange.kucoin.uta.dto.UtaBatchCancelResult;
import org.knowm.xchange.kucoin.uta.dto.UtaLedgerEntry;
import org.knowm.xchange.kucoin.uta.dto.UtaMarginMode;
import org.knowm.xchange.kucoin.uta.dto.UtaModifyLeverageRequest;
import org.knowm.xchange.kucoin.uta.dto.UtaModifyLeverageResult;
import org.knowm.xchange.kucoin.uta.dto.UtaPositionHistory;

/** Deterministic fixtures for the Phase 4 endpoint additions (margin, leverage, history, batch). */
class UtaAccountPositionServiceTest extends AbstractUtaResilienceTest {

  private void stubJson(String path, String body) {
    wireMockRule.stubFor(
        WireMock.get(WireMock.urlPathEqualTo(path))
            .willReturn(
                WireMock.aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(body)));
  }

  @Test
  void parsesMarginMode() throws Exception {
    stubJson(
        "/api/ua/v1/unified/position/margin-mode",
        "{\"code\":\"200000\",\"data\":{\"ts\":1780630392057,\"items\":["
            + "{\"symbol\":\"XBTUSDTM\",\"marginMode\":\"CROSS\"},"
            + "{\"symbol\":\"ETHUSDTM\",\"marginMode\":\"ISOLATED\"}]}}");

    UtaMarginMode mode =
        createUtaExchange().getUtaTradeService().getUtaMarginMode(null);
    assertEquals(2, mode.getItems().size());
    assertEquals("CROSS", mode.getItems().get(0).getMarginMode());
    assertEquals("ISOLATED", mode.getItems().get(1).getMarginMode());
  }

  @Test
  void modifiesLeverageWithBody() throws Exception {
    wireMockRule.stubFor(
        WireMock.post(WireMock.urlPathEqualTo("/api/ua/v1/unified/account/modify-leverage"))
            .willReturn(
                WireMock.aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"code\":\"200000\",\"data\":{\"code\":\"200000\",\"leverage\":\"80.00\"}}")));

    UtaModifyLeverageResult result =
        createUtaExchange()
            .getUtaAccountService()
            .modifyLeverage(
                UtaModifyLeverageRequest.builder().symbol("XBTUSDTM").leverage("80").build());

    assertEquals("80.00", result.getLeverage());
    wireMockRule.verify(
        WireMock.postRequestedFor(WireMock.urlPathEqualTo("/api/ua/v1/unified/account/modify-leverage"))
            .withRequestBody(WireMock.containing("\"symbol\":\"XBTUSDTM\""))
            .withRequestBody(WireMock.containing("\"leverage\":\"80\"")));
  }

  @Test
  void parsesPositionHistoryWithCursor() throws Exception {
    stubJson(
        "/api/ua/v1/position/history",
        "{\"code\":\"200000\",\"data\":{\"items\":["
            + "{\"closeId\":\"556\",\"symbol\":\"XBTUSDTM\",\"marginMode\":\"CROSS\","
            + "\"side\":\"LONG\",\"entryPrice\":\"66895.7\",\"closePrice\":\"62889.8\","
            + "\"avgClosePrice\":\"62889.8\",\"maxSize\":\"5\",\"leverage\":\"25\","
            + "\"realizedPnL\":\"-20.27523235\",\"fee\":\"0.3893565\",\"tax\":\"0\","
            + "\"fundingFee\":\"0.14362415\",\"closingTime\":1771940011060000000,"
            + "\"creationTime\":1771495960604000000}],\"lastId\":502}}");

    UtaPositionHistory history =
        createUtaExchange()
            .getUtaTradeService()
            .getPositionHistory("XBTUSDTM", null, null, null, 10);

    assertEquals(1, history.getItems().size());
    assertEquals(502L, history.getLastId());
    assertEquals("LONG", history.getItems().get(0).getSide());
    assertEquals(new BigDecimal("-20.27523235"), history.getItems().get(0).getRealizedPnL());
  }

  @Test
  void batchCancelPreservesPartialItemOutcomes() throws Exception {
    wireMockRule.stubFor(
        WireMock.post(WireMock.urlPathEqualTo("/api/ua/v1/unified/order/cancel-batch"))
            .willReturn(
                WireMock.aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"code\":\"200000\",\"data\":{\"tradeType\":\"SPOT\",\"items\":["
                            + "{\"code\":\"200000\",\"msg\":\"success\",\"orderId\":\"o-1\",\"clientOid\":null},"
                            + "{\"code\":\"116101\",\"msg\":\"Not found\",\"orderId\":\"o-2\",\"clientOid\":null}]}}")));

    UtaBatchCancelResult result =
        createUtaExchange()
            .getUtaTradeService()
            .batchCancel(
                UtaBatchCancelRequest.builder()
                    .tradeType("SPOT")
                    .cancelOrderList(
                        List.of(
                            UtaBatchCancelRequest.Item.builder().symbol("BTC-USDT").orderId("o-1").build(),
                            UtaBatchCancelRequest.Item.builder().symbol("BTC-USDT").orderId("o-2").build()))
                    .build());

    assertEquals(2, result.getItems().size());
    assertTrue(result.getItems().get(0).isSuccessful());
    assertFalse(result.getItems().get(1).isSuccessful());
    assertEquals("116101", result.getItems().get(1).getCode());
  }

  @Test
  void ledgerReturnsBareArrayWithCursorFields() throws Exception {
    stubJson(
        "/api/ua/v1/account/ledger",
        "{\"code\":\"200000\",\"data\":["
            + "{\"accountType\":\"SPOT\",\"id\":\"2204009408659584\",\"currency\":\"USDT\","
            + "\"amount\":\"10.00000000\",\"tax\":\"0\",\"fee\":\"0.00000000\","
            + "\"balance\":\"130.37263715\",\"businessType\":\"TRANSFER\",\"direction\":\"IN\","
            + "\"ts\":1768536375585000000,\"remark\":\"{}\"}]}");

    List<UtaLedgerEntry> ledger =
        createUtaExchange()
            .getUtaAccountService()
            .getLedger("SPOT", "USDT", "IN", null, null, null, null, 100);

    assertEquals(1, ledger.size());
    assertEquals("TRANSFER", ledger.get(0).getBusinessType());
    assertEquals(new BigDecimal("10.00000000"), ledger.get(0).getAmount());
  }
}
