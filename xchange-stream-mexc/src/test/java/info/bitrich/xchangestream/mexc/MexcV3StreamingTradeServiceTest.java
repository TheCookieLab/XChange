package info.bitrich.xchangestream.mexc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mxc.push.common.protobuf.PrivateDealsV3Api;
import com.mxc.push.common.protobuf.PrivateOrdersV3Api;
import com.mxc.push.common.protobuf.PushDataV3ApiWrapper;
import io.reactivex.rxjava3.observers.TestObserver;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.Order.OrderStatus;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.instrument.Instrument;

/** Trade stream: channel names, order/deal adaptation, and per-instrument filtering. */
class MexcV3StreamingTradeServiceTest {

  private static final CurrencyPair PAIR = new CurrencyPair(Currency.BTC, Currency.USDT);
  private static final CurrencyPair OTHER_PAIR = new CurrencyPair(Currency.ETH, Currency.USDT);
  private static final String ORDERS_CHANNEL = "spot@private.orders.v3.api.pb";
  private static final String DEALS_CHANNEL = "spot@private.deals.v3.api.pb";

  private static PushDataV3ApiWrapper orderPushWrapper(String symbol) {
    PrivateOrdersV3Api order =
        PrivateOrdersV3Api.newBuilder()
            .setId("order-42")
            .setClientId("client-ref-7")
            .setPrice("65400.00")
            .setQuantity("0.50000000")
            .setAvgPrice("65398.50")
            .setOrderType(1)
            .setTradeType(1)
            .setCumulativeQuantity("0.20000000")
            .setStatus(2)
            .setCreateTime(1_712_345_678_901L)
            .build();
    return PushDataV3ApiWrapper.newBuilder()
        .setChannel(ORDERS_CHANNEL)
        .setSymbol(symbol)
        .setPrivateOrders(order)
        .build();
  }

  private static String orderPushJson(String symbol) {
    try {
      return MexcV3ProtoCodec.toJson(orderPushWrapper(symbol));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static String dealPushJson(String symbol) {
    PrivateDealsV3Api deal =
        PrivateDealsV3Api.newBuilder()
            .setPrice("65400.00")
            .setQuantity("0.05000000")
            .setTradeType(1)
            .setTradeId("deal-9")
            .setClientOrderId("client-ref-7")
            .setOrderId("order-42")
            .setFeeAmount("0.32699999")
            .setFeeCurrency("USDT")
            .setTime(1_712_345_678_901L)
            .build();
    PushDataV3ApiWrapper wrapper =
        PushDataV3ApiWrapper.newBuilder()
            .setChannel(DEALS_CHANNEL)
            .setSymbol(symbol)
            .setPrivateDeals(deal)
            .build();
    try {
      return MexcV3ProtoCodec.toJson(wrapper);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @Test
  void getOrderChangesSubscribesChannelAndMapsOrder() {
    StubStreamingService service = new StubStreamingService();
    service.enqueue(ORDERS_CHANNEL, orderPushJson("BTCUSDT"));
    MexcV3StreamingTradeService tradeService = new MexcV3StreamingTradeService(service);

    Order order = tradeService.getOrderChanges(PAIR).blockingFirst();

    assertEquals(PAIR, order.getInstrument());
    assertEquals(OrderType.BID, order.getType());
    assertEquals(OrderStatus.FILLED, order.getStatus());
    assertEquals("order-42", order.getId());
    assertEquals("client-ref-7", order.getUserReference());
    assertEquals(new BigDecimal("65400.00"), ((org.knowm.xchange.dto.trade.LimitOrder) order).getLimitPrice());
    assertTrue(service.subscribedChannels.contains(ORDERS_CHANNEL));
  }

  @Test
  void getUserTradesSubscribesChannelAndMapsDeal() {
    StubStreamingService service = new StubStreamingService();
    service.enqueue(DEALS_CHANNEL, dealPushJson("BTCUSDT"));
    MexcV3StreamingTradeService tradeService = new MexcV3StreamingTradeService(service);

    UserTrade trade = tradeService.getUserTrades(PAIR).blockingFirst();

    assertEquals(PAIR, trade.getInstrument());
    assertEquals(OrderType.BID, trade.getType());
    assertEquals("deal-9", trade.getId());
    assertEquals("order-42", trade.getOrderId());
    assertEquals("client-ref-7", trade.getOrderUserReference());
    assertEquals(new BigDecimal("0.32699999"), trade.getFeeAmount());
    assertTrue(service.subscribedChannels.contains(DEALS_CHANNEL));
  }

  @Test
  void eventsAreFilteredByInstrumentAndNullReceivesAll() {
    StubStreamingService service = new StubStreamingService();
    service.enqueue(ORDERS_CHANNEL, orderPushJson("ETHUSDT"));
    service.enqueue(ORDERS_CHANNEL, orderPushJson("BTCUSDT"));
    MexcV3StreamingTradeService tradeService = new MexcV3StreamingTradeService(service);

    tradeService
        .getOrderChanges(PAIR)
        .test()
        .awaitDone(1, TimeUnit.SECONDS)
        .assertNoValues();

    Order all = tradeService.getOrderChanges((Instrument) null).blockingFirst();
    assertEquals(PAIR, all.getInstrument(), "null instrument receives every order event");
  }

  @Test
  void twoConsumersShareOnePrivateChannelSubscriptionAndFilterIndependently() throws Exception {
    MexcV3StreamingServiceTest.CapturingService service =
        new MexcV3StreamingServiceTest.CapturingService();
    MexcV3StreamingServiceTest.forceOpenChannel(service);
    MexcV3StreamingTradeService tradeService = new MexcV3StreamingTradeService(service);

    TestObserver<Order> btc = tradeService.getOrderChanges(PAIR).test();
    TestObserver<Order> eth = tradeService.getOrderChanges(OTHER_PAIR).test();

    service.handleBinaryPush(orderPushWrapper("BTCUSDT").toByteArray());
    service.handleBinaryPush(orderPushWrapper("ETHUSDT").toByteArray());

    btc.assertValueCount(1)
        .assertValue(order -> PAIR.equals(order.getInstrument()));
    eth.assertValueCount(1)
        .assertValue(order -> OTHER_PAIR.equals(order.getInstrument()));
    assertEquals(
        1, service.channelCount(), "both consumers share one private-channel subscription");

    // Disposing one consumer must not tear down the shared subscription of the other.
    btc.dispose();
    service.handleBinaryPush(orderPushWrapper("ETHUSDT").toByteArray());
    eth.assertValueCount(2)
        .assertValueAt(1, order -> OTHER_PAIR.equals(order.getInstrument()));
  }

  /** Captures subscribed channels and serves enqueued canned pushes. */
  private static final class StubStreamingService extends MexcV3StreamingService {

    private final List<String> subscribedChannels = new ArrayList<>();
    private final Map<String, Queue<String>> pushes = new HashMap<>();

    StubStreamingService() {
      super("ws://stub/ws");
    }

    void enqueue(String channel, String json) {
      pushes.computeIfAbsent(channel, k -> new LinkedList<>()).add(json);
    }

    @Override
    public io.reactivex.rxjava3.core.Observable<String> subscribeChannel(
        String channelName, Object... args) {
      subscribedChannels.add(channelName);
      return io.reactivex.rxjava3.core.Observable.defer(
          () -> {
            Queue<String> queue = pushes.get(channelName);
            String next = queue == null ? null : queue.poll();
            return next == null
                ? io.reactivex.rxjava3.core.Observable.never()
                : io.reactivex.rxjava3.core.Observable.just(next);
          });
    }
  }
}
