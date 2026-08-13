package org.knowm.xchange.bybit.service;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import jakarta.ws.rs.Path;
import java.io.IOException;
import java.lang.reflect.Method;
import org.junit.Test;
import org.knowm.xchange.bybit.BybitAuthenticated;
import org.knowm.xchange.bybit.dto.BybitCategory;
import org.knowm.xchange.bybit.dto.BybitResult;
import org.knowm.xchange.bybit.dto.BybitCategorizedPayload;
import org.knowm.xchange.bybit.dto.account.BybitDeliveryRecord;
import org.knowm.xchange.bybit.dto.marketdata.BybitDeliveryPrice;

/**
 * Option delivery identity surface ({@code /v5/market/delivery-price} and {@code
 * /v5/asset/delivery-record}) with lossless string numerics and explicit unsupported-case guard
 * for the deprecated OTC RFQ trading protocol.
 */
public class BybitOptionDeliveryExtTest extends BaseWiremockTest {

  @Test
  public void deliveryPriceMapsExactDecimalsAndCursor() throws IOException {
    initGetStub(
        "/v5/market/delivery-price",
        "/deliveryPrice.json5",
        "category",
        equalTo("option"));

    BybitMarketDataServiceRaw raw =
        (BybitMarketDataServiceRaw) createExchange().getMarketDataService();
    BybitResult<BybitCategorizedPayload<BybitDeliveryPrice>> result =
        raw.getDeliveryPrice(BybitCategory.OPTION, "BTC-28JUN26-60000-C", "BTC", 50, null);

    assertTrue(result.isSuccess());
    assertEquals(BybitCategory.OPTION, result.getResult().getCategory());
    assertEquals("next_page_delivery", result.getResult().getNextPageCursor());
    java.util.List<BybitDeliveryPrice> list = result.getResult().getList();
    assertEquals(2, list.size());
    BybitDeliveryPrice btc = list.get(0);
    assertEquals("BTC-28JUN26-60000-C", btc.getSymbol());
    assertEquals("58914.5", btc.getDeliveryPrice());
    assertEquals("1751040000000", btc.getDeliveryTime());
    BybitDeliveryPrice eth = list.get(1);
    assertEquals("ETH-28JUN26-3000-C", eth.getSymbol());
    assertEquals("0.0000001", eth.getDeliveryPrice()); // exact string preserved
  }

  @Test
  public void deliveryRecordMapsSettlementIdentityAndCursor() throws IOException {
    initGetStub(
        "/v5/asset/delivery-record",
        "/deliveryRecord.json5",
        "category",
        equalTo("option"));
    initGetStub(
        "/v5/asset/delivery-record",
        "/deliveryRecord.json5",
        "expDate",
        equalTo("28JUN26"));

    BybitAccountServiceRaw raw = (BybitAccountServiceRaw) createExchange().getAccountService();
    BybitResult<BybitCategorizedPayload<BybitDeliveryRecord>> result =
        raw.getDeliveryRecord(
            BybitCategory.OPTION, null, null, null, "28JUN26", null, null);

    assertTrue(result.isSuccess());
    assertEquals(BybitCategory.OPTION, result.getResult().getCategory());
    assertEquals("delivery_page_2", result.getResult().getNextPageCursor());
    java.util.List<BybitDeliveryRecord> list = result.getResult().getList();
    assertEquals(2, list.size());
    BybitDeliveryRecord btc = list.get(0);
    assertEquals("BTC-28JUN26-60000-C", btc.getSymbol());
    assertEquals("Buy", btc.getSide());
    assertEquals("0.1", btc.getPosition());
    assertEquals("58914.5", btc.getDeliveryPrice());
    assertEquals("60000", btc.getStrike());
    assertEquals("0.0000001", btc.getFee());
    assertEquals("12.3456789", btc.getDeliveryRpl());
    BybitDeliveryRecord eth = list.get(1);
    assertEquals("Sell", eth.getSide());
    assertEquals("-0.0000001", eth.getDeliveryRpl());
  }

  /**
   * Explicit unsupported case: OTC RFQ order/quote trading is not part of the current V5 API
   * surface (the {@code /v5/otc/rfq/*} pages were removed from the official docs; the V5 "otc"
   * namespace now holds margin-loan helpers only). Guard so a future RFQ addition must revisit
   * this decision instead of silently changing the raw surface.
   */
  @Test
  public void otcRfqTradingExplicitlyUnsupported() {
    for (Method method : BybitAuthenticated.class.getDeclaredMethods()) {
      Path path = method.getAnnotation(Path.class);
      assertFalse(
          "BybitAuthenticated must not expose V5 OTC RFQ trading (deprecated): " + method,
          path != null && path.value().toLowerCase().contains("rfq"));
    }
  }
}
