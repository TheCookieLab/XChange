package org.knowm.xchange.okx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.knowm.xchange.okx.dto.OkxInstType.SPOT;
import static org.knowm.xchange.okx.dto.OkxInstType.SWAP;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.derivative.FuturesContract;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.account.Fee;
import org.knowm.xchange.dto.meta.ExchangeMetaData;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.okx.dto.OkxResponse;
import org.knowm.xchange.okx.dto.account.OkxTradeFee;
import org.knowm.xchange.okx.dto.trade.OkxOrderRequest;

public class OkxAdapterTest {
  @Test
  public void testAdaptTradingFee() throws IOException {
    ObjectMapper mapper = new ObjectMapper();
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    InputStream is = OkxAdapterTest.class.getResourceAsStream("/getFeeRatesSpot.json5");
    OkxTradeFee okxSpotTradeFee =
        mapper
            .readValue(is, new TypeReference<OkxResponse<List<OkxTradeFee>>>() {})
            .getData()
            .get(0);
    assertThat(OkxAdapters.adaptTradingFee(okxSpotTradeFee, SPOT, new CurrencyPair("USDT/SGD")))
        .isEqualTo(new Fee(new BigDecimal("0.0005"), new BigDecimal("0.0007")));
    assertThat(OkxAdapters.adaptTradingFee(okxSpotTradeFee, SPOT, new CurrencyPair("OKB/BTC")))
        .isEqualTo(new Fee(new BigDecimal("0.0005"), new BigDecimal("0.0007")));
    assertThat(OkxAdapters.adaptTradingFee(okxSpotTradeFee, SPOT, new CurrencyPair("USDC/USDT")))
        .isEqualTo(new Fee(new BigDecimal("0.0008"), new BigDecimal("0.001")));
    assertThat(OkxAdapters.adaptTradingFee(okxSpotTradeFee, SPOT, new CurrencyPair("EUR/USDT")))
        .isEqualTo(new Fee(new BigDecimal("0.0008"), new BigDecimal("0.001")));

    is = OkxAdapterTest.class.getResourceAsStream("/getFeeRatesSwap.json5");
    OkxTradeFee okxSwapTradeFee =
        mapper
            .readValue(is, new TypeReference<OkxResponse<List<OkxTradeFee>>>() {})
            .getData()
            .get(0);
    assertThat(
            OkxAdapters.adaptTradingFee(
                okxSwapTradeFee, SWAP, new FuturesContract("BTC/USDT/SWAP")))
        .isEqualTo(new Fee(new BigDecimal("0.0002"), new BigDecimal("0.0005")));
    assertThat(
            OkxAdapters.adaptTradingFee(
                okxSwapTradeFee, SWAP, new FuturesContract("BTC/USDT/SWAP")))
        .isEqualTo(new Fee(new BigDecimal("0.0002"), new BigDecimal("0.0005")));
    assertThat(
            OkxAdapters.adaptTradingFee(
                okxSwapTradeFee, SWAP, new FuturesContract("USDC/USDT/SWAP")))
        .isEqualTo(new Fee(new BigDecimal("0.0002"), new BigDecimal("0.0005")));
    // currently no USD support in OKX swap
    //    assertThat(OkxAdapters.adaptTradingFee(okxSwapTradeFee,SWAP, new
    // FuturesContract("BTC/USD/SWAP")))
    //        .isEqualTo(new Fee(new BigDecimal("-0.0002"),new BigDecimal("-0.0005")));
  }

  @Test
  public void testAdaptOrderResolvesUnifiedUsdInstrumentCode() {
    // Remote init keys the code map by the adapted wire instrument (BTC/USD since the unified USD
    // orderbook revamp), while legacy callers still trade the BTC/USDC pair; the map lookup must
    // resolve the adapted wire instrument instead of NPE-ing. The map is shared static state that
    // other tests (including a live remoteInit) may populate, so snapshot and restore it.
    Map<Instrument, Long> original = new HashMap<>(OkxAdapters.instrumentToInstrumentIdMap);
    OkxAdapters.instrumentToInstrumentIdMap.clear();
    OkxAdapters.instrumentToInstrumentIdMap.put(new CurrencyPair("BTC/USD"), 1234567890L);
    try {
      LimitOrder order =
          new LimitOrder(
              Order.OrderType.BID,
              new BigDecimal("0.1"),
              new CurrencyPair("BTC/USDC"),
              "order-1",
              new Date(),
              new BigDecimal("60000"));
      OkxOrderRequest request =
          OkxAdapters.adaptOrder(
              order,
              new ExchangeMetaData(
                  Collections.emptyMap(), Collections.emptyMap(), null, null, null),
              "1");
      assertThat(request.getInstrumentId()).isEqualTo("BTC-USD");
      assertThat(request.getInstIdCode()).isEqualTo("1234567890");
    } finally {
      OkxAdapters.instrumentToInstrumentIdMap.clear();
      OkxAdapters.instrumentToInstrumentIdMap.putAll(original);
    }
  }

  @Test
  public void testInstrumentCodePrefersAdaptedWireKeyOverDirectKey() {
    // A currency-pair caller must get the code matching the wire instId (BTC-USD), not a direct
    // BTC/USDC registration which would belong to a different instrument type; the direct key is
    // only a fallback when no adapted key exists.
    Map<Instrument, Long> original = new HashMap<>(OkxAdapters.instrumentToInstrumentIdMap);
    OkxAdapters.instrumentToInstrumentIdMap.clear();
    try {
      OkxAdapters.instrumentToInstrumentIdMap.put(new CurrencyPair("BTC/USD"), 1234567890L);
      OkxAdapters.instrumentToInstrumentIdMap.put(new CurrencyPair("BTC/USDC"), 987654321L);
      assertThat(OkxAdapters.instrumentCode(new CurrencyPair("BTC/USDC")))
          .isEqualTo(1234567890L);
      OkxAdapters.instrumentToInstrumentIdMap.remove(new CurrencyPair("BTC/USD"));
      assertThat(OkxAdapters.instrumentCode(new CurrencyPair("BTC/USDC")))
          .isEqualTo(987654321L);
      assertThat(OkxAdapters.instrumentCode(new CurrencyPair("ETH/USDT"))).isNull();
    } finally {
      OkxAdapters.instrumentToInstrumentIdMap.clear();
      OkxAdapters.instrumentToInstrumentIdMap.putAll(original);
    }
  }
}
