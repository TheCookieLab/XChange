package org.knowm.xchange.okx;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.meta.ExchangeMetaData;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.okex.OkexAdapters;
import org.knowm.xchange.okex.dto.marketdata.OkexInstrument;
import org.knowm.xchange.okex.dto.trade.OkexAmendOrderRequest;
import org.knowm.xchange.okex.dto.trade.OkexOrderDetails;
import org.knowm.xchange.okex.dto.trade.OkexOrderRequest;
import org.knowm.xchange.okx.dto.marketdata.OkxInstrument;

/**
 * Verifies the legacy {@code Okex*} order adapter facade still adapts orders and requests through
 * the deprecated DTOs. Lives in the canonical {@code okx} package because it exercises the
 * package-private instrument-map test seam in {@link OkxAdapters}.
 */
public class OkxLegacyDtoOrderAdapterTest {

  @Test
  public void okexAdaptersFacadeAdaptsOrdersAndRequestsThroughLegacyDtos() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    ExchangeMetaData meta = adaptExchangeMetaData(mapper);

    Map<Instrument, Long> original = OkxAdapters.snapshotInstrumentToInstrumentIdMapForTesting();
    OkxAdapters.replaceInstrumentToInstrumentIdMapForTesting(
        Collections.singletonMap(new CurrencyPair("BTC/USDT"), 1234567890L));
    try {
      OkexOrderDetails order =
          mapper.readValue(
              "{\"instType\":\"SPOT\",\"instId\":\"BTC-USDT\",\"side\":\"buy\",\"sz\":\"1\","
                  + "\"px\":\"100\",\"avgPx\":\"100\",\"accFillSz\":\"1\",\"state\":\"live\","
                  + "\"cTime\":\"1610000000000\",\"uTime\":\"1610000000000\",\"fee\":\"0.1\","
                  + "\"feeCcy\":\"USDT\",\"ordId\":\"ord-1\"}",
              OkexOrderDetails.class);
      LimitOrder adaptedOrder = OkexAdapters.adaptOrder(order, meta);
      assertThat(adaptedOrder.getOriginalAmount()).isEqualByComparingTo("1");
      assertThat(adaptedOrder.getInstrument()).isEqualTo(new CurrencyPair("BTC/USDT"));

      LimitOrder input =
          new LimitOrder(
              OrderType.BID,
              new BigDecimal("1"),
              new CurrencyPair("BTC", "USDT"),
              "ord-2",
              null,
              new BigDecimal("100"));
      OkexOrderRequest request = OkexAdapters.adaptOrder(input, meta, "1");
      assertThat(request.getInstrumentId()).isEqualTo("BTC-USDT");
      assertThat(request.getAmount()).isEqualTo("1");
      assertThat(request.getTradeMode()).isEqualTo("cash");

      MarketOrder marketOrder =
          new MarketOrder(OrderType.ASK, new BigDecimal("2"), new CurrencyPair("BTC", "USDT"));
      OkexOrderRequest marketRequest = OkexAdapters.adaptOrder(marketOrder, meta, "1");
      assertThat(marketRequest.getInstrumentId()).isEqualTo("BTC-USDT");
      assertThat(marketRequest.getOrderType()).isEqualTo("market");

      OkexAmendOrderRequest amend = OkexAdapters.adaptAmendOrder(input, meta);
      assertThat(amend.getInstrumentId()).isEqualTo("BTC-USDT");
    } finally {
      OkxAdapters.replaceInstrumentToInstrumentIdMapForTesting(original);
    }
  }

  private static ExchangeMetaData adaptExchangeMetaData(ObjectMapper mapper) throws Exception {
    com.fasterxml.jackson.databind.JsonNode root =
        mapper.readTree(
            OkxLegacyDtoOrderAdapterTest.class.getResourceAsStream("/instrumentsSpot.json5"));
    List<OkxInstrument> instruments =
        mapper.readValue(root.get("data").traverse(), new TypeReference<List<OkxInstrument>>() {});
    List<OkexInstrument> legacy =
        instruments.stream().map(OkexInstrument::new).collect(java.util.stream.Collectors.toList());
    return OkexAdapters.adaptToExchangeMetaData(legacy, null);
  }
}
