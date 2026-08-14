package org.knowm.xchange.okex;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import org.junit.Test;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.derivative.FuturesContract;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.okex.dto.OkexException;
import org.knowm.xchange.okex.dto.OkexInstType;
import org.knowm.xchange.okex.dto.OkexResponse;
import org.knowm.xchange.okex.dto.account.OkexAccountConfig;
import org.knowm.xchange.okex.dto.account.OkexAccountPositionRisk;
import org.knowm.xchange.okex.dto.account.OkexAssetBalance;
import org.knowm.xchange.okex.dto.account.OkexBillDetails;
import org.knowm.xchange.okex.dto.account.OkexDepositAddress;
import org.knowm.xchange.okex.dto.account.OkexPosition;
import org.knowm.xchange.okex.dto.account.OkexSetLeverageResponse;
import org.knowm.xchange.okex.dto.account.OkexTradeFee;
import org.knowm.xchange.okex.dto.account.OkexWalletBalance;
import org.knowm.xchange.okex.dto.account.OkexWithdrawalResponse;
import org.knowm.xchange.okex.dto.marketdata.OkexCandleStick;
import org.knowm.xchange.okex.dto.marketdata.OkexCandles;
import org.knowm.xchange.okex.dto.marketdata.OkexCurrency;
import org.knowm.xchange.okex.dto.marketdata.OkexFundingRate;
import org.knowm.xchange.okex.dto.marketdata.OkexInstrument;
import org.knowm.xchange.okex.dto.marketdata.OkexOrderbook;
import org.knowm.xchange.okex.dto.marketdata.OkexPublicOrder;
import org.knowm.xchange.okex.dto.marketdata.OkexTicker;
import org.knowm.xchange.okex.dto.marketdata.OkexTrade;
import org.knowm.xchange.okex.dto.trade.OkexOrderRequest;
import org.knowm.xchange.okex.dto.trade.OkexTradeParams;
import org.knowm.xchange.okex.service.OkexAccountService;
import org.knowm.xchange.okex.service.OkexAccountServiceRaw;
import org.knowm.xchange.okex.service.OkexBaseService;
import org.knowm.xchange.okex.service.OkexCandleStickPeriodType;
import org.knowm.xchange.okex.service.OkexDigest;
import org.knowm.xchange.okex.service.OkexMarketDataService;
import org.knowm.xchange.okex.service.OkexMarketDataServiceRaw;
import org.knowm.xchange.okex.service.OkexTradeService;
import org.knowm.xchange.okex.service.OkexTradeServiceRaw;
import org.knowm.xchange.okx.OkxExchange;
import org.knowm.xchange.okx.dto.OkxException;
import org.knowm.xchange.okx.dto.OkxInstType;
import org.knowm.xchange.okx.dto.OkxResponse;
import org.knowm.xchange.okx.dto.account.OkxAccountPositionRisk;
import org.knowm.xchange.okx.dto.account.OkxTradeFee;
import org.knowm.xchange.okx.dto.account.OkxWalletBalance;
import org.knowm.xchange.okx.service.OkxAccountService;
import org.knowm.xchange.okx.service.OkxAccountServiceRaw;
import org.knowm.xchange.okx.service.OkxCandleStickPeriodType;
import org.knowm.xchange.okx.service.OkxMarketDataService;
import org.knowm.xchange.okx.service.OkxMarketDataServiceRaw;
import org.knowm.xchange.okx.service.OkxTradeService;
import org.knowm.xchange.okx.service.OkxTradeServiceRaw;
import org.knowm.xchange.service.trade.params.CancelOrderByIdParams;
import org.knowm.xchange.service.trade.params.CancelOrderByInstrument;
import org.knowm.xchange.service.trade.params.CancelOrderByUserReferenceParams;
import org.knowm.xchange.service.trade.params.WithdrawFundsParams;

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
  public void legacyCandleStickPeriodTypeDelegatesToCanonicalEnum() {
    assertThat(OkexCandleStickPeriodType.class.getAnnotation(Deprecated.class)).isNotNull();
    assertThat(OkexCandleStickPeriodType.CANDLE_STICK_1M.getFieldValue()).isEqualTo("1m");
    assertThat(OkexCandleStickPeriodType.CANDLE_STICK_2H.getFieldValue()).isEqualTo("2H");
    assertThat(OkexCandleStickPeriodType.CANDLE_STICK_1H.getFieldValue()).isEqualTo("1H");
    // Delegation must mirror the canonical period set (seconds, not minutes).
    assertThat(OkexCandleStickPeriodType.getSupportedPeriodsInSecs())
        .isEqualTo(OkxCandleStickPeriodType.getSupportedPeriodsInSecs());
  }

  @Test
  public void legacyDigestFacadeDelegatesToCanonicalDigest() {
    assertThat(OkexDigest.class.getAnnotation(Deprecated.class)).isNotNull();
    assertThat(OkexDigest.class.getSuperclass())
        .isEqualTo(org.knowm.xchange.okx.service.OkxDigest.class);
    assertThat(OkexDigest.createInstance(null)).isNull();
    assertThat(OkexDigest.createInstance("c2VjcmV0")).isInstanceOf(OkexDigest.class);
  }

  @Test
  public void legacyBaseServiceRemainsExtendable() throws Exception {
    assertThat(OkexBaseService.class.getAnnotation(Deprecated.class)).isNotNull();
    assertThat(OkexBaseService.class.getSuperclass()).isNotNull();

    ExchangeSpecification spec = new OkexExchange().getDefaultExchangeSpecification();
    spec.setApiKey("api-key");
    spec.setSecretKey("secret-key");
    spec.setExchangeSpecificParametersItem(OkxExchange.PARAM_PASSPHRASE, "passphrase");
    spec.setShouldLoadRemoteMetaData(false);

    OkexExchange exchange = new OkexExchange();
    exchange.applySpecification(spec);

    OkexBaseService service =
        new OkexBaseService(exchange, new org.knowm.xchange.client.ResilienceRegistries());
    assertThat(serviceField(service, "signatureCreator")).isInstanceOf(OkexDigest.class);
    assertThat(serviceField(service, "apiKey")).isEqualTo("api-key");
    assertThat(serviceField(service, "passphrase")).isEqualTo("passphrase");
    assertThat(serviceField(service, "okex")).isNotNull();
    assertThat(serviceField(service, "okexAuthenticated")).isNotNull();
  }

  private static Object serviceField(OkexBaseService service, String name) throws Exception {
    java.lang.reflect.Field field = OkexBaseService.class.getDeclaredField(name);
    field.setAccessible(true);
    return field.get(service);
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
  public void legacyTradeRemainsJacksonConstructible() throws Exception {
    // The retained Okex#getTrades REST path must be able to deserialize a trade payload directly
    // as OkexTrade (delegating creator mirroring the OkexOrderDetails repair).
    ObjectMapper mapper = new ObjectMapper();
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    OkexTrade trade =
        mapper.readValue(
            "{\"instId\":\"BTC-USDT\",\"tradeId\":\"t-1\",\"px\":\"50000\","
                + "\"sz\":\"0.001\",\"side\":\"buy\",\"ts\":\"1610000000000\"}",
            OkexTrade.class);
    assertThat(trade.getTradeId()).isEqualTo("t-1");
    assertThat(trade.getPx()).isEqualByComparingTo("50000");
    assertThat(trade.to().getInstId()).isEqualTo("BTC-USDT");
  }

  @Test
  public void legacyAccountConfigRemainsJacksonConstructible() throws Exception {
    // OkexAuthenticated#getAccountConfiguration deserializes directly into OkexAccountConfig.
    ObjectMapper mapper = new ObjectMapper();
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    OkexAccountConfig config =
        mapper.readValue(
            "{\"uid\":\"123\",\"acctLv\":\"1\",\"posMode\":\"net_mode\","
                + "\"autoLoan\":true,\"greeksType\":\"PA\",\"level\":\"Lv1\",\"levelTmp\":\"Lv1\"}",
            OkexAccountConfig.class);
    assertThat(config.getUid()).isEqualTo("123");
    assertThat(config.getAccountLevel()).isEqualTo("1");
    assertThat(config.getAutoLoan()).isTrue();
  }

  @Test
  public void legacyResilienceFacadeDelegates() {
    // The pre-rename public OkexResilience class must stay available for the grace period.
    assertThat(OkexResilience.createRegistries()).isNotNull();
  }

  @Test
  public void legacyCandleDeserializesFromWireArray() throws Exception {
    // OKX candle responses are JSON arrays; Rescu maps them straight into OkexCandleStick.
    ObjectMapper mapper = new ObjectMapper();
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    OkexCandleStick candle =
        mapper.readValue(
            "[\"1597026383085\",\"3901.5\",\"3901.5\",\"3901.5\",\"3901.5\","
                + "\"0.01191779\",\"46.48\",\"0\",\"0\"]",
            OkexCandleStick.class);
    assertThat(candle.getTimestamp()).isEqualTo(1597026383085L);
    assertThat(candle.getOpenPrice()).isEqualTo("3901.5");
    assertThat(candle.getClosePrice()).isEqualTo("3901.5");
    assertThat(candle.getVolume()).isEqualTo("0.01191779");
    assertThat(candle.to()).isNotNull();
  }

  @Test
  public void everyLegacyRestResponseDtoIsJacksonConstructible() throws Exception {
    // Rescu deserializes the legacy REST interfaces' response payloads directly into the Okex*
    // wrappers, so every wrapper reachable as a return type must expose a Jackson creator (a
    // public no-arg constructor, like OkexTicker, or a delegating @JsonCreator constructor).
    for (Class<?> restInterface : new Class<?>[] {Okex.class, OkexAuthenticated.class}) {
      for (java.lang.reflect.Method method : restInterface.getMethods()) {
        for (Class<?> responseType : responseTypesOf(method.getGenericReturnType())) {
          if (!responseType.getName().startsWith("org.knowm.xchange.okex.")) {
            continue;
          }
          boolean noArg = hasPublicNoArgConstructor(responseType);
          boolean jsonCreator =
              Arrays.stream(responseType.getConstructors())
                  .anyMatch(
                      c ->
                          c.isAnnotationPresent(
                              com.fasterxml.jackson.annotation.JsonCreator.class));
          // Jackson also accepts an implicit creator: a constructor whose parameters are all
          // @JsonProperty-annotated (used by the legacy OkxFundingRateHistory wrapper).
          boolean implicitCreator =
              Arrays.stream(responseType.getConstructors())
                  .anyMatch(
                      c ->
                          c.getParameterCount() > 0
                              && Arrays.stream(c.getParameters())
                                  .allMatch(
                                      p ->
                                          p.isAnnotationPresent(
                                              com.fasterxml.jackson.annotation.JsonProperty
                                                  .class)));
          assertThat(noArg || jsonCreator || implicitCreator)
              .as(
                  "legacy REST response type %s must stay Jackson-constructible",
                  responseType.getName())
              .isTrue();
        }
      }
    }
  }

  private static List<Class<?>> responseTypesOf(java.lang.reflect.Type returnType) {
    java.lang.reflect.Type current = returnType;
    // Unwrap OkexResponse<...> and List<...> layers (legacy responses are flat or list-shaped).
    while (current instanceof java.lang.reflect.ParameterizedType) {
      java.lang.reflect.ParameterizedType parameterized =
          (java.lang.reflect.ParameterizedType) current;
      java.lang.reflect.Type[] args = parameterized.getActualTypeArguments();
      if (args.length == 1 && args[0] instanceof Class<?>) {
        return java.util.Collections.singletonList((Class<?>) args[0]);
      }
      if (args.length == 1 && args[0] instanceof java.lang.reflect.ParameterizedType) {
        current = args[0];
        continue;
      }
      break;
    }
    return java.util.Collections.emptyList();
  }

  private static boolean hasPublicNoArgConstructor(Class<?> type) {
    try {
      type.getConstructor();
      return true;
    } catch (NoSuchMethodException e) {
      return false;
    }
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

  @Test
  public void legacyValueConstructorsBuildCanonicalDelegates() {
    OkexFundingRate fundingRate =
        new OkexFundingRate(
            "SWAP",
            "BTC-USDT-SWAP",
            new BigDecimal("0.0001"),
            new BigDecimal("0.0002"),
            new Date(1000),
            new Date(2000));
    assertThat(fundingRate.getInstType()).isEqualTo("SWAP");
    assertThat(fundingRate.getInstId()).isEqualTo("BTC-USDT-SWAP");
    assertThat(fundingRate.getFundingRate()).isEqualByComparingTo("0.0001");
    assertThat(fundingRate.getNextFundingRate()).isEqualByComparingTo("0.0002");
    assertThat(fundingRate.getFundingTime()).isEqualTo(new Date(1000));
    assertThat(fundingRate.getNextFundingTime()).isEqualTo(new Date(2000));

    OkexTrade trade =
        new OkexTrade(
            "t1", "BTC-USDT", new BigDecimal("100"), new BigDecimal("2"), "buy", new Date(3000));
    assertThat(trade.getTradeId()).isEqualTo("t1");
    assertThat(trade.getInstId()).isEqualTo("BTC-USDT");
    assertThat(trade.getPx()).isEqualByComparingTo("100");
    assertThat(trade.getSz()).isEqualByComparingTo("2");
    assertThat(trade.getSide()).isEqualTo("buy");
    assertThat(trade.getTs()).isEqualTo(new Date(3000));

    OkexPublicOrder publicOrder =
        new OkexPublicOrder(new BigDecimal("100"), new BigDecimal("2"), 3, 4);
    assertThat(publicOrder.getPrice()).isEqualByComparingTo("100");
    assertThat(publicOrder.getVolume()).isEqualByComparingTo("2");

    OkexOrderbook orderbook = new OkexOrderbook(List.of(publicOrder), List.of(publicOrder), "1234");
    assertThat(orderbook.getAsks()).hasSize(1);
    assertThat(orderbook.getAsks().get(0).getPrice()).isEqualByComparingTo("100");
    assertThat(orderbook.getBids()).hasSize(1);
    assertThat(orderbook.getBids().get(0).getVolume()).isEqualByComparingTo("2");
    assertThat(orderbook.getTs()).isEqualTo("1234");

    OkexAccountPositionRisk.BalanceData balance =
        new OkexAccountPositionRisk.BalanceData(
            Currency.BTC, new BigDecimal("1"), new BigDecimal("2"));
    OkexAccountPositionRisk.PositionData position =
        new OkexAccountPositionRisk.PositionData(
            "BTC-USDT", new BigDecimal("3"), new BigDecimal("4"), "cross", "net");
    OkexAccountPositionRisk positionRisk =
        new OkexAccountPositionRisk(
            new BigDecimal("5"), List.of(balance), List.of(position), new Date(4000));
    assertThat(positionRisk.getAdjustEquity()).isEqualByComparingTo("5");
    assertThat(positionRisk.getBalanceData()).hasSize(1);
    assertThat(positionRisk.getBalanceData().get(0).getCurrency()).isEqualTo(Currency.BTC);
    assertThat(positionRisk.getBalanceData().get(0).getEquityOfCurrency())
        .isEqualByComparingTo("1");
    assertThat(positionRisk.getPositionData()).hasSize(1);
    assertThat(positionRisk.getPositionData().get(0).getInstrumentId()).isEqualTo("BTC-USDT");
    assertThat(positionRisk.getPositionData().get(0).getPositionSize()).isEqualByComparingTo("3");
    assertThat(positionRisk.getPositionData().get(0).getMgnMode()).isEqualTo("cross");
    assertThat(positionRisk.getPositionData().get(0).getPosSide()).isEqualTo("net");
    assertThat(positionRisk.getTimestamp()).isEqualTo(new Date(4000));
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

  @Test
  public void legacyOkexResponseDeserializesWithJackson() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    OkexResponse<List<String>> response =
        mapper.readValue(
            "{\"id\":\"1\",\"code\":\"0\",\"msg\":\"\",\"data\":[\"a\",\"b\"]}",
            new TypeReference<OkexResponse<List<String>>>() {});

    assertThat(response.getId()).isEqualTo("1");
    assertThat(response.getCode()).isEqualTo("0");
    assertThat(response.getMsg()).isEmpty();
    assertThat(response.getData()).containsExactly("a", "b");
    assertThat(response.isSuccess()).isTrue();
  }

  @Test
  public void legacyOkexInstrumentDeserializesWithJackson() throws Exception {
    // Pre-rename clients deserialize raw instrument payloads into OkexInstrument; the shim must
    // remain Jackson-constructible even though its only constructor takes the canonical DTO.
    ObjectMapper mapper = new ObjectMapper();
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    OkexInstrument instrument =
        mapper.readValue(
            "{\"instType\":\"SPOT\",\"instId\":\"BTC-USDT\",\"state\":\"live\"}",
            OkexInstrument.class);

    assertThat(instrument.getInstrumentType()).isEqualTo("SPOT");
    assertThat(instrument.getInstrumentId()).isEqualTo("BTC-USDT");
    assertThat(instrument.getState()).isEqualTo("live");
  }

  @Test
  public void okexAdaptersFacadeDelegatesTickerAndInstrumentAdapters() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    OkexTicker ticker =
        mapper.readValue(
            "{\"instType\":\"SPOT\",\"instId\":\"BTC-USDT\",\"last\":\"1.5\","
                + "\"ts\":\"1610000000000\"}",
            OkexTicker.class);

    assertThat(OkexAdapters.adaptInstrument(new CurrencyPair("BTC", "USDT"))).isEqualTo("BTC-USDT");
    assertThat(OkexAdapters.adaptOkexInstrumentId("BTC-USDT"))
        .isEqualTo(new CurrencyPair("BTC/USDT"));
    assertThat(OkexAdapters.adaptOkexOrderSideToOrderType("buy")).isEqualTo(OrderType.BID);
    assertThat(OkexAdapters.adaptTradeMode(new CurrencyPair("BTC", "USDT"), "1")).isEqualTo("cash");

    Ticker adaptedTicker = OkexAdapters.adaptTicker(ticker);
    assertThat(adaptedTicker.getInstrument()).isEqualTo(new CurrencyPair("BTC/USDT"));
    assertThat(adaptedTicker.getLast()).isEqualByComparingTo("1.5");
  }

  @Test
  public void legacyCandlesDeserializeWithPreRenameWireKeys() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    OkexCandles candles =
        mapper.readValue(
            "{\"ts\":\"1700000000000\",\"o\":\"1.1\",\"h\":\"1.2\",\"l\":\"1.0\","
                + "\"c\":\"1.15\",\"vol\":\"123.4\",\"confirm\":\"1\"}",
            OkexCandles.class);

    assertThat(candles).isNotNull();
    assertThat(readField(candles, "ts")).isEqualTo("1700000000000");
    assertThat(readField(candles, "openPrice")).isEqualTo("1.1");
    assertThat(readField(candles, "highestPrice")).isEqualTo("1.2");
    assertThat(readField(candles, "lowestPrice")).isEqualTo("1.0");
    assertThat(readField(candles, "closePrice")).isEqualTo("1.15");
    assertThat(readField(candles, "volume")).isEqualTo("123.4");
    assertThat(readField(candles, "confirm")).isEqualTo("1");
  }

  @Test
  public void legacyNestedTypeNamesArePreserved() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    OkxTradeFee.FiatList canonicalFiat =
        mapper.readValue(
            "{\"ccy\":\"ccy\",\"taker\":\"taker\",\"maker\":\"maker\"}",
            OkxTradeFee.FiatList.class);
    OkexTradeFee.FiatList fiatList = new OkexTradeFee.FiatList(canonicalFiat);
    assertThat(fiatList.getCcy()).isEqualTo("ccy");
    assertThat(fiatList.getTaker()).isEqualTo("taker");
    assertThat(fiatList.getMaker()).isEqualTo("maker");

    OkxWalletBalance.Detail canonicalDetail =
        mapper.readValue(
            "{\"ccy\":\"BTC\",\"eq\":\"1.5\",\"cashBal\":\"0.5\"}", OkxWalletBalance.Detail.class);
    OkexWalletBalance.Detail detail = new OkexWalletBalance.Detail(canonicalDetail);
    assertThat(detail.getCurrency()).isEqualTo("BTC");
    assertThat(detail.getEquity()).isEqualTo("1.5");
    assertThat(detail.getCashBalance()).isEqualTo("0.5");
  }

  @Test
  public void legacyWrapperDtosRetainPublicNoArgConstructors() throws Exception {
    for (Class<?> legacyType :
        new Class<?>[] {
          OkexAccountConfig.class,
          OkexAssetBalance.class,
          OkexBillDetails.class,
          OkexCurrency.class,
          OkexDepositAddress.class,
          OkexInstrument.class,
          OkexPosition.class,
          OkexSetLeverageResponse.class,
          OkexTradeFee.class,
          OkexWalletBalance.class,
          OkexWithdrawalResponse.class
        }) {
      Object instance = legacyType.getDeclaredConstructor().newInstance();
      assertThat(instance)
          .as("%s must keep a public no-argument constructor", legacyType.getName())
          .isNotNull();
    }

    // Delegate-backed wrappers must produce a fully wired canonical DTO, not a null delegate.
    assertThat(new OkexInstrument().to()).isNotNull();
    assertThat(new OkexPosition().to()).isNotNull();
    assertThat(new OkexCurrency().to()).isNotNull();
    assertThat(new OkexTradeFee().to()).isNotNull();
    assertThat(new OkexWalletBalance().to()).isNotNull();
  }

  @Test
  public void legacyStandardServicesTranslateCanonicalExceptions() throws Exception {
    // Pre-rename handlers catch OkexException; the canonical delegates throw OkxException. The
    // legacy standard services must translate, or existing catch (OkexException) blocks silently
    // stop matching provider failures.
    OkexExchange exchange = new OkexExchange();
    exchange.applySpecification(exchange.getDefaultExchangeSpecification());

    OkexAccountService account = (OkexAccountService) exchange.getAccountService();
    OkxAccountService canonicalAccount = mock(OkxAccountService.class);
    when(canonicalAccount.withdrawFunds(any()))
        .thenThrow(new OkxException("Insufficient balance", 51008));
    setDelegate(account, canonicalAccount);

    assertThatThrownBy(() -> account.withdrawFunds(mock(WithdrawFundsParams.class)))
        .isInstanceOf(OkexException.class)
        .hasMessageContaining("Insufficient balance");

    OkexMarketDataService marketData = (OkexMarketDataService) exchange.getMarketDataService();
    OkxMarketDataService canonicalMarketData = mock(OkxMarketDataService.class);
    when(canonicalMarketData.getFundingRate(any()))
        .thenThrow(new OkxException("Invalid instrument", 51000));
    setDelegate(marketData, canonicalMarketData);

    assertThatThrownBy(() -> marketData.getFundingRate(new FuturesContract("BTC/USDT/SWAP")))
        .isInstanceOf(OkexException.class)
        .hasMessageContaining("Invalid instrument");

    OkexTradeService trade = (OkexTradeService) exchange.getTradeService();
    OkxTradeService canonicalTrade = mock(OkxTradeService.class);
    when(canonicalTrade.placeMarketOrder(any()))
        .thenThrow(new OkxException("Insufficient balance", 51008));
    setDelegate(trade, canonicalTrade);

    assertThatThrownBy(() -> trade.placeMarketOrder(mock(MarketOrder.class)))
        .isInstanceOf(OkexException.class)
        .hasMessageContaining("Insufficient balance");
  }

  private static void setDelegate(Object service, Object canonicalDelegate) throws Exception {
    Field field = service.getClass().getDeclaredField("delegate");
    field.setAccessible(true);
    field.set(service, canonicalDelegate);
  }

  private static Object readField(Object target, String name) throws Exception {
    java.lang.reflect.Field field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    return field.get(target);
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
