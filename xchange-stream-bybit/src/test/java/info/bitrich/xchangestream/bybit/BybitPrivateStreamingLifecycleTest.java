package info.bitrich.xchangestream.bybit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import info.bitrich.xchangestream.service.netty.NettyStreamingService;
import info.bitrich.xchangestream.service.netty.StreamingObjectMapperHelper;
import io.reactivex.rxjava3.core.Observable;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.bybit.dto.BybitCategory;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.exceptions.ExchangeException;

/**
 * Private streaming lifecycle: reconnection must re-authenticate the user-data socket and
 * resubscribe channels only after the auth ack, and the order-entry socket must fail pending
 * requests instead of replaying them (which would duplicate live orders).
 */
public class BybitPrivateStreamingLifecycleTest {

  private final ObjectMapper mapper = StreamingObjectMapperHelper.getObjectMapper();

  /** Socket-less service recording every outbound message. */
  private static class RecordingUserDataService extends BybitUserDataStreamingService {
    final List<String> sent = new ArrayList<>();

    RecordingUserDataService(String url, ExchangeSpecification spec) {
      super(url, spec);
    }

    @Override
    public void sendMessage(String message) {
      sent.add(message);
    }
  }

  private static class RecordingTradeService extends BybitUserTradeStreamingService {
    final List<String> sent = new ArrayList<>();

    RecordingTradeService(String url, ExchangeSpecification spec) {
      super(url, spec);
    }

    @Override
    public void sendMessage(String message) {
      if (message != null) {
        sent.add(message);
      }
    }
  }

  private ExchangeSpecification spec;

  /** Fakes an open netty channel so subscribeChannel registers and sends without a socket. */
  private static void openSocket(NettyStreamingService<?> service) throws Exception {
    java.lang.reflect.Field channelField =
        NettyStreamingService.class.getDeclaredField("webSocketChannel");
    channelField.setAccessible(true);
    io.netty.channel.Channel channel = org.mockito.Mockito.mock(io.netty.channel.Channel.class);
    org.mockito.Mockito.when(channel.isOpen()).thenReturn(true);
    channelField.set(service, channel);
  }

  @Before
  public void setUp() {
    ExchangeSpecification base = new BybitStreamingExchange().getDefaultExchangeSpecification();
    base.setShouldLoadRemoteMetaData(false);
    // Service constructors read WS timing params; mirror StreamingExchange.applyWebsocketTimeouts.
    base.setExchangeSpecificParametersItem(
        info.bitrich.xchangestream.core.StreamingExchange.WS_CONNECTION_TIMEOUT,
        info.bitrich.xchangestream.service.netty.NettyStreamingService.DEFAULT_CONNECTION_TIMEOUT);
    base.setExchangeSpecificParametersItem(
        info.bitrich.xchangestream.core.StreamingExchange.WS_RETRY_DURATION,
        info.bitrich.xchangestream.service.netty.NettyStreamingService.DEFAULT_RETRY_DURATION);
    base.setExchangeSpecificParametersItem(
        info.bitrich.xchangestream.core.StreamingExchange.WS_IDLE_TIMEOUT,
        info.bitrich.xchangestream.service.netty.NettyStreamingService.DEFAULT_IDLE_TIMEOUT);
    base.setApiKey("api-key");
    base.setSecretKey("secret-key");
    spec = base;
  }

  @Test
  public void userDataReconnectReauthenticatesThenResubscribesAfterAuthAck() throws Exception {
    RecordingUserDataService service =
        new RecordingUserDataService("wss://stream.bybit.com/v5/private", spec);
    openSocket(service);
    service.subscribeChannel("order.spot.BTCUSDT").subscribe();
    service.subscribeChannel("position.linear.BTCUSDT").subscribe();
    assertThat(service.sent).hasSize(2);
    service.sent.clear();

    // Reconnect: base transport calls resubscribeChannels(); private subscriptions are
    // rejected before auth, so only an auth request may leave the socket.
    service.resubscribeChannels();
    assertThat(service.sent).hasSize(1);
    JsonNode auth = mapper.readTree(service.sent.get(0));
    assertThat(auth.get("op").asText()).isEqualTo("auth");
    assertThat(service.isAuthorized()).isFalse();

    // Auth ack: every previously subscribed channel is re-subscribed.
    service.messageHandler("{\"op\":\"auth\",\"success\":true,\"connId\":\"c1\"}");
    assertThat(service.isAuthorized()).isTrue();
    assertThat(service.sent).hasSize(3);
    assertThat(service.sent.get(1)).contains("order.spot.BTCUSDT");
    assertThat(service.sent.get(2)).contains("position.linear.BTCUSDT");
  }

  @Test
  public void userDataReconnectWithoutSubscriptionsStillReauthenticates() throws Exception {
    RecordingUserDataService service =
        new RecordingUserDataService("wss://stream.bybit.com/v5/private", spec);
    openSocket(service);
    service.resubscribeChannels();
    // Server-side auth dies with the connection; a later subscribe must never be sent
    // unauthenticated, so reconnect always re-authenticates even with zero channels.
    assertThat(service.sent).hasSize(1);
    assertThat(service.sent.get(0)).contains("\"op\":\"auth\"");
    assertThat(service.isAuthorized()).isFalse();
  }

  @Test
  public void userDataLoginWithoutCredentialsRejected() throws Exception {
    ExchangeSpecification credentialless = new BybitStreamingExchange().getDefaultExchangeSpecification();
    credentialless.setShouldLoadRemoteMetaData(false);
    credentialless.setExchangeSpecificParametersItem(
        info.bitrich.xchangestream.core.StreamingExchange.WS_CONNECTION_TIMEOUT,
        info.bitrich.xchangestream.service.netty.NettyStreamingService.DEFAULT_CONNECTION_TIMEOUT);
    credentialless.setExchangeSpecificParametersItem(
        info.bitrich.xchangestream.core.StreamingExchange.WS_RETRY_DURATION,
        info.bitrich.xchangestream.service.netty.NettyStreamingService.DEFAULT_RETRY_DURATION);
    credentialless.setExchangeSpecificParametersItem(
        info.bitrich.xchangestream.core.StreamingExchange.WS_IDLE_TIMEOUT,
        info.bitrich.xchangestream.service.netty.NettyStreamingService.DEFAULT_IDLE_TIMEOUT);
    RecordingUserDataService service =
        new RecordingUserDataService("wss://stream.bybit.com/v5/private", credentialless);
    openSocket(service);
    service.subscribeChannel("order.spot.BTCUSDT").subscribe();
    Throwable thrown = catchThrowable(service::resubscribeChannels);
    assertThat(thrown)
        .isInstanceOf(ExchangeException.class)
        .hasMessageContaining("API key and secret are required");
  }

  @Test
  public void tradeReconnectFailsPendingRequestsWithoutReplay() throws Exception {
    RecordingTradeService service =
        new RecordingTradeService("wss://stream.bybit.com/v5/trade", spec);
    openSocket(service);
    service.messageHandler("{\"op\":\"auth\",\"retMsg\":\"OK\",\"connId\":\"c1\"}");
    assertThat(service.isAuthorized()).isTrue();

    MarketOrder order =
        new MarketOrder(Order.OrderType.BID, new java.math.BigDecimal("0.001"), CurrencyPair.BTC_USDT);
    Observable<JsonNode> pending =
        service.subscribeChannel(
            BybitUserTradeStreamingService.ORDER_CREATE, order, "req-1", BybitCategory.LINEAR);
    io.reactivex.rxjava3.observers.TestObserver<JsonNode> observer = pending.test();
    assertThat(service.sent).hasSize(1);
    assertThat(service.sent.get(0)).contains("\"op\":\"order.create\"");

    // Reconnect: the in-flight request must fail explicitly (outcome unknown) and must NOT
    // be replayed — a replay would duplicate the order on the exchange.
    service.resubscribeChannels();
    observer.assertError(
        t -> t instanceof ExchangeException && t.getMessage().contains("outcome unknown"));
    assertThat(service.sent).hasSize(2); // only a fresh auth, no order.create replay
    assertThat(service.sent.get(1)).contains("\"op\":\"auth\"");
    assertThat(service.isAuthorized()).isFalse();

    // A late response from the old connection generation is dropped, not re-subscribed.
    service.messageHandler(
        "{\"op\":\"order.create\",\"connId\":\"c1\",\"reqId\":\"req-1\",\"retCode\":0,\"retMsg\":\"OK\"}");
    assertThat(service.sent).hasSize(2);
  }

  @Test
  public void tradeStaleGenerationResponseDropped() throws Exception {
    RecordingTradeService service =
        new RecordingTradeService("wss://stream.bybit.com/v5/trade", spec);
    openSocket(service);
    service.messageHandler("{\"op\":\"auth\",\"retMsg\":\"OK\",\"connId\":\"c1\"}");

    MarketOrder order =
        new MarketOrder(Order.OrderType.BID, new java.math.BigDecimal("0.001"), CurrencyPair.BTC_USDT);
    io.reactivex.rxjava3.observers.TestObserver<JsonNode> observer =
        service
            .subscribeChannel(
                BybitUserTradeStreamingService.ORDER_CREATE, order, "req-2", BybitCategory.LINEAR)
            .test();
    service.sent.clear();

    // Response carrying a different connection id (stale generation) must be ignored.
    service.messageHandler(
        "{\"op\":\"order.create\",\"connId\":\"c2\",\"reqId\":\"req-2\",\"retCode\":0,\"retMsg\":\"OK\"}");
    observer.assertNoValues().assertNotComplete();

    // Response of the current generation satisfies the request.
    service.messageHandler(
        "{\"op\":\"order.create\",\"connId\":\"c1\",\"reqId\":\"req-2\",\"retCode\":0,\"retMsg\":\"OK\"}");
    observer.assertValueCount(1);
    assertThat(observer.values().get(0).get("retCode").asInt()).isZero();
  }
}
