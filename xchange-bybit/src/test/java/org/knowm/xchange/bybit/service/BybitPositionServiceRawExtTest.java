package org.knowm.xchange.bybit.service;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.knowm.xchange.bybit.dto.BybitCategory;
import org.knowm.xchange.bybit.dto.BybitCategorizedPayload;
import org.knowm.xchange.bybit.dto.BybitResult;
import org.knowm.xchange.bybit.dto.account.position.BybitClosedPnl;
import org.knowm.xchange.bybit.dto.account.position.BybitPosition;
import org.knowm.xchange.bybit.dto.account.position.BybitPositions;
import org.knowm.xchange.bybit.dto.account.position.BybitTradingStopPayload;
import org.knowm.xchange.dto.account.OpenPosition;

public class BybitPositionServiceRawExtTest extends BaseWiremockTest {

  @Test
  public void positionsPreserveHedgeIdentityAndExactDecimals() throws IOException {
    initGetStub("/v5/position/list", "/getPositions.json5");

    BybitTradeServiceRaw raw = (BybitTradeServiceRaw) createExchange().getTradeService();
    BybitResult<BybitPositions> result =
        raw.getPositions(BybitCategory.LINEAR, null, null, null, null, null);

    assertTrue(result.isSuccess());
    assertEquals("linear", result.getResult().getCategory());
    assertEquals("0%3A3%3A3", result.getResult().getNextPageCursor());
    List<BybitPosition> list = result.getResult().getList();
    assertEquals(3, list.size());

    // one-way subposition keeps identity and exact strings
    BybitPosition oneWay = list.get(0);
    assertEquals("0", oneWay.getPositionIdx());
    assertEquals("BTCUSDT", oneWay.getSymbol());
    assertEquals("10.01", oneWay.getSize());
    assertEquals("33125.42", oneWay.getAvgPrice());
    assertEquals("-0.123456789012", oneWay.getUnrealisedPnl());
    assertEquals("3.141592653589", oneWay.getCumRealisedPnl());
    assertEquals("10", oneWay.getLeverage());

    // hedge long subposition
    BybitPosition longLeg = list.get(1);
    assertEquals("1", longLeg.getPositionIdx());
    assertEquals("Buy", longLeg.getSide());
    assertEquals("2210.11", longLeg.getAvgPrice());

    // hedge short subposition
    BybitPosition shortLeg = list.get(2);
    assertEquals("2", shortLeg.getPositionIdx());
    assertEquals("Sell", shortLeg.getSide());
    assertEquals("100", shortLeg.getSize());
    assertEquals("152.3", shortLeg.getAvgPrice());
    assertEquals("160", shortLeg.getTakeProfit());
    assertEquals("140", shortLeg.getStopLoss());
    assertEquals("IndexPrice", shortLeg.getTpTriggerBy());

    assertEquals(new BigDecimal("-0.123456789012"), new BigDecimal(oneWay.getUnrealisedPnl()));
  }

  @Test
  public void openPositionsGenericMappingKeepsHedgeSubpositions() throws IOException {
    // LINEAR/INVERSE position queries are enumerated per settle coin from the instrument catalog.
    initGetStub(
        "/v5/market/instruments-info",
        "/getInstrumentLinear.json5",
        "category",
        equalTo("linear"));
    initGetStub(
        "/v5/market/instruments-info",
        "/getInstrumentInverse.json5",
        "category",
        equalTo("inverse"));
    initGetStub(
        "/v5/position/list", "/getPositions.json5", "category", equalTo("linear"));
    // First page has a non-empty nextPageCursor; the paginated follow-up must hit the empty page.
    initGetStub(
        "/v5/position/list",
        "/getPositionsEmpty.json5",
        Map.of("category", equalTo("linear"), "cursor", equalTo("0%3A3%3A3")));
    initGetStub(
        "/v5/position/list", "/getPositionsEmpty.json5", "category", equalTo("inverse"));
    initGetStub(
        "/v5/position/list", "/getPositionsEmpty.json5", "category", equalTo("option"));

    BybitTradeService tradeService = (BybitTradeService) createExchange().getTradeService();
    List<OpenPosition> positions = tradeService.getOpenPositions().getOpenPositions();

    assertEquals(3, positions.size());

    OpenPosition oneWay = positions.get(0);
    assertEquals("BTCUSDT:0", oneWay.getId());
    assertEquals(OpenPosition.Type.LONG, oneWay.getType());
    assertEquals(new BigDecimal("10.01"), oneWay.getSize());
    assertEquals(new BigDecimal("33125.42"), oneWay.getPrice());
    assertEquals(new BigDecimal("29797.88"), oneWay.getLiquidationPrice());
    assertEquals(new BigDecimal("-0.123456789012"), oneWay.getUnRealisedPnl());
    assertEquals(Instant.ofEpochMilli(1672304894063L), oneWay.getCreatedAt());
    assertEquals(Instant.ofEpochMilli(1672304994063L), oneWay.getUpdatedAt());

    OpenPosition hedgeLong = positions.get(1);
    assertEquals("ETHUSDT:1", hedgeLong.getId());
    assertEquals(OpenPosition.Type.LONG, hedgeLong.getType());
    assertEquals(new BigDecimal("6.105"), hedgeLong.getUnRealisedPnl());

    OpenPosition hedgeShort = positions.get(2);
    assertEquals("SOLUSDT:2", hedgeShort.getId());
    assertEquals(OpenPosition.Type.SHORT, hedgeShort.getType());
    assertEquals(new BigDecimal("-80"), hedgeShort.getUnRealisedPnl());
  }

  @Test
  public void closedPnlParsesWithExactDecimals() throws IOException {
    initGetStub("/v5/position/closed-pnl", "/getClosedPnl.json5");

    BybitTradeServiceRaw raw = (BybitTradeServiceRaw) createExchange().getTradeService();
    BybitResult<BybitCategorizedPayload<BybitClosedPnl>> result =
        raw.getClosedPnl(BybitCategory.LINEAR, null, null, null, null, null);

    assertTrue(result.isSuccess());
    assertEquals(BybitCategory.LINEAR, result.getResult().getCategory());
    assertEquals("0%3A2%3A2", result.getResult().getNextPageCursor());
    List<BybitClosedPnl> list = result.getResult().getList();
    assertEquals(2, list.size());
    BybitClosedPnl first = list.get(0);
    assertEquals("BTCUSDT", first.getSymbol());
    assertEquals("CLOSE-20260812", first.getOrderLinkId());
    assertEquals("-12.345678901234", first.getClosedPnl());
    assertEquals("33125.42", first.getAvgEntryPrice());
    assertEquals("0.5", first.getClosedSize());
    assertEquals("1672304894063", first.getCreatedTime());
    assertEquals(new BigDecimal("-12.345678901234"), new BigDecimal(first.getClosedPnl()));
    assertEquals("88.88", list.get(1).getClosedPnl());
  }

  @Test
  public void tradingStopSetsTakeProfitAndStopLoss() throws IOException {
    initPostStub("/v5/position/trading-stop", "/setTradingStop.json5");

    BybitTradeServiceRaw raw = (BybitTradeServiceRaw) createExchange().getTradeService();
    BybitResult<Object> set =
        raw.setTradingStop(
            BybitTradingStopPayload.builder()
                .category("linear")
                .symbol("BTCUSDT")
                .positionIdx("1")
                .takeProfit("36000")
                .stopLoss("30000")
                .build());
    assertTrue(set.isSuccess());
    assertNotNull(set.getResult());
  }

  @Test
  public void riskLimitMarginMutationsSucceed() throws IOException {
    initPostStub("/v5/position/set-risk-limit", "/setRiskLimit.json5");
    initPostStub("/v5/position/add-margin", "/addMargin.json5");
    initPostStub("/v5/position/set-auto-add-margin", "/setAutoAddMargin.json5");

    BybitTradeServiceRaw raw = (BybitTradeServiceRaw) createExchange().getTradeService();

    assertTrue(
        raw.setRiskLimit(
                org.knowm.xchange.bybit.dto.account.position.BybitSetRiskLimitPayload.builder()
                    .category("linear")
                    .symbol("BTCUSDT")
                    .riskId("2")
                    .positionIdx("0")
                    .build())
            .isSuccess());
    assertTrue(
        raw.addMargin(
                org.knowm.xchange.bybit.dto.account.position.BybitAddMarginPayload.builder()
                    .category("linear")
                    .symbol("BTCUSDT")
                    .margin("100")
                    .positionIdx("0")
                    .build())
            .isSuccess());
    assertTrue(
        raw.setAutoAddMargin(
                org.knowm.xchange.bybit.dto.account.position.BybitSetAutoAddMarginPayload
                    .builder()
                    .category("linear")
                    .symbol("BTCUSDT")
                    .autoAddMargin(1)
                    .positionIdx(0)
                    .build())
            .isSuccess());
  }
}
