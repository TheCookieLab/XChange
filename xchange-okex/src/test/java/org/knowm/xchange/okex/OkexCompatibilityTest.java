package org.knowm.xchange.okex;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.lang.reflect.Method;
import org.junit.Test;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.okex.dto.OkexException;
import org.knowm.xchange.okex.dto.OkexInstType;
import org.knowm.xchange.okex.dto.OkexResponse;
import org.knowm.xchange.okex.dto.trade.OkexTradeParams;
import org.knowm.xchange.okex.service.OkexAccountService;
import org.knowm.xchange.okex.service.OkexAccountServiceRaw;
import org.knowm.xchange.okex.service.OkexMarketDataService;
import org.knowm.xchange.okex.service.OkexMarketDataServiceRaw;
import org.knowm.xchange.okex.service.OkexTradeService;
import org.knowm.xchange.okex.service.OkexTradeServiceRaw;
import org.knowm.xchange.okx.OkxExchange;
import org.knowm.xchange.okx.dto.OkxException;
import org.knowm.xchange.okx.dto.OkxInstType;
import org.knowm.xchange.okx.dto.OkxResponse;
import org.knowm.xchange.okx.service.OkxAccountServiceRaw;
import org.knowm.xchange.okx.service.OkxMarketDataServiceRaw;
import org.knowm.xchange.okx.service.OkxTradeServiceRaw;
import org.knowm.xchange.service.trade.params.CancelOrderByIdParams;
import org.knowm.xchange.service.trade.params.CancelOrderByInstrument;
import org.knowm.xchange.service.trade.params.CancelOrderByUserReferenceParams;

/**
 * Verifies that the deprecated {@code org.knowm.xchange.okex} compatibility shims expose the old
 * Okex client surface and delegate to the canonical {@code org.knowm.xchange.okx} implementation.
 * All tests are offline.
 */
public class OkexCompatibilityTest {

  @Test
  public void testDefaultExchangeSpecification() {
    ExchangeSpecification spec = new OkexExchange().getDefaultExchangeSpecification();

    assertThat(spec.getExchangeName()).isEqualTo("Okex");
    assertThat(spec.getSslUri()).isEqualTo("https://www.okx.com");
  }

  @Test
  public void testServicesAreOkexShims() {
    ExchangeSpecification spec = new OkexExchange().getDefaultExchangeSpecification();
    spec.setApiKey("api-key");
    spec.setSecretKey("secret-key");
    spec.setExchangeSpecificParametersItem(OkxExchange.PARAM_PASSPHRASE, "passphrase");
    spec.setShouldLoadRemoteMetaData(false);

    OkexExchange exchange = new OkexExchange();
    exchange.applySpecification(spec);

    assertThat(exchange.getMarketDataService()).isInstanceOf(OkexMarketDataService.class);
    assertThat(exchange.getAccountService()).isInstanceOf(OkexAccountService.class);
    assertThat(exchange.getTradeService()).isInstanceOf(OkexTradeService.class);
  }

  @Test
  public void legacyExchangeRetainsMetadataWhenRemoteInitIsDisabled() {
    ExchangeSpecification spec = new OkexExchange().getDefaultExchangeSpecification();
    spec.setShouldLoadRemoteMetaData(false);

    OkexExchange exchange = new OkexExchange();
    exchange.applySpecification(spec);

    assertThat(exchange.getExchangeMetaData()).isNotNull();
  }

  @Test
  public void legacyServicesAreCastableToCanonicalRawTypes() {
    // Precondition for the inherited OkxExchange#remoteInit() casts: normal initialization
    // must not throw ClassCastException before any metadata request is made.
    ExchangeSpecification spec = new OkexExchange().getDefaultExchangeSpecification();
    spec.setShouldLoadRemoteMetaData(false);

    OkexExchange exchange = new OkexExchange();
    exchange.applySpecification(spec);

    assertThat(exchange.getMarketDataService()).isInstanceOf(OkxMarketDataServiceRaw.class);
    assertThat(exchange.getAccountService()).isInstanceOf(OkxAccountServiceRaw.class);
    assertThat(exchange.getTradeService()).isInstanceOf(OkxTradeServiceRaw.class);
  }

  @Test
  public void legacyResponseRetainsFourArgumentConstructor() {
    OkexResponse<String> response = new OkexResponse<>("id-1", "0", "ok", "payload");

    assertThat(response.getId()).isEqualTo("id-1");
    assertThat(response.getCode()).isEqualTo("0");
    assertThat(response.getMsg()).isEqualTo("ok");
    assertThat(response.getData()).isEqualTo("payload");
    assertThat(response.isSuccess()).isTrue();
  }

  @Test
  public void testWrapperConversions() {
    assertThat(OkexInstType.from(OkxInstType.SWAP).to()).isEqualTo(OkxInstType.SWAP);
    assertThat(OkexInstType.from(null)).isNull();

    OkexResponse<String> response = OkexResponse.of(new OkxResponse<>("1", "0", "OK", "data"));
    assertThat(response.getId()).isEqualTo("1");
    assertThat(response.getCode()).isEqualTo("0");
    assertThat(response.getMsg()).isEqualTo("OK");
    assertThat(response.getData()).isEqualTo("data");
    assertThat(response.isSuccess()).isTrue();

    OkexResponse<String> failure = OkexResponse.of(new OkxResponse<>("1", "500", "error", null));
    assertThat(failure.isSuccess()).isFalse();

    OkxException cause = new OkxException("m", 7);
    OkexException shim = new OkexException(cause);
    assertThat(shim).isInstanceOf(OkxException.class);
    assertThat(shim.getCode()).isEqualTo(7);
    assertThat(shim.getMessage()).isEqualTo("m");
  }

  @Test
  public void testRawServiceSurfaceContract() {
    assertRawServiceSurface(OkxMarketDataServiceRaw.class, OkexMarketDataServiceRaw.class);
    assertRawServiceSurface(OkxAccountServiceRaw.class, OkexAccountServiceRaw.class);
    assertRawServiceSurface(OkxTradeServiceRaw.class, OkexTradeServiceRaw.class);
  }

  @Test
  public void testOkexCancelOrderParamsInterfaces() {
    CurrencyPair instrument = new CurrencyPair("BTC/USDT");
    OkexTradeParams.OkexCancelOrderParams params =
        new OkexTradeParams.OkexCancelOrderParams(instrument, "order-123", "user-ref");

    assertThat(params).isInstanceOf(CancelOrderByInstrument.class);
    assertThat(params).isInstanceOf(CancelOrderByIdParams.class);
    assertThat(params).isInstanceOf(CancelOrderByUserReferenceParams.class);
    assertThat(params.instrument).isEqualTo(instrument);
    assertThat(params.orderId).isEqualTo("order-123");
    assertThat(params.userReference).isEqualTo("user-ref");

    OkexTradeParams.OkexCancelOrderParams paramsWithoutRef =
        new OkexTradeParams.OkexCancelOrderParams(instrument, "order-456");
    assertThat(paramsWithoutRef.userReference).isNull();
    assertThat(paramsWithoutRef.orderId).isEqualTo("order-456");
  }

  /**
   * Asserts that every public method declared on the canonical Okx raw service (including inherited
   * publics) has a same-name, same-parameter-type public method on the Okex shim raw service, with
   * {@code okx} parameter types substituted by their {@code okex} counterparts. Where the canonical
   * method returns {@code OkxResponse}, the shim method must return {@code OkexResponse}.
   */
  private static void assertRawServiceSurface(Class<?> canonicalRaw, Class<?> shimRaw) {
    for (Method method : canonicalRaw.getMethods()) {
      if (method.getDeclaringClass() == Object.class) {
        continue;
      }

      Class<?>[] parameterTypes = method.getParameterTypes();
      Class<?>[] shimParameterTypes = new Class<?>[parameterTypes.length];
      for (int i = 0; i < parameterTypes.length; i++) {
        shimParameterTypes[i] = substituteOkxType(parameterTypes[i]);
      }

      String shimMethodName = method.getName().replace("Okx", "Okex");

      Method shimMethod;
      try {
        shimMethod = shimRaw.getMethod(shimMethodName, shimParameterTypes);
      } catch (NoSuchMethodException e) {
        fail(
            "Missing shim method "
                + shimRaw.getSimpleName()
                + "."
                + shimMethodName
                + "("
                + describe(shimParameterTypes)
                + ") for canonical "
                + canonicalRaw.getSimpleName()
                + "."
                + method.getName());
        return;
      }

      if (method.getReturnType() == OkxResponse.class) {
        assertThat(shimMethod.getReturnType())
            .as("Shim return type of %s.%s", shimRaw.getSimpleName(), method.getName())
            .isEqualTo(OkexResponse.class);
      }
    }
  }

  private static Class<?> substituteOkxType(Class<?> type) {
    if (type.getName().startsWith("org.knowm.xchange.okx")) {
      String shimName =
          type.getName()
              .replace("org.knowm.xchange.okx", "org.knowm.xchange.okex")
              .replace("Okx", "Okex");
      try {
        return Class.forName(shimName);
      } catch (ClassNotFoundException e) {
        fail("No shim type found for canonical type " + type.getName());
      }
    }
    return type;
  }

  private static String describe(Class<?>[] types) {
    StringBuilder sb = new StringBuilder();
    for (Class<?> type : types) {
      if (sb.length() > 0) {
        sb.append(", ");
      }
      sb.append(type.getSimpleName());
    }
    return sb.toString();
  }
}
