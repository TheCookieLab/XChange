package org.knowm.xchange.okx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.knowm.xchange.okx.dto.OkxInstType.FUTURES;
import static org.knowm.xchange.okx.dto.OkxInstType.OPTION;
import static org.knowm.xchange.okx.dto.OkxInstType.SPOT;
import static org.knowm.xchange.okx.dto.OkxInstType.SWAP;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
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
import org.knowm.xchange.derivative.OptionsContract;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.account.Fee;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trades;
import org.knowm.xchange.dto.meta.ExchangeMetaData;
import org.knowm.xchange.dto.meta.InstrumentMetaData;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.okex.dto.trade.OkexAmendAlgoRequest;
import org.knowm.xchange.okex.dto.trade.OkexAttachAlgoOrder;
import org.knowm.xchange.okex.dto.trade.OkexOrderRequest;
import org.knowm.xchange.okex.service.OkexTradeServiceRaw;
import org.knowm.xchange.okx.dto.OkxResponse;
import org.knowm.xchange.okx.dto.account.OkxDepositAddress;
import org.knowm.xchange.okx.dto.account.OkxPosition;
import org.knowm.xchange.okx.dto.account.OkxTradeFee;
import org.knowm.xchange.okx.dto.marketdata.OkxOrderbook;
import org.knowm.xchange.okx.dto.marketdata.OkxPublicOrder;
import org.knowm.xchange.okx.dto.marketdata.OkxTicker;
import org.knowm.xchange.okx.dto.marketdata.OkxTrade;
import org.knowm.xchange.okx.dto.trade.OkxAlgoOrderRequest;
import org.knowm.xchange.okx.dto.trade.OkxAmendAlgoRequest;
import org.knowm.xchange.okx.dto.trade.OkxAttachAlgoOrder;
import org.knowm.xchange.okx.dto.trade.OkxOrderDetails;
import org.knowm.xchange.okx.dto.trade.OkxOrderRequest;
import org.knowm.xchange.okx.service.OkxTradeServiceRaw;
import si.mazi.rescu.ParamsDigest;

public class OkxAdapterTest {
  @Test
  public void testAdaptOptionTickerUsesBaseVolumeLikeOtherDerivatives() throws IOException {
    // OPTION tickers report vol24h in contracts and volCcy24h in base currency, like SWAP and
    // FUTURES; the spot branch would surface contract count as volume and base amount as quote
    // volume.
    ObjectMapper mapper = new ObjectMapper();
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    OkxTicker optionTicker =
        mapper.readValue(
            "{\"instType\":\"OPTION\",\"instId\":\"BTC-USD-260828-110000-C\",\"last\":\"100\","
                + "\"open24h\":\"90\",\"high24h\":\"110\",\"low24h\":\"80\",\"bidPx\":\"99\","
                + "\"askPx\":\"101\",\"bidSz\":\"1\",\"askSz\":\"2\",\"vol24h\":\"42\","
                + "\"volCcy24h\":\"4.2\",\"ts\":\"1720000000000\"}",
            OkxTicker.class);

    Ticker adapted = OkxAdapters.adaptTicker(optionTicker);

    assertThat(adapted.getInstrument()).isEqualTo(new OptionsContract("BTC/USD/260828/110000/C"));
    assertThat(adapted.getVolume()).isEqualByComparingTo("4.2");
    assertThat(adapted.getQuoteVolume()).isEqualByComparingTo("420");
  }

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
    // dated futures and options select the margin-currency rate like perpetual swaps
    assertThat(
            OkxAdapters.adaptTradingFee(
                okxSwapTradeFee, FUTURES, new FuturesContract("BTC/USD/260814")))
        .isEqualTo(new Fee(new BigDecimal("0.0002"), new BigDecimal("0.0005")));
    assertThat(
            OkxAdapters.adaptTradingFee(
                okxSwapTradeFee, OPTION, new OptionsContract("BTC/USD/260828/110000/C")))
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
      assertThat(OkxAdapters.instrumentCode(new CurrencyPair("BTC/USDC"))).isEqualTo(1234567890L);
      OkxAdapters.instrumentToInstrumentIdMap.remove(new CurrencyPair("BTC/USD"));
      assertThat(OkxAdapters.instrumentCode(new CurrencyPair("BTC/USDC"))).isEqualTo(987654321L);
      assertThat(OkxAdapters.instrumentCode(new CurrencyPair("ETH/USDT"))).isNull();
    } finally {
      OkxAdapters.instrumentToInstrumentIdMap.clear();
      OkxAdapters.instrumentToInstrumentIdMap.putAll(original);
    }
  }

  @Test
  public void testAdaptLimitOrderConvertsOptionVolumeToContractSize() {
    // Options trade in contracts: sz = volume / ctMult (0.01 in the instrument fixture), so a
    // 0.01 BTC trade is one contract, not 0.01.
    OptionsContract option = new OptionsContract("BTC/USD/260828/110000/C");
    Map<Instrument, Long> original = new HashMap<>(OkxAdapters.instrumentToInstrumentIdMap);
    OkxAdapters.instrumentToInstrumentIdMap.clear();
    try {
      OkxAdapters.instrumentToInstrumentIdMap.put(
          OkxAdapters.adaptOkxInstrumentId("BTC-USD-260828-110000-C"), 273778L);
      LimitOrder order =
          new LimitOrder(
              Order.OrderType.BID,
              new BigDecimal("0.01"),
              option,
              "order-1",
              new Date(),
              new BigDecimal("0.05"));
      OkxOrderRequest request =
          OkxAdapters.adaptOrder(
              order,
              new ExchangeMetaData(
                  Map.of(
                      option,
                      InstrumentMetaData.builder().contractValue(new BigDecimal("0.01")).build()),
                  Collections.emptyMap(),
                  null,
                  null,
                  null),
              "1");
      assertThat(request.getInstrumentId()).isEqualTo("BTC-USD-260828-110000-C");
      assertThat(request.getInstIdCode()).isEqualTo("273778");
      assertThat(request.getAmount()).isEqualTo("1");
    } finally {
      OkxAdapters.instrumentToInstrumentIdMap.clear();
      OkxAdapters.instrumentToInstrumentIdMap.putAll(original);
    }
  }

  @Test
  public void testAdaptOrderDetailsInverseContractSizeToVolume() throws IOException {
    FuturesContract contract = new FuturesContract("BTC/USD/260814");
    ObjectMapper mapper = new ObjectMapper();
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    OkxOrderDetails details =
        mapper.readValue(
            "{\"instId\":\"BTC-USD-260814\",\"instType\":\"FUTURES\",\"sz\":\"1\",\"px\":\"50000\","
                + "\"avgPx\":\"50000\",\"accFillSz\":\"1\",\"fillPx\":\"50000\",\"fillSz\":\"1\","
                + "\"fee\":\"0.5\",\"feeCcy\":\"USD\",\"side\":\"buy\",\"ordId\":\"order-1\","
                + "\"uTime\":\"1690000000000\",\"cTime\":\"1690000000000\",\"state\":\"filled\","
                + "\"clOrdId\":\"ref-1\",\"ordType\":\"limit\"}",
            OkxOrderDetails.class);

    Map<Instrument, Long> original = new HashMap<>(OkxAdapters.instrumentToInstrumentIdMap);
    OkxAdapters.instrumentToInstrumentIdMap.clear();
    try {
      ExchangeMetaData metaData = metaDataWithContractValue(contract, new BigDecimal("100"));
      assertThat(
              OkxAdapters.adaptUserTrades(List.of(details), metaData)
                  .getUserTrades()
                  .get(0)
                  .getOriginalAmount())
          .isEqualByComparingTo("0.002");
      assertThat(OkxAdapters.adaptOrder(details, metaData).getOriginalAmount())
          .isEqualByComparingTo("0.002");
    } finally {
      OkxAdapters.instrumentToInstrumentIdMap.clear();
      OkxAdapters.instrumentToInstrumentIdMap.putAll(original);
    }
  }

  @Test
  public void testAdaptTradesInverseContractSizeToVolume() throws IOException {
    FuturesContract contract = new FuturesContract("BTC/USD/260814");
    ObjectMapper mapper = new ObjectMapper();
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    OkxTrade trade =
        mapper.readValue(
            "{\"tradeId\":\"t1\",\"instId\":\"BTC-USD-260814\",\"px\":50000,\"side\":\"buy\","
                + "\"sz\":1,\"ts\":1690000000000}",
            OkxTrade.class);

    Map<Instrument, Long> original = new HashMap<>(OkxAdapters.instrumentToInstrumentIdMap);
    OkxAdapters.instrumentToInstrumentIdMap.clear();
    try {
      ExchangeMetaData metaData = metaDataWithContractValue(contract, new BigDecimal("100"));
      assertThat(
              OkxAdapters.adaptTrades(List.of(trade), contract, metaData)
                  .getTrades()
                  .get(0)
                  .getOriginalAmount())
          .isEqualByComparingTo("0.002");
    } finally {
      OkxAdapters.instrumentToInstrumentIdMap.clear();
      OkxAdapters.instrumentToInstrumentIdMap.putAll(original);
    }
  }

  @Test
  public void testAdaptLimitOrderInverseContractSizeToVolume() {
    FuturesContract contract = new FuturesContract("BTC/USD/260814");
    OkxPublicOrder okxPublicOrder =
        new OkxPublicOrder(new BigDecimal("50000"), new BigDecimal("1"), 0, 0);
    LimitOrder order =
        OkxAdapters.adaptLimitOrder(
            okxPublicOrder, contract, Order.OrderType.ASK, new Date(), new BigDecimal("100"));
    assertThat(order.getOriginalAmount()).isEqualByComparingTo("0.002");
  }

  @Test
  public void testAdaptOrderInverseVolumeToContractSize() {
    FuturesContract contract = new FuturesContract("BTC/USD/260814");
    ExchangeMetaData metaData = metaDataWithContractValue(contract, new BigDecimal("100"));
    Map<Instrument, Long> original = new HashMap<>(OkxAdapters.instrumentToInstrumentIdMap);
    OkxAdapters.instrumentToInstrumentIdMap.clear();
    try {
      OkxAdapters.instrumentToInstrumentIdMap.put(contract, 1234567890L);
      LimitOrder limitOrder =
          new LimitOrder(
              Order.OrderType.BID,
              new BigDecimal("0.002"),
              contract,
              "order-1",
              new Date(),
              new BigDecimal("50000"));
      assertThat(OkxAdapters.adaptOrder(limitOrder, metaData, "1").getAmount()).isEqualTo("1");

      MarketOrder marketOrder =
          new MarketOrder(Order.OrderType.BID, new BigDecimal("0.002"), contract);
      // Inverse market orders carry no price for the volume*price/ctVal conversion; rejecting is
      // safer than silently submitting volume/ctVal.
      assertThatThrownBy(() -> OkxAdapters.adaptOrder(marketOrder, metaData, "1"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("require a price");
    } finally {
      OkxAdapters.instrumentToInstrumentIdMap.clear();
      OkxAdapters.instrumentToInstrumentIdMap.putAll(original);
    }
  }

  @Test
  public void testAdaptOpenPositionsInverseContractSizeToVolume() throws IOException {
    FuturesContract contract = new FuturesContract("BTC/USD/260814");
    ObjectMapper mapper = new ObjectMapper();
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    OkxPosition position =
        mapper.readValue(
            "{\"instType\":\"FUTURES\",\"mgnMode\":\"cross\",\"posSide\":\"net\",\"pos\":\"1\","
                + "\"avgPx\":\"50000\",\"instId\":\"BTC-USD-260814\"}",
            OkxPosition.class);

    Map<Instrument, Long> original = new HashMap<>(OkxAdapters.instrumentToInstrumentIdMap);
    OkxAdapters.instrumentToInstrumentIdMap.clear();
    try {
      ExchangeMetaData metaData = metaDataWithContractValue(contract, new BigDecimal("100"));
      assertThat(
              OkxAdapters.adaptOpenPositions(List.of(position), metaData)
                  .getOpenPositions()
                  .get(0)
                  .getSize())
          .isEqualByComparingTo("0.002");
    } finally {
      OkxAdapters.instrumentToInstrumentIdMap.clear();
      OkxAdapters.instrumentToInstrumentIdMap.putAll(original);
    }
  }

  private static ExchangeMetaData metaDataWithContractValue(
      Instrument instrument, BigDecimal contractValue) {
    return new ExchangeMetaData(
        Map.of(instrument, InstrumentMetaData.builder().contractValue(contractValue).build()),
        Collections.emptyMap(),
        null,
        null,
        null);
  }

  @Test
  public void testAdaptTradesResolvesMetadataThroughWireInstrument() throws IOException {
    // After the unified USD orderbook revamp remote init registers BTC/USD while callers keep
    // trading BTC/USDC; the metadata lookup must resolve the alias or the first trade NPEs.
    ExchangeMetaData metaData =
        new ExchangeMetaData(
            Map.of(new CurrencyPair("BTC/USD"), InstrumentMetaData.builder().build()),
            Collections.emptyMap(),
            null,
            null,
            null);
    ObjectMapper mapper = new ObjectMapper();
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    OkxTrade trade =
        mapper.readValue(
            "{\"tradeId\":\"t1\",\"instId\":\"BTC-USD\",\"px\":50000,\"side\":\"buy\","
                + "\"sz\":1,\"ts\":1690000000000}",
            OkxTrade.class);
    Trades trades = OkxAdapters.adaptTrades(List.of(trade), new CurrencyPair("BTC/USDC"), metaData);
    assertThat(trades.getTrades().get(0).getOriginalAmount()).isEqualByComparingTo("1");
  }

  @Test
  public void testAdaptOrderBookResolvesMetadataThroughWireInstrument() {
    ExchangeMetaData metaData =
        new ExchangeMetaData(
            Map.of(new CurrencyPair("BTC/USD"), InstrumentMetaData.builder().build()),
            Collections.emptyMap(),
            null,
            null,
            null);
    OkxOrderbook orderbook =
        new OkxOrderbook(
            List.of(new OkxPublicOrder(new BigDecimal("50000"), new BigDecimal("1"), 0, 0)),
            List.of(new OkxPublicOrder(new BigDecimal("49000"), new BigDecimal("2"), 0, 0)),
            "1690000000000");
    OrderBook book =
        OkxAdapters.adaptOrderBook(List.of(orderbook), new CurrencyPair("BTC/USDC"), metaData);
    assertThat(book.getAsks()).hasSize(1);
    assertThat(book.getBids()).hasSize(1);
    assertThat(book.getAsks().get(0).getOriginalAmount()).isEqualByComparingTo("1");
  }

  @Test
  public void testAlgoOrderRequestSerializesTriggerWireKeys() throws JsonProcessingException {
    OkxAlgoOrderRequest request =
        OkxAlgoOrderRequest.builder()
            .instrumentId("BTC-USDT-SWAP")
            .tradeMode("cross")
            .side("buy")
            .orderType("trigger")
            .amount("1")
            .triggerPrice("65000")
            .orderPrice("64000")
            .build();
    JsonNode node = new ObjectMapper().readTree(new ObjectMapper().writeValueAsString(request));
    assertThat(node.get("triggerPx").asText()).isEqualTo("65000");
    assertThat(node.get("orderPx").asText()).isEqualTo("64000");
  }

  @Test
  public void testDepositAddressBindsCurrencyFromCcy() throws IOException {
    ObjectMapper mapper = new ObjectMapper();
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    OkxDepositAddress address =
        mapper.readValue(
            "{\"chain\":\"Bitcoin\",\"ctAddr\":\"\",\"ccy\":\"BTC\",\"to\":\"6\",\"addr\":\"addr-1\","
                + "\"selected\":true,\"pmtId\":\"123\"}",
            OkxDepositAddress.class);
    assertThat(address.getCurrency()).isEqualTo("BTC");
    assertThat(address.getPaymentId()).isEqualTo("123");
    assertThat(address.getAddress()).isEqualTo("addr-1");
  }

  @Test
  public void testAmendAlgoRequestSerializesTpSlWireKeys() throws JsonProcessingException {
    // /trade/amend-algos accepts TP/SL-specific amendment keys, not a generic newPx.
    OkxAmendAlgoRequest request =
        OkxAmendAlgoRequest.builder()
            .algoId("algo-1")
            .instrumentId("BTC-USDT-SWAP")
            .amendedAmount("1")
            .newTakeProfitTriggerPrice("70000")
            .newTakeProfitOrderPrice("69000")
            .newStopLossTriggerPrice("50000")
            .newStopLossOrderPrice("51000")
            .build();
    JsonNode node = new ObjectMapper().readTree(new ObjectMapper().writeValueAsString(request));
    assertThat(node.get("newTpTriggerPx").asText()).isEqualTo("70000");
    assertThat(node.get("newTpOrdPx").asText()).isEqualTo("69000");
    assertThat(node.get("newSlTriggerPx").asText()).isEqualTo("50000");
    assertThat(node.get("newSlOrdPx").asText()).isEqualTo("51000");
    assertThat(node.get("newPx")).isNull();
  }

  @Test
  public void testAmendAlgoOrderTakesSingleRequest() throws NoSuchMethodException {
    // /trade/amend-algos accepts one amendment object per request; a List parameter would make
    // Rescu serialize a JSON array and every amend call would be rejected.
    assertThat(
            OkxAuthenticated.class.getMethod(
                "amendAlgoOrders",
                String.class,
                ParamsDigest.class,
                String.class,
                String.class,
                String.class,
                OkxAmendAlgoRequest.class))
        .isNotNull();
    assertThat(OkxTradeServiceRaw.class.getMethod("amendOkxAlgoOrder", OkxAmendAlgoRequest.class))
        .isNotNull();
    assertThat(
            OkexTradeServiceRaw.class.getMethod("amendOkexAlgoOrder", OkexAmendAlgoRequest.class))
        .isNotNull();
  }

  @Test
  public void testOrderRequestUsesSingleAttachAlgoOrdsWireKey() throws IOException {
    // OKX place-order accepts one top-level attachAlgoOrds array; attachAlgoOrs/attachAlgoCls
    // do not exist on the wire and must never be emitted.
    OkxOrderRequest request =
        OkxOrderRequest.builder()
            .instrumentId("BTC-USDT")
            .clientOrderId("cl-1")
            .attachAlgoOrds(
                List.of(
                    OkxAttachAlgoOrder.builder()
                        .takeProfitTriggerPrice("60000")
                        .takeProfitOrderPrice("60000")
                        .amount("1")
                        .build()))
            .build();
    String json = new ObjectMapper().writeValueAsString(request);
    assertThat(json).contains("\"attachAlgoOrds\"");
    assertThat(json).doesNotContain("attachAlgoOrs", "attachAlgoCls");

    // The legacy shim exposes the same single list and maps it onto the canonical request.
    OkexOrderRequest legacy =
        OkexOrderRequest.builder()
            .instrumentId("BTC-USDT")
            .attachAlgoOrds(
                List.of(OkexAttachAlgoOrder.builder().takeProfitTriggerPrice("60000").build()))
            .build();
    OkxOrderRequest converted = legacy.to();
    assertThat(converted.getAttachAlgoOrds()).hasSize(1);
    assertThat(converted.getAttachAlgoOrds().get(0).getTakeProfitTriggerPrice()).isEqualTo("60000");
  }
}
