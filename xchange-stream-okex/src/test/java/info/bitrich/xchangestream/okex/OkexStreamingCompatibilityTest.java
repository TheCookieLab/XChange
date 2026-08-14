package info.bitrich.xchangestream.okex;

import static org.assertj.core.api.Assertions.assertThat;

import info.bitrich.xchangestream.core.StreamingMarketDataService;
import info.bitrich.xchangestream.core.StreamingTradeService;
import info.bitrich.xchangestream.okx.OkxBusinessStreamingService;
import info.bitrich.xchangestream.okx.OkxPrivateStreamingService;
import info.bitrich.xchangestream.okx.OkxStreamingExchange;
import info.bitrich.xchangestream.okx.OkxStreamingMarketDataService;
import info.bitrich.xchangestream.okx.OkxStreamingService;
import info.bitrich.xchangestream.okx.OkxStreamingTradeService;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.okex.OkexExchange;

public class OkexStreamingCompatibilityTest {

  @Test
  public void exchangeIsInstantiableAndServicesAreNullBeforeConnect() {
    OkexStreamingExchange exchange = new OkexStreamingExchange();

    assertThat(exchange).isInstanceOf(OkexStreamingExchange.class);
    assertThat(exchange.getStreamingMarketDataService()).isNull();
    assertThat(exchange.getStreamingTradeService()).isNull();
  }

  @Test
  public void exchangeExtendsCanonicalOkxStreamingExchange() {
    assertThat(OkxStreamingExchange.class.isAssignableFrom(OkexStreamingExchange.class)).isTrue();
    assertThat(OkexStreamingExchange.class.getSuperclass()).isEqualTo(OkxStreamingExchange.class);
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
