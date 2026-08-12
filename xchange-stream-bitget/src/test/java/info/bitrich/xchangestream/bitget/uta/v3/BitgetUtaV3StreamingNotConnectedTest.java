package info.bitrich.xchangestream.bitget.uta.v3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import info.bitrich.xchangestream.bitget.uta.v3.dto.BitgetUtaV3Channel;
import info.bitrich.xchangestream.bitget.uta.v3.dto.BitgetUtaV3InstType;
import info.bitrich.xchangestream.bitget.uta.v3.dto.BitgetUtaV3WsNotification;
import info.bitrich.xchangestream.service.exception.NotConnectedException;
import info.bitrich.xchangestream.service.netty.NettyStreamingService;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.observers.TestObserver;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.Ticker;

/**
 * A public market-data subscription made before {@code connect()} completes must not poison the
 * channel registry or the shared-observable caches, so a post-connect retry of the same ticker,
 * book, trade, or kline channel registers normally.
 *
 * <p>The inherited {@link NettyStreamingService#subscribeChannel} signals {@link
 * NotConnectedException} when the socket is not open but then continues into the channel
 * registry, storing the already-terminated emitter; a retry after connect finds that dead entry,
 * registers no replacement emitter, and silently loses every push. {@link
 * BitgetUtaV3StreamingService#subscribeChannel} rejects the not-connected subscription without
 * registering it, and both {@link BitgetUtaV3StreamingService#sharedChannel} and {@link
 * BitgetUtaV3StreamingMarketDataService} evict a terminated shared stream so the retry builds a
 * fresh one.
 */
class BitgetUtaV3StreamingNotConnectedTest {

  private static final BitgetUtaV3Channel BTC_TRADE =
      BitgetUtaV3Channel.builder()
          .instType(BitgetUtaV3InstType.SPOT)
          .topic("trade")
          .symbol("BTCUSDT")
          .build();

  private static final BitgetUtaV3Channel BTC_TICKER =
      BitgetUtaV3Channel.builder()
          .instType(BitgetUtaV3InstType.SPOT)
          .topic("ticker")
          .symbol("BTCUSDT")
          .build();

  /** Subclass recording outbound frames and exposing registry state without a live socket. */
  private static class TestableStreamingService extends BitgetUtaV3StreamingService {

    private final List<String> frames = new ArrayList<>();

    TestableStreamingService() {
      super("ws://127.0.0.1:9");
    }

    /** Simulates a completed socket handshake. */
    void open() {
      injectOpenChannel(this);
    }

    List<String> frames() {
      return frames;
    }

    boolean containsChannel(String subscriptionId) {
      return channels.containsKey(subscriptionId);
    }

    Map<String, Observable<BitgetUtaV3WsNotification>> sharedChannels() {
      return sharedChannelsForTesting();
    }

    @Override
    public void sendMessage(String message) {
      frames.add(message);
    }
  }

  private static void injectOpenChannel(BitgetUtaV3StreamingService service) {
    io.netty.channel.Channel channel = mock(io.netty.channel.Channel.class);
    when(channel.isOpen()).thenReturn(true);
    when(channel.isWritable()).thenReturn(true);
    when(channel.writeAndFlush(any())).thenReturn(mock(io.netty.channel.ChannelFuture.class));
    try {
      Field field = NettyStreamingService.class.getDeclaredField("webSocketChannel");
      field.setAccessible(true);
      field.set(service, channel);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new IllegalStateException("Cannot inject WebSocket channel", e);
    }
  }

  private static Map<String, Observable<BitgetUtaV3WsNotification>> marketDataSharedChannels(
      BitgetUtaV3StreamingMarketDataService service) {
    return service.sharedChannelsForTesting();
  }

  @Test
  void subscribeChannelBeforeConnectSignalsNotConnectedAndSkipsRegistry() {
    TestableStreamingService service = new TestableStreamingService();

    TestObserver<BitgetUtaV3WsNotification> observer =
        service.subscribeChannel(null, BTC_TRADE).test();

    observer.assertError(NotConnectedException.class);
    assertThat(service.containsChannel(BTC_TRADE.toSubscriptionId())).isFalse();
  }

  @Test
  void retryAfterConnectRegistersChannelAndSendsSubscribeFrame() {
    TestableStreamingService service = new TestableStreamingService();
    service.subscribeChannel(null, BTC_TRADE).test().assertError(NotConnectedException.class);
    // the rejected subscription must not have left a dead emitter in the registry, or the retry
    // below would find it and register no replacement (the inherited not-connected branch stores
    // the terminated emitter; the override returns without registering)
    assertThat(service.containsChannel(BTC_TRADE.toSubscriptionId())).isFalse();

    service.open();

    TestObserver<BitgetUtaV3WsNotification> retry =
        service.subscribeChannel(null, BTC_TRADE).test();
    retry.assertNoErrors();
    assertThat(service.containsChannel(BTC_TRADE.toSubscriptionId())).isTrue();
    assertThat(service.frames()).anyMatch(frame -> frame.contains("\"subscribe\""));
    retry.dispose();
    assertThat(service.frames()).anyMatch(frame -> frame.contains("\"unsubscribe\""));
  }

  @Test
  void composingMarketDataObservablesWithoutSubscribingRetainsNothing() throws Exception {
    TestableStreamingService service = new TestableStreamingService();
    BitgetUtaV3StreamingMarketDataService marketData =
        new BitgetUtaV3StreamingMarketDataService(service);
    service.open();

    // composing observable feeds without subscribing (e.g. optional combinations) must not
    // materialize cache entries, assemblers, or channel registrations for the service lifetime
    marketData.getTicker(CurrencyPair.BTC_USDT);
    marketData.getTicker(CurrencyPair.ETH_USDT);
    marketData.getOrderBook(CurrencyPair.BTC_USDT);

    assertThat(marketDataSharedChannels(marketData)).isEmpty();
    assertThat(service.containsChannel(BTC_TICKER.toSubscriptionId())).isFalse();
    assertThat(service.frames()).isEmpty();
  }

  @Test
  void consumersOfTheSameMarketDataChannelShareTheWinningObservable() throws Exception {
    TestableStreamingService service = new TestableStreamingService();
    BitgetUtaV3StreamingMarketDataService marketData =
        new BitgetUtaV3StreamingMarketDataService(service);
    service.open();

    TestObserver<Ticker> first = marketData.getTicker(CurrencyPair.BTC_USDT).test();
    // a second consumer of the same channel must attach to the canonical observable that won the
    // per-subscription cache: the service keeps a single emitter per subscription id, so a second
    // independently constructed stream would silently receive no pushes (review wave 15k)
    Observable<Ticker> secondSource = marketData.getTicker(CurrencyPair.BTC_USDT);
    TestObserver<Ticker> second = secondSource.test();

    // exactly one wire subscription, one registry entry, one cache entry
    assertThat(service.frames().stream().filter(f -> f.contains("\"subscribe\"")).count())
        .isEqualTo(1);
    assertThat(service.containsChannel(BTC_TICKER.toSubscriptionId())).isTrue();
    assertThat(marketDataSharedChannels(marketData))
        .containsOnlyKeys(BTC_TICKER.toSubscriptionId());

    // the shared stream is evicted with its last subscriber
    first.dispose();
    second.dispose();
    assertThat(service.containsChannel(BTC_TICKER.toSubscriptionId())).isFalse();
    assertThat(marketDataSharedChannels(marketData)).isEmpty();
  }

  @Test
  void sharedChannelEvictsTerminatedStreamSoRetryRebuilds() throws Exception {
    TestableStreamingService service = new TestableStreamingService();

    service.sharedChannel(BTC_TRADE).test().assertError(NotConnectedException.class);
    assertThat(service.sharedChannels()).isEmpty();

    service.open();

    TestObserver<BitgetUtaV3WsNotification> retry = service.sharedChannel(BTC_TRADE).test();
    retry.assertNoErrors();
    assertThat(service.sharedChannels()).containsKey(BTC_TRADE.toSubscriptionId());
    assertThat(service.frames()).anyMatch(frame -> frame.contains("\"subscribe\""));
    retry.dispose();
  }

  @Test
  void marketDataSharedChannelEvictsFailedStreamSoRetryRebuilds() throws Exception {
    TestableStreamingService service = new TestableStreamingService();
    BitgetUtaV3StreamingMarketDataService marketData =
        new BitgetUtaV3StreamingMarketDataService(service);

    marketData.getTicker(CurrencyPair.BTC_USDT).test().assertError(NotConnectedException.class);
    assertThat(marketDataSharedChannels(marketData)).isEmpty();

    service.open();

    TestObserver<Ticker> retry = marketData.getTicker(CurrencyPair.BTC_USDT).test();
    retry.assertNoErrors();
    assertThat(service.frames()).anyMatch(frame -> frame.contains("\"subscribe\""));
    retry.dispose();
  }

  @Test
  void sharedChannelEvictsDisposedStreamSoResubscribeRebuilds() throws Exception {
    TestableStreamingService service = new TestableStreamingService();
    service.open();

    TestObserver<BitgetUtaV3WsNotification> first = service.sharedChannel(BTC_TRADE).test();
    assertThat(service.sharedChannels()).containsKey(BTC_TRADE.toSubscriptionId());
    assertThat(service.containsChannel(BTC_TRADE.toSubscriptionId())).isTrue();

    // a normally disposed shared stream (final subscriber left) must drop its cached observable
    // and its channel registry entry, so dynamic subscriptions cannot grow the caches without
    // bound
    first.dispose();
    assertThat(service.sharedChannels()).doesNotContainKey(BTC_TRADE.toSubscriptionId());
    assertThat(service.containsChannel(BTC_TRADE.toSubscriptionId())).isFalse();

    // and a later subscription rebuilds a working stream from scratch
    TestObserver<BitgetUtaV3WsNotification> second = service.sharedChannel(BTC_TRADE).test();
    second.assertNoErrors();
    assertThat(service.sharedChannels()).containsKey(BTC_TRADE.toSubscriptionId());
    assertThat(service.containsChannel(BTC_TRADE.toSubscriptionId())).isTrue();
    assertThat(service.frames()).anyMatch(frame -> frame.contains("\"subscribe\""));
    second.dispose();
  }

  @Test
  void marketDataSharedChannelEvictsDisposedStreamSoResubscribeRebuilds() throws Exception {
    TestableStreamingService service = new TestableStreamingService();
    BitgetUtaV3StreamingMarketDataService marketData =
        new BitgetUtaV3StreamingMarketDataService(service);
    service.open();

    TestObserver<Ticker> first = marketData.getTicker(CurrencyPair.BTC_USDT).test();
    assertThat(marketDataSharedChannels(marketData))
        .containsKey(BTC_TICKER.toSubscriptionId());
    assertThat(service.containsChannel(BTC_TICKER.toSubscriptionId())).isTrue();

    first.dispose();
    assertThat(marketDataSharedChannels(marketData))
        .doesNotContainKey(BTC_TICKER.toSubscriptionId());
    assertThat(service.containsChannel(BTC_TICKER.toSubscriptionId())).isFalse();

    TestObserver<Ticker> second = marketData.getTicker(CurrencyPair.BTC_USDT).test();
    second.assertNoErrors();
    assertThat(service.frames()).anyMatch(frame -> frame.contains("\"subscribe\""));
    second.dispose();
  }
}
