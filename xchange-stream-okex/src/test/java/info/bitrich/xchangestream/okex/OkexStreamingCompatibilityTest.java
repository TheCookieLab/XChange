package info.bitrich.xchangestream.okex;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import info.bitrich.xchangestream.core.StreamingExchange;
import info.bitrich.xchangestream.core.StreamingMarketDataService;
import info.bitrich.xchangestream.core.StreamingTradeService;
import info.bitrich.xchangestream.okex.dto.OkexLoginMessage;
import info.bitrich.xchangestream.okex.dto.OkexSubscribeMessage;
import info.bitrich.xchangestream.okex.dto.OkexSubscriptionTopic;
import info.bitrich.xchangestream.okex.dto.OkexWebsocketPlaceOrderPayload;
import info.bitrich.xchangestream.okx.OkxBusinessStreamingService;
import info.bitrich.xchangestream.okx.OkxPrivateStreamingService;
import info.bitrich.xchangestream.okx.OkxStreamingExchange;
import info.bitrich.xchangestream.okx.OkxStreamingMarketDataService;
import info.bitrich.xchangestream.okx.OkxStreamingService;
import info.bitrich.xchangestream.okx.OkxStreamingTradeService;
import info.bitrich.xchangestream.okx.TransportRole;
import info.bitrich.xchangestream.service.netty.ConnectionStateModel.State;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.client.ResilienceRegistries;
import org.knowm.xchange.dto.meta.ExchangeMetaData;
import org.knowm.xchange.okex.OkexExchange;
import org.knowm.xchange.okex.dto.OkexInstType;

public class OkexStreamingCompatibilityTest {

  @Test
  public void exchangeIsInstantiableAndServicesAreNullBeforeConnect() {
    OkexStreamingExchange exchange = new OkexStreamingExchange();

    assertThat(exchange).isInstanceOf(OkexStreamingExchange.class);
    assertThat(exchange.getStreamingMarketDataService()).isNull();
    assertThat(exchange.getStreamingTradeService()).isNull();
  }

  @Test
  public void exchangeRemainsInLegacyHierarchy() {
    assertThat(OkexExchange.class.isAssignableFrom(OkexStreamingExchange.class)).isTrue();
    assertThat(OkexStreamingExchange.class.getSuperclass()).isEqualTo(OkexExchange.class);
    assertThat(StreamingExchange.class.isAssignableFrom(OkexStreamingExchange.class)).isTrue();

    assertThat(OkexStreamingExchange.WS_PUBLIC_CHANNEL_URI)
        .isEqualTo(OkxStreamingExchange.WS_PUBLIC_CHANNEL_URI);
    assertThat(OkexStreamingExchange.WS_PRIVATE_CHANNEL_URI)
        .isEqualTo(OkxStreamingExchange.WS_PRIVATE_CHANNEL_URI);
    assertThat(OkexStreamingExchange.WS_BUSINESS_CHANNEL_URI)
        .isEqualTo(OkxStreamingExchange.WS_BUSINESS_CHANNEL_URI);
    assertThat(OkexStreamingExchange.SANDBOX_WS_PUBLIC_CHANNEL_URI)
        .isEqualTo(OkxStreamingExchange.SANDBOX_WS_PUBLIC_CHANNEL_URI);
    assertThat(OkexStreamingExchange.SANDBOX_WS_PRIVATE_CHANNEL_URI)
        .isEqualTo(OkxStreamingExchange.SANDBOX_WS_PRIVATE_CHANNEL_URI);
    assertThat(OkexStreamingExchange.SANDBOX_WS_BUSINESS_CHANNEL_URI)
        .isEqualTo(OkxStreamingExchange.SANDBOX_WS_BUSINESS_CHANNEL_URI);
  }

  @Test
  public void exchangeSurfaceStillCoversCanonicalStreamingExchange() {
    assertSurfaceCovered(OkxStreamingExchange.class, OkexStreamingExchange.class);
  }

  @Test
  public void exchangeDelegatesLifecycleCallsBeforeConnect() {
    OkexStreamingExchange exchange = new OkexStreamingExchange();

    assertThat(exchange.isAlive()).isFalse();
    exchange.disconnect().blockingAwait();
    assertThat(exchange.connectionStateObservable().blockingFirst()).isEqualTo(State.CLOSED);
    assertThat(exchange.connectionStateObservablePrivateChannel().blockingFirst())
        .isEqualTo(State.CLOSED);
    assertThat(exchange.connectionStateObservableBusinessChannel().blockingFirst())
        .isEqualTo(State.CLOSED);
    assertThat(exchange.getConnectionGeneration()).isZero();
    assertThat(exchange.getRequiredTransports())
        .containsExactlyInAnyOrder(TransportRole.PUBLIC, TransportRole.BUSINESS);
    exchange.resubscribeChannels();
  }

  @Test
  public void serviceShimsImplementCoreStreamingInterfaces() {
    assertThat(
            StreamingMarketDataService.class.isAssignableFrom(OkexStreamingMarketDataService.class))
        .isTrue();
    assertThat(StreamingTradeService.class.isAssignableFrom(OkexStreamingTradeService.class))
        .isTrue();
  }

  @Test
  public void marketDataServiceSurfaceMatchesCanonical() {
    assertSurfaceCovered(OkxStreamingMarketDataService.class, OkexStreamingMarketDataService.class);
  }

  @Test
  public void tradeServiceSurfaceMatchesCanonical() {
    assertSurfaceCovered(OkxStreamingTradeService.class, OkexStreamingTradeService.class);
  }

  @Test
  public void streamingServiceShimsExtendCanonicalServices() {
    ExchangeSpecification spec = new ExchangeSpecification(OkexStreamingExchange.class);

    OkexStreamingService service =
        new OkexStreamingService("wss://ws.okx.com:8443/ws/v5/public", spec);
    assertThat(service).isInstanceOf(OkxStreamingService.class);
    assertThat(service.getClass().getSuperclass()).isEqualTo(OkxStreamingService.class);

    OkexBusinessStreamingService business =
        new OkexBusinessStreamingService("wss://ws.okx.com:8443/ws/v5/business", spec);
    assertThat(business).isInstanceOf(OkxBusinessStreamingService.class);
    assertThat(business.getClass().getSuperclass()).isEqualTo(OkxBusinessStreamingService.class);

    OkexPrivateStreamingService priv =
        new OkexPrivateStreamingService(
            "wss://ws.okx.com:8443/ws/v5/private", spec, new OkexExchange());
    assertThat(priv).isInstanceOf(OkxPrivateStreamingService.class);
    assertThat(priv.getClass().getSuperclass()).isEqualTo(OkxPrivateStreamingService.class);
  }

  @Test
  public void channelConstantsRemainAccessibleThroughLegacyNames() {
    assertThat(OkexStreamingService.TRADES).isEqualTo(OkxStreamingService.TRADES);
    assertThat(OkexStreamingService.ORDERBOOK_BBO_TBT)
        .isEqualTo(OkxStreamingService.ORDERBOOK_BBO_TBT);
    assertThat(OkexPrivateStreamingService.USER_ORDER_CHANGES)
        .isEqualTo(OkxPrivateStreamingService.USER_ORDER_CHANGES);
    assertThat(OkexPrivateStreamingService.PLACE_ORDER)
        .isEqualTo(OkxPrivateStreamingService.PLACE_ORDER);
    assertThat(OkexPrivateStreamingService.CHANGE_ORDER)
        .isEqualTo(OkxPrivateStreamingService.CHANGE_ORDER);
    assertThat(OkexPrivateStreamingService.CANCEL_ORDER)
        .isEqualTo(OkxPrivateStreamingService.CANCEL_ORDER);
  }

  @Test
  public void legacyFacadeConstructorsRemainAvailable() {
    ExchangeSpecification spec = new ExchangeSpecification(OkexStreamingExchange.class);
    OkexStreamingService service =
        new OkexStreamingService("wss://ws.okx.com:8443/ws/v5/public", spec);
    OkexBusinessStreamingService business =
        new OkexBusinessStreamingService("wss://ws.okx.com:8443/ws/v5/business", spec);
    ExchangeMetaData metaData =
        new ExchangeMetaData(Collections.emptyMap(), Collections.emptyMap(), null, null, null);

    OkexStreamingMarketDataService marketDataService =
        new OkexStreamingMarketDataService(service, business, metaData);
    assertThat(marketDataService).isInstanceOf(StreamingMarketDataService.class);

    OkexPrivateStreamingService priv =
        new OkexPrivateStreamingService(
            "wss://ws.okx.com:8443/ws/v5/private", spec, new OkexExchange());
    OkexStreamingTradeService tradeService =
        new OkexStreamingTradeService(priv, metaData, new ResilienceRegistries());
    assertThat(tradeService).isInstanceOf(StreamingTradeService.class);
  }

  @Test
  public void legacyDtoShimsDeserializeWithPreRenameWireKeys() throws Exception {
    ObjectMapper mapper = new ObjectMapper();

    OkexLoginMessage login = mapper.readValue("{\"op\":\"login\"}", OkexLoginMessage.class);
    assertThat(login.getOp()).isEqualTo("login");
    assertThat(login.getArgs()).isEmpty();
    OkexLoginMessage.LoginArg arg = new OkexLoginMessage.LoginArg("k", "p", "1", "s");
    assertThat(arg.getApiKey()).isEqualTo("k");
    assertThat(arg.getSign()).isEqualTo("s");

    OkexSubscribeMessage<String> subscribe =
        new OkexSubscribeMessage<>("1", "subscribe", Arrays.asList("a"));
    assertThat(subscribe.getOp()).isEqualTo("subscribe");
    assertThat(subscribe.getArgs()).containsExactly("a");

    OkexSubscriptionTopic topic =
        new OkexSubscriptionTopic("tickers", OkexInstType.SPOT, "", "BTC-USDT");
    assertThat(topic.getInstType()).isEqualTo(OkexInstType.SPOT);
    assertThat(topic.getInstId()).isEqualTo("BTC-USDT");
    assertThat(mapper.writeValueAsString(topic))
        .isEqualTo(
            "{\"channel\":\"tickers\",\"instType\":\"SPOT\",\"uly\":\"\",\"instId\":\"BTC-USDT\"}");

    OkexWebsocketPlaceOrderPayload payload =
        mapper.readValue(
            "{\"instId\":\"BTC-USDT\",\"tdMode\":\"cash\",\"side\":\"buy\",\"ordType\":\"limit\","
                + "\"sz\":\"1\",\"px\":\"100\"}",
            OkexWebsocketPlaceOrderPayload.class);
    assertThat(payload.getInstId()).isEqualTo("BTC-USDT");
    assertThat(payload.getTdMode()).isEqualTo("cash");
    assertThat(payload.getSz()).isEqualTo("1");
  }

  private static void assertSurfaceCovered(Class<?> canonical, Class<?> shim) {
    Set<String> shimMethods = new HashSet<>();
    for (Method method : shim.getMethods()) {
      shimMethods.add(methodKey(method));
    }
    for (Method method : canonical.getMethods()) {
      assertThat(shimMethods)
          .as(
              "%s must expose a public method matching %s.%s",
              shim.getName(), canonical.getName(), methodKey(method))
          .contains(methodKey(method));
    }
  }

  private static String methodKey(Method method) {
    return method.getName()
        + "("
        + Arrays.stream(method.getParameterTypes())
            .map(Class::getName)
            .collect(Collectors.joining(", "))
        + ")";
  }
}
