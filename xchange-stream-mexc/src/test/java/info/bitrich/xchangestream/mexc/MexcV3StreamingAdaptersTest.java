package info.bitrich.xchangestream.mexc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.InvalidProtocolBufferException;
import com.mxc.push.common.protobuf.PrivateAccountV3Api;
import com.mxc.push.common.protobuf.PrivateDealsV3Api;
import com.mxc.push.common.protobuf.PrivateOrdersV3Api;
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
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.Order.OrderStatus;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.account.Balance;
import org.knowm.xchange.dto.marketdata.CandleStick;
import org.knowm.xchange.dto.marketdata.CandleStickData;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trade;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.dto.trade.UserTrade;

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

  @Test
  void adaptAccountPushMapsBalance() throws InvalidProtocolBufferException {
    PrivateAccountV3Api account =
        PrivateAccountV3Api.newBuilder()
            .setVcoinName("BTC")
            .setBalanceAmount("1.50000000")
            .setBalanceAmountChange("-0.10000000")
            .setFrozenAmount("0.25000000")
            .setFrozenAmountChange("0.25000000")
            .setType("1")
            .setTime(1_712_345_678_901L)
            .build();
    PushDataV3ApiWrapper wrapper =
        PushDataV3ApiWrapper.newBuilder()
            .setChannel("spot@private.account.v3.api.pb")
            .setSymbol("BTCUSDT")
            .setPrivateAccount(account)
            .build();

    Balance balance = MexcV3StreamingAdapters.adaptAccountPush(toJson(wrapper));

    assertEquals(Currency.BTC, balance.getCurrency());
    assertEquals(new BigDecimal("1.50000000"), balance.getTotal());
    assertEquals(new BigDecimal("0.25000000"), balance.getFrozen());
    assertEquals(new BigDecimal("1.25000000"), balance.getAvailable());
    assertEquals(Date.from(Instant.ofEpochMilli(1_712_345_678_901L)), balance.getTimestamp());
  }

  @Test
  void adaptOrderPushMapsLimitOrder() throws InvalidProtocolBufferException {
    PrivateOrdersV3Api order =
        PrivateOrdersV3Api.newBuilder()
            .setId("order-42")
            .setClientId("client-ref-7")
            .setPrice("65400.00")
            .setQuantity("0.50000000")
            .setAmount("32700.00")
            .setAvgPrice("65398.50")
            .setOrderType(1)
            .setTradeType(1)
            .setCumulativeQuantity("0.20000000")
            .setStatus(1)
            .setCreateTime(1_712_345_678_901L)
            .build();
    PushDataV3ApiWrapper wrapper =
        PushDataV3ApiWrapper.newBuilder()
            .setChannel("spot@private.orders.v3.api.pb")
            .setSymbol("BTCUSDT")
            .setPrivateOrders(order)
            .build();

    Order adapted = MexcV3StreamingAdapters.adaptOrderPush(toJson(wrapper));

    assertTrue(adapted instanceof LimitOrder);
    LimitOrder limit = (LimitOrder) adapted;
    assertEquals(OrderType.BID, limit.getType());
    assertEquals(OrderStatus.NEW, limit.getStatus());
    assertEquals(PAIR, limit.getInstrument());
    assertEquals("order-42", limit.getId());
    assertEquals("client-ref-7", limit.getUserReference());
    assertEquals(new BigDecimal("65400.00"), limit.getLimitPrice());
    assertEquals(new BigDecimal("65398.50"), limit.getAveragePrice());
    assertEquals(new BigDecimal("0.50000000"), limit.getOriginalAmount());
    assertEquals(new BigDecimal("0.20000000"), limit.getCumulativeAmount());
    assertEquals(Date.from(Instant.ofEpochMilli(1_712_345_678_901L)), limit.getTimestamp());
  }

  @Test
  void adaptOrderPushMapsMarketOrderStatusAndSide() throws InvalidProtocolBufferException {
    PrivateOrdersV3Api order =
        PrivateOrdersV3Api.newBuilder()
            .setId("order-43")
            .setPrice("0")
            .setQuantity("0.30000000")
            .setAmount("19620.00")
            .setAvgPrice("65400.00")
            .setOrderType(5)
            .setTradeType(2)
            .setCumulativeQuantity("0.30000000")
            .setStatus(3)
            .setCreateTime(1_712_345_678_902L)
            .build();
    PushDataV3ApiWrapper wrapper =
        PushDataV3ApiWrapper.newBuilder()
            .setChannel("spot@private.orders.v3.api.pb")
            .setSymbol("BTCUSDT")
            .setPrivateOrders(order)
            .build();

    Order adapted = MexcV3StreamingAdapters.adaptOrderPush(toJson(wrapper));

    assertTrue(adapted instanceof MarketOrder);
    assertEquals(OrderType.ASK, adapted.getType());
    assertEquals(OrderStatus.PARTIALLY_FILLED, adapted.getStatus());
    assertEquals(new BigDecimal("65400.00"), adapted.getAveragePrice());
    assertEquals(new BigDecimal("0.30000000"), adapted.getCumulativeAmount());
  }

  @Test
  void adaptOrderPushMapsFilledAndCanceledStatuses() throws InvalidProtocolBufferException {
    PushDataV3ApiWrapper filled =
        PushDataV3ApiWrapper.newBuilder()
            .setChannel("spot@private.orders.v3.api.pb")
            .setSymbol("BTCUSDT")
            .setPrivateOrders(
                PrivateOrdersV3Api.newBuilder()
                    .setOrderType(1)
                    .setTradeType(1)
                    .setStatus(2)
                    .setPrice("65400.00")
                    .setQuantity("0.1")
                    .setAvgPrice("65400.00")
                    .setCumulativeQuantity("0.1")
                    .setCreateTime(1L)
                    .build())
            .build();
    assertEquals(
        OrderStatus.FILLED,
        MexcV3StreamingAdapters.adaptOrderPush(toJson(filled)).getStatus());

    PushDataV3ApiWrapper canceled =
        PushDataV3ApiWrapper.newBuilder()
            .setChannel("spot@private.orders.v3.api.pb")
            .setSymbol("BTCUSDT")
            .setPrivateOrders(
                PrivateOrdersV3Api.newBuilder()
                    .setOrderType(1)
                    .setTradeType(1)
                    .setStatus(4)
                    .setPrice("65400.00")
                    .setQuantity("0.1")
                    .setAvgPrice("0")
                    .setCumulativeQuantity("0")
                    .setCreateTime(1L)
                    .build())
            .build();
    assertEquals(
        OrderStatus.CANCELED,
        MexcV3StreamingAdapters.adaptOrderPush(toJson(canceled)).getStatus());
  }

  @Test
  void adaptUserTradePushMapsDeal() throws InvalidProtocolBufferException {
    PrivateDealsV3Api deal =
        PrivateDealsV3Api.newBuilder()
            .setPrice("65400.00")
            .setQuantity("0.05000000")
            .setAmount("3270.00")
            .setTradeType(1)
            .setIsMaker(true)
            .setTradeId("deal-9")
            .setClientOrderId("client-ref-7")
            .setOrderId("order-42")
            .setFeeAmount("0.32699999")
            .setFeeCurrency("USDT")
            .setTime(1_712_345_678_901L)
            .build();
    PushDataV3ApiWrapper wrapper =
        PushDataV3ApiWrapper.newBuilder()
            .setChannel("spot@private.deals.v3.api.pb")
            .setSymbol("BTCUSDT")
            .setPrivateDeals(deal)
            .build();

    UserTrade adapted = MexcV3StreamingAdapters.adaptUserTradePush(toJson(wrapper));

    assertEquals(OrderType.BID, adapted.getType());
    assertEquals(PAIR, adapted.getInstrument());
    assertEquals(new BigDecimal("65400.00"), adapted.getPrice());
    assertEquals(new BigDecimal("0.05000000"), adapted.getOriginalAmount());
    assertEquals("deal-9", adapted.getId());
    assertEquals("order-42", adapted.getOrderId());
    assertEquals("client-ref-7", adapted.getOrderUserReference());
    assertEquals(new BigDecimal("0.32699999"), adapted.getFeeAmount());
    assertEquals(Currency.USDT, adapted.getFeeCurrency());
    assertEquals(Date.from(Instant.ofEpochMilli(1_712_345_678_901L)), adapted.getTimestamp());
  }

  @Test
  void privateAdaptersRejectWrongBody() throws InvalidProtocolBufferException {
    PushDataV3ApiWrapper tickerWrapper =
        PushDataV3ApiWrapper.newBuilder()
            .setChannel("spot@private.account.v3.api.pb")
            .setSymbol("BTCUSDT")
            .setPublicAggreBookTicker(PublicAggreBookTickerV3Api.getDefaultInstance())
            .build();
    assertThrows(
        IllegalArgumentException.class,
        () -> MexcV3StreamingAdapters.adaptAccountPush(toJson(tickerWrapper)));
    assertThrows(
        IllegalArgumentException.class,
        () -> MexcV3StreamingAdapters.adaptOrderPush(toJson(tickerWrapper)));
    assertThrows(
        IllegalArgumentException.class,
        () -> MexcV3StreamingAdapters.adaptUserTradePush(toJson(tickerWrapper)));
  }
}
