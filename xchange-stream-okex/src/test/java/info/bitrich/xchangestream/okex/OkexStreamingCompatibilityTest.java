package info.bitrich.xchangestream.okex;

import static org.assertj.core.api.Assertions.assertThat;

import info.bitrich.xchangestream.core.StreamingMarketDataService;
import info.bitrich.xchangestream.core.StreamingTradeService;
import info.bitrich.xchangestream.okx.OkxStreamingExchange;
import info.bitrich.xchangestream.okx.OkxStreamingMarketDataService;
import info.bitrich.xchangestream.okx.OkxStreamingTradeService;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;

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
    assertThat(StreamingMarketDataService.class.isAssignableFrom(OkexStreamingMarketDataService.class))
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
