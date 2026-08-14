package org.knowm.xchange.okex;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import org.junit.Test;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.okex.dto.OkexException;
import org.knowm.xchange.okex.dto.OkexInstType;
import org.knowm.xchange.okex.dto.OkexResponse;
import org.knowm.xchange.okex.dto.account.OkexAccountPositionRisk;
import org.knowm.xchange.okex.dto.marketdata.OkexTicker;
import org.knowm.xchange.okex.dto.trade.OkexOrderRequest;
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
import org.knowm.xchange.okx.dto.account.OkxAccountPositionRisk;
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
  public void legacyAuthenticatedSurfaceRestoredWithDeprecatedInterfaces() throws Exception {
    assertThat(Okex.class.isInterface()).isTrue();
    assertThat(OkexAuthenticated.class.isInterface()).isTrue();
    assertThat(Okex.class.getAnnotation(Deprecated.class)).isNotNull();
    assertThat(OkexAuthenticated.class.getAnnotation(Deprecated.class)).isNotNull();
    assertThat(Okex.instrumentsPath).isEqualTo("/public/instruments");
    assertThat(OkexAuthenticated.placeOrderPath).isEqualTo("/trade/order");
    assertThat(OkexAuthenticated.privatePathRateLimits.get(OkexAuthenticated.placeOrderPath))
        .isEqualTo(Arrays.asList(60, 2));
    assertThat(
            OkexAuthenticated.class.getMethod(
                "placeOrder",
                String.class,
                si.mazi.rescu.ParamsDigest.class,
                String.class,
                String.class,
                String.class,
                OkexOrderRequest.class))
        .isNotNull();
  }

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
  public void legacyServicesExposeCanonicalDelegatesForRemoteInit() {
    // The shim raws cannot extend the canonical raws (legacy methods clash on return types), so
    // OkexExchange#remoteInit() unwraps the canonical delegates instead. The unwrap casts must
    // succeed on normally initialized services, or initialization throws ClassCastException
    // before any metadata request is made.
    ExchangeSpecification spec = new OkexExchange().getDefaultExchangeSpecification();
    spec.setShouldLoadRemoteMetaData(false);

    OkexExchange exchange = new OkexExchange();
    exchange.applySpecification(spec);

    OkexMarketDataServiceRaw marketDataRaw =
        (OkexMarketDataServiceRaw) exchange.getMarketDataService();
    assertThat(marketDataRaw.getDelegate()).isInstanceOf(OkxMarketDataServiceRaw.class);
    OkexAccountServiceRaw accountRaw = (OkexAccountServiceRaw) exchange.getAccountService();
    assertThat(accountRaw.getDelegate()).isInstanceOf(OkxAccountServiceRaw.class);
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
  public void legacyTickerRemainsNoArgAndJacksonConstructible() throws Exception {
    // The pre-rename OkexTicker exposed a public no-arg constructor and was Jackson-deserializable
    // from the flat ticker payload; compiled legacy clients and reflection-based tooling depend on
    // both.
    assertThat(new OkexTicker()).isNotNull();
    ObjectMapper mapper = new ObjectMapper();
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    OkexTicker ticker =
        mapper.readValue(
            "{\"instId\":\"BTC-USDT\",\"last\":\"1.5\",\"ts\":\"1610000000000\"}",
            OkexTicker.class);
    assertThat(ticker.getInstrumentId()).isEqualTo("BTC-USDT");
    assertThat(ticker.getLast()).isEqualByComparingTo("1.5");
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

  @Test
  public void legacyPositionRiskRetainsNestedDtoNames() {
    // Pre-rename clients reference OkexAccountPositionRisk.BalanceData / .PositionData (nested),
    // so the shim must keep those exact names and the wrapper getters must return them.
    OkxAccountPositionRisk.BalanceData canonicalBalance =
        new OkxAccountPositionRisk.BalanceData(
            Currency.BTC, new BigDecimal("1"), new BigDecimal("2"));
    OkexAccountPositionRisk.BalanceData balance =
        new OkexAccountPositionRisk.BalanceData(canonicalBalance);
    assertThat(balance.getCurrency()).isEqualTo(Currency.BTC);
    assertThat(balance.getEquityOfCurrency()).isEqualByComparingTo("1");
    assertThat(balance.getDiscountEquityOfCurrency()).isEqualByComparingTo("2");

    OkxAccountPositionRisk.PositionData canonicalPosition =
        new OkxAccountPositionRisk.PositionData(
            "BTC-USDT", new BigDecimal("3"), new BigDecimal("4"), "cross", "net");
    OkexAccountPositionRisk.PositionData position =
        new OkexAccountPositionRisk.PositionData(canonicalPosition);
    assertThat(position.getInstrumentId()).isEqualTo("BTC-USDT");
    assertThat(position.getPositionSize()).isEqualByComparingTo("3");

    OkxAccountPositionRisk canonical =
        new OkxAccountPositionRisk(
            null, List.of(canonicalBalance), List.of(canonicalPosition), new Date());
    OkexAccountPositionRisk shim = new OkexAccountPositionRisk(canonical);
    assertThat(shim.getBalanceData()).hasSize(1);
    assertThat(shim.getPositionData()).hasSize(1);
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
