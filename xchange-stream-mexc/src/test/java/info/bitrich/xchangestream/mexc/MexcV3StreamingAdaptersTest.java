package info.bitrich.xchangestream.mexc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.InvalidProtocolBufferException;
import com.mxc.push.common.protobuf.PublicAggreBookTickerV3Api;
import com.mxc.push.common.protobuf.PublicAggreDealsV3Api;
import com.mxc.push.common.protobuf.PublicAggreDealsV3ApiItem;
import com.mxc.push.common.protobuf.PublicSpotKlineV3Api;
import com.mxc.push.common.protobuf.PushDataV3ApiWrapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.marketdata.CandleStick;
import org.knowm.xchange.dto.marketdata.CandleStickData;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trade;

/** Adapter tests: protobuf push (as canonical JSON) to XChange DTOs with exact decimals. */
class MexcV3StreamingAdaptersTest {

  private static final CurrencyPair PAIR = new CurrencyPair(Currency.BTC, Currency.USDT);

  private String toJson(PushDataV3ApiWrapper wrapper) throws InvalidProtocolBufferException {
    return MexcV3ProtoCodec.toJson(wrapper);
  }

  @Test
  void adaptBookTickerMapsBidAskAndCreateTime() throws InvalidProtocolBufferException {
    PublicAggreBookTickerV3Api ticker =
        PublicAggreBookTickerV3Api.newBuilder()
            .setBidPrice("65432.10")
            .setBidQuantity("1.25000000")
            .setAskPrice("65432.20")
            .setAskQuantity("0.75000000")
            .build();
    PushDataV3ApiWrapper wrapper =
        PushDataV3ApiWrapper.newBuilder()
            .setChannel("spot@public.aggre.bookTicker.v3.api.pb@100ms@BTCUSDT")
            .setSymbol("BTCUSDT")
            .setCreateTime(1_712_345_678_901L)
            .setPublicAggreBookTicker(ticker)
            .build();

    Ticker adapted = MexcV3StreamingAdapters.adaptBookTicker(toJson(wrapper), PAIR);

    assertEquals(new BigDecimal("65432.10"), adapted.getBid());
    assertEquals(new BigDecimal("1.25000000"), adapted.getBidSize());
    assertEquals(new BigDecimal("65432.20"), adapted.getAsk());
    assertEquals(new BigDecimal("0.75000000"), adapted.getAskSize());
    assertEquals(PAIR, adapted.getInstrument());
    assertEquals(Date.from(Instant.ofEpochMilli(1_712_345_678_901L)), adapted.getTimestamp());
  }

  @Test
  void adaptAggreDealsMapsItemsToTradesWithSides() throws InvalidProtocolBufferException {
    PublicAggreDealsV3ApiItem buy =
        PublicAggreDealsV3ApiItem.newBuilder()
            .setTradeId("111")
            .setPrice("65430.00")
            .setQuantity("0.10000000")
            .setTradeType(1)
            .setTime(1_712_345_678_100L)
            .build();
    PublicAggreDealsV3ApiItem sell =
        PublicAggreDealsV3ApiItem.newBuilder()
            .setTradeId("112")
            .setPrice("65431.00")
            .setQuantity("0.20000000")
            .setTradeType(2)
            .setTime(1_712_345_678_200L)
            .build();
    PublicAggreDealsV3Api deals =
        PublicAggreDealsV3Api.newBuilder().addDeals(buy).addDeals(sell).build();
    PushDataV3ApiWrapper wrapper =
        PushDataV3ApiWrapper.newBuilder()
            .setChannel("spot@public.aggre.deals.v3.api.pb@100ms@BTCUSDT")
            .setSymbol("BTCUSDT")
            .setCreateTime(1_712_345_678_901L)
            .setPublicAggreDeals(deals)
            .build();

    List<Trade> trades = MexcV3StreamingAdapters.adaptAggreDeals(toJson(wrapper), PAIR);

    assertEquals(2, trades.size());
    Trade first = trades.get(0);
    assertEquals(OrderType.BID, first.getType());
    assertEquals("111", first.getId());
    assertEquals(new BigDecimal("65430.00"), first.getPrice());
    assertEquals(new BigDecimal("0.10000000"), first.getOriginalAmount());
    assertEquals(PAIR, first.getInstrument());
    assertEquals(Date.from(Instant.ofEpochMilli(1_712_345_678_100L)), first.getTimestamp());
    Trade second = trades.get(1);
    assertEquals(OrderType.ASK, second.getType());
    assertEquals("112", second.getId());
    assertEquals(new BigDecimal("65431.00"), second.getPrice());
    assertEquals(new BigDecimal("0.20000000"), second.getOriginalAmount());
  }

  @Test
  void adaptKlineMapsSingleNotCompletedCandle() throws InvalidProtocolBufferException {
    PublicSpotKlineV3Api kline =
        PublicSpotKlineV3Api.newBuilder()
            .setInterval("Min1")
            .setOpeningPrice("65400.00")
            .setClosingPrice("65450.00")
            .setHighestPrice("65460.00")
            .setLowestPrice("65390.00")
            .setVolume("12.50000000")
            .setAmount("817500.5")
            .setWindowStart(1_712_345_660L)
            .build();
    PushDataV3ApiWrapper wrapper =
        PushDataV3ApiWrapper.newBuilder()
            .setChannel("spot@public.kline.v3.api.pb@BTCUSDT@Min1")
            .setSymbol("BTCUSDT")
            .setCreateTime(1_712_345_678_901L)
            .setPublicSpotKline(kline)
            .build();

    CandleStickData data = MexcV3StreamingAdapters.adaptKline(toJson(wrapper), PAIR);

    assertEquals(PAIR, data.getInstrument());
    assertEquals(1, data.getCandleSticks().size());
    CandleStick stick = data.getCandleSticks().get(0);
    assertEquals(Instant.ofEpochSecond(1_712_345_660L), stick.getTimestamp());
    assertEquals(new BigDecimal("65400.00"), stick.getOpen());
    assertEquals(new BigDecimal("65450.00"), stick.getLast());
    assertEquals(new BigDecimal("65460.00"), stick.getHigh());
    assertEquals(new BigDecimal("65390.00"), stick.getLow());
    assertEquals(new BigDecimal("65450.00"), stick.getClose());
    assertEquals(new BigDecimal("12.50000000"), stick.getVolume());
    assertEquals(new BigDecimal("817500.5"), stick.getQuotaVolume());
    assertTrue(!stick.isCompleted(), "in-progress candle must not be completed");
  }

  @Test
  void adaptBookTickerRejectsWrongBody() throws InvalidProtocolBufferException {
    PushDataV3ApiWrapper dealsWrapper =
        PushDataV3ApiWrapper.newBuilder()
            .setChannel("spot@public.aggre.deals.v3.api.pb@100ms@BTCUSDT")
            .setSymbol("BTCUSDT")
            .setPublicAggreDeals(PublicAggreDealsV3Api.getDefaultInstance())
            .build();
    assertThrows(
        IllegalArgumentException.class,
        () -> MexcV3StreamingAdapters.adaptBookTicker(toJson(dealsWrapper), PAIR));
  }

  @Test
  void adaptAggreDealsRejectsWrongBody() throws InvalidProtocolBufferException {
    PushDataV3ApiWrapper tickerWrapper =
        PushDataV3ApiWrapper.newBuilder()
            .setChannel("spot@public.aggre.bookTicker.v3.api.pb@100ms@BTCUSDT")
            .setSymbol("BTCUSDT")
            .setPublicAggreBookTicker(PublicAggreBookTickerV3Api.getDefaultInstance())
            .build();
    assertThrows(
        IllegalArgumentException.class,
        () -> MexcV3StreamingAdapters.adaptAggreDeals(toJson(tickerWrapper), PAIR));
  }

  @Test
  void adaptKlineRejectsWrongBody() throws InvalidProtocolBufferException {
    PushDataV3ApiWrapper dealsWrapper =
        PushDataV3ApiWrapper.newBuilder()
            .setChannel("spot@public.kline.v3.api.pb@BTCUSDT@Min1")
            .setSymbol("BTCUSDT")
            .setPublicAggreDeals(PublicAggreDealsV3Api.getDefaultInstance())
            .build();
    assertThrows(
        IllegalArgumentException.class,
        () -> MexcV3StreamingAdapters.adaptKline(toJson(dealsWrapper), PAIR));
  }
}
