package info.bitrich.xchangestream.bitget.uta.v3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import info.bitrich.xchangestream.service.netty.NettyStreamingService;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.reactivex.rxjava3.observers.TestObserver;
import java.lang.reflect.Field;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.bitget.uta.v3.service.BitgetUtaV3TradeService;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.marketdata.CandleStickData;
import org.knowm.xchange.dto.marketdata.CandleStickInterval;

/**
 * Concurrent subscribers on one subscription id must each receive pushes, and disposing one
 * subscriber must not tear down the channel for the others.
 */
class BitgetUtaV3StreamingSubscriptionTest {

  private static final CurrencyPair BTC = CurrencyPair.BTC_USDT;
  private static final CurrencyPair ETH = CurrencyPair.ETH_USDT;

  private static void injectOpenChannel(BitgetUtaV3StreamingService service) throws Exception {
    Channel channel = mock(Channel.class);
    when(channel.isOpen()).thenReturn(true);
    when(channel.isWritable()).thenReturn(true);
    when(channel.writeAndFlush(any())).thenReturn(mock(ChannelFuture.class));
    Field field = NettyStreamingService.class.getDeclaredField("webSocketChannel");
    field.setAccessible(true);
    field.set(service, channel);
  }

  private static BitgetUtaV3StreamingService newPublicService() throws Exception {
    BitgetUtaV3StreamingService service = new BitgetUtaV3StreamingService("wss://localhost/public");
    injectOpenChannel(service);
    return service;
  }

  private static BitgetUtaV3PrivateStreamingService newPrivateService() throws Exception {
    BitgetUtaV3PrivateStreamingService service =
        new BitgetUtaV3PrivateStreamingService(
            "wss://localhost/private", "apiKey", "apiSecret", "passphrase");
    injectOpenChannel(service);
    return service;
  }

  /** UTA private order-channel push; symbol identifies the instrument in the payload. */
  private static String orderPush(String orderId, String symbol, String clientOid) {
    return "{\"action\":\"snapshot\",\"arg\":{\"instType\":\"UTA\",\"topic\":\"order\"},"
        + "\"data\":[{\"orderId\":\""
        + orderId
        + "\",\"clientOid\":\""
        + clientOid
        + "\",\"symbol\":\""
        + symbol
        + "\",\"side\":\"buy\",\"orderType\":\"limit\",\"price\":\"100.0\","
        + "\"qty\":\"0.5\",\"orderStatus\":\"live\",\"createdTime\":\"1710518400000\"}],"
        + "\"ts\":1710518400000}";
  }

  /** Kline push; the provider echoes the interval inside the push {@code arg}. */
  private static String klinePush(String interval) {
    return "{\"action\":\"snapshot\",\"arg\":{\"instType\":\"spot\",\"topic\":\"kline\","
        + "\"symbol\":\"BTCUSDT\",\"interval\":\""
        + interval
        + "\"},"
        + "\"data\":[{\"start\":\"1710518400000\",\"open\":\"100.0\",\"close\":\"101.0\","
        + "\"high\":\"102.0\",\"low\":\"99.0\",\"volume\":\"10.0\",\"turnover\":\"1000.0\"}],"
        + "\"ts\":1710518400000}";
  }

  @Test
  void concurrentPerInstrumentSubscriptionsEachReceivePushesAndSurvivePeerDispose()
      throws Exception {
    BitgetUtaV3PrivateStreamingService ws = newPrivateService();
    BitgetUtaV3StreamingTradeService trade =
        new BitgetUtaV3StreamingTradeService(ws, mock(BitgetUtaV3TradeService.class));

    TestObserver<Order> btc = trade.getOrderChanges(BTC).test();
    TestObserver<Order> eth = trade.getOrderChanges(ETH).test();

    ws.messageHandler(orderPush("order-1", "BTCUSDT", "client-1"));
    btc.assertValueCount(1);

    // the second per-instrument subscriber must receive its own pushes
    ws.messageHandler(orderPush("order-2", "ETHUSDT", "client-2"));
    eth.assertValueCount(1);

    // disposing one subscriber must not unsubscribe the shared channel for the other
    eth.dispose();
    ws.messageHandler(orderPush("order-3", "BTCUSDT", "client-3"));
    btc.assertValueCount(2);
  }

  @Test
  void klineIntervalsOnTheSameSymbolRouteToTheirOwnSubscribers() throws Exception {
    BitgetUtaV3StreamingService ws = newPublicService();
    BitgetUtaV3StreamingMarketDataService marketData =
        new BitgetUtaV3StreamingMarketDataService(ws);

    TestObserver<CandleStickData> m1 =
        marketData.getCandleStick(BTC, CandleStickInterval.m1).test();
    TestObserver<CandleStickData> h1 =
        marketData.getCandleStick(BTC, CandleStickInterval.h1).test();

    ws.messageHandler(klinePush("1m"));
    m1.assertValueCount(1);

    // a different interval on the same symbol must reach its own subscriber
    ws.messageHandler(klinePush("1H"));
    h1.assertValueCount(1);

    // disposing the 1m subscriber must not tear down the 1H channel
    m1.dispose();
    ws.messageHandler(klinePush("1H"));
    h1.assertValueCount(2);
  }

  @Test
  void disposingAnOrderBookSubscriptionEvictsItsAssembler() throws Exception {
    BitgetUtaV3StreamingService ws = newPublicService();
    BitgetUtaV3StreamingMarketDataService marketData =
        new BitgetUtaV3StreamingMarketDataService(ws);

    marketData.getOrderBook(BTC).test().dispose();

    Field field = BitgetUtaV3StreamingMarketDataService.class.getDeclaredField("assemblers");
    field.setAccessible(true);
    Map<?, ?> assemblers = (Map<?, ?>) field.get(marketData);
    assertThat(assemblers).isEmpty();
  }
}
