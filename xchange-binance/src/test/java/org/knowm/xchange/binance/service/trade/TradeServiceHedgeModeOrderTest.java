package org.knowm.xchange.binance.service.trade;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.notMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.binance.AbstractResilienceTest;
import org.knowm.xchange.binance.BinanceExchange;
import org.knowm.xchange.binance.BinanceFuturesAuthenticated;
import org.knowm.xchange.binance.config.BinanceConfiguration;
import org.knowm.xchange.binance.config.BinanceProductFamily;
import org.knowm.xchange.binance.dto.trade.BinanceOrderFlags;
import org.knowm.xchange.binance.dto.trade.OrderSide;
import org.knowm.xchange.binance.dto.trade.OrderType;
import org.knowm.xchange.binance.dto.trade.TimeInForce;
import org.knowm.xchange.binance.service.BinanceTradeService;
import org.knowm.xchange.derivative.FuturesContract;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.OrderHedgeMode;
import si.mazi.rescu.RestProxyFactory;

/**
 * Regression tests for order placement in Binance hedge (dual-side) mode. Binance rejects requests
 * that send {@code reduceOnly} together with {@code positionSide} ("Cannot send this parameter in
 * Hedge Mode"), so in hedge mode the request must carry {@code positionSide} only, while one-way
 * mode must keep translating exit orders into {@code reduceOnly}.
 *
 * <p>Exercise each product-family endpoint through the service so the tests cover both order
 * adaptation and the actual encoded wire parameters.
 */
class TradeServiceHedgeModeOrderTest extends AbstractResilienceTest {

  private static final String NEW_ORDER_RESPONSE = "{\"orderId\":\"42\"}";

  static Stream<Arguments> futuresEndpoints() {
    return Stream.of(
        Arguments.of(BinanceProductFamily.USDM, "BTC/USDT/PERP", "BTCUSDT", "/fapi/v1/order"),
        Arguments.of(BinanceProductFamily.COINM, "BTC/USD/PERP", "BTCUSD_PERP", "/dapi/v1/order"),
        Arguments.of(
            BinanceProductFamily.PORTFOLIO_MARGIN,
            "BTC/USDT/PERP",
            "BTCUSDT",
            "/papi/v1/um/order"),
        Arguments.of(
            BinanceProductFamily.PORTFOLIO_MARGIN,
            "BTC/USD/PERP",
            "BTCUSD_PERP",
            "/papi/v1/cm/order"));
  }

  @ParameterizedTest
  @MethodSource("futuresEndpoints")
  void hedgeModeSendsPositionSideWithoutReduceOnly(
      BinanceProductFamily family, String contract, String symbol, String path) throws Exception {
    BinanceTradeService service = createFuturesExchange(family);
    stubNewFuturesOrder(path);
    Order.OrderType[] types = {
      Order.OrderType.ASK, Order.OrderType.BID, Order.OrderType.EXIT_ASK, Order.OrderType.EXIT_BID
    };
    String[] sides = {"SELL", "BUY", "BUY", "SELL"};
    String[] positions = {"SHORT", "LONG", "SHORT", "LONG"};

    for (int i = 0; i < types.length; i++) {
      LimitOrder order =
          new LimitOrder.Builder(types[i], new FuturesContract(contract))
              .originalAmount(new BigDecimal("1"))
              .limitPrice(new BigDecimal("30000"))
              .flag(OrderHedgeMode.TWOWAY)
              .flag(BinanceOrderFlags.REDUCE_ONLY)
              .build();

      assertThat(service.placeLimitOrder(order)).isEqualTo("42");
      verify(
          postRequestedFor(urlPathEqualTo(path))
              .withRequestBody(containing("symbol=" + symbol))
              .withRequestBody(containing("side=" + sides[i]))
              .withRequestBody(containing("positionSide=" + positions[i]))
              .withRequestBody(notMatching(".*reduceOnly.*")));
    }
  }

  @ParameterizedTest
  @MethodSource("futuresEndpoints")
  void oneWayModeSendsReduceOnlyWithoutPositionSide(
      BinanceProductFamily family, String contract, String symbol, String path) throws Exception {
    BinanceTradeService service = createFuturesExchange(family);
    stubNewFuturesOrder(path);
    Order.OrderType[] types = {
      Order.OrderType.ASK, Order.OrderType.BID, Order.OrderType.EXIT_ASK, Order.OrderType.EXIT_BID
    };
    String[] sides = {"SELL", "BUY", "BUY", "SELL"};

    for (int i = 0; i < types.length; i++) {
      LimitOrder order =
          new LimitOrder.Builder(types[i], new FuturesContract(contract))
              .originalAmount(new BigDecimal("1"))
              .limitPrice(new BigDecimal("30000"))
              .build();

      assertThat(service.placeLimitOrder(order)).isEqualTo("42");
      verify(
          postRequestedFor(urlPathEqualTo(path))
              .withRequestBody(containing("symbol=" + symbol))
              .withRequestBody(containing("side=" + sides[i]))
              .withRequestBody(containing("reduceOnly=" + (i >= 2)))
              .withRequestBody(notMatching(".*positionSide.*")));
    }
  }

  @ParameterizedTest
  @MethodSource("futuresEndpoints")
  void oneWayModeHonorsExplicitReduceOnly(
      BinanceProductFamily family, String contract, String symbol, String path) throws Exception {
    BinanceTradeService service = createFuturesExchange(family);
    stubNewFuturesOrder(path);
    LimitOrder order =
        new LimitOrder.Builder(Order.OrderType.BID, new FuturesContract(contract))
            .originalAmount(new BigDecimal("1"))
            .limitPrice(new BigDecimal("30000"))
            .flag(BinanceOrderFlags.REDUCE_ONLY)
            .build();

    assertThat(service.placeLimitOrder(order)).isEqualTo("42");
    verify(
        postRequestedFor(urlPathEqualTo(path))
            .withRequestBody(containing("symbol=" + symbol))
            .withRequestBody(containing("reduceOnly=true"))
            .withRequestBody(notMatching(".*positionSide.*")));
  }

  @SuppressWarnings("deprecation")
  @ParameterizedTest
  @MethodSource("futuresEndpoints")
  void legacyFacadeOrderSignaturesRemainUsable(
      BinanceProductFamily family, String contract, String symbol, String path) throws Exception {
    stubNewFuturesOrder(path);
    BinanceFuturesAuthenticated facade =
        RestProxyFactory.createProxy(
            BinanceFuturesAuthenticated.class, "http://localhost:" + wireMockRule.getPort());

    switch (path) {
      case "/fapi/v1/order":
        facade.newOrder(
            symbol,
            OrderSide.BUY,
            OrderType.LIMIT,
            TimeInForce.GTC,
            BigDecimal.ONE,
            true,
            BigDecimal.TEN,
            null,
            null,
            false,
            null,
            null,
            null,
            null,
            null,
            null,
            null);
        break;
      case "/dapi/v1/order":
        facade.newInverseOrder(
            symbol,
            OrderSide.BUY,
            OrderType.LIMIT,
            TimeInForce.GTC,
            BigDecimal.ONE,
            true,
            BigDecimal.TEN,
            null,
            null,
            false,
            null,
            null,
            null,
            null,
            null,
            null,
            null);
        break;
      case "/papi/v1/um/order":
        facade.newPortfolioMarginLinearOrder(
            symbol,
            OrderSide.BUY,
            OrderType.LIMIT,
            TimeInForce.GTC,
            BigDecimal.ONE,
            true,
            BigDecimal.TEN,
            null,
            null,
            null,
            null,
            null,
            null);
        break;
      case "/papi/v1/cm/order":
        facade.newPortfolioMarginInverseOrder(
            symbol,
            OrderSide.BUY,
            OrderType.LIMIT,
            TimeInForce.GTC,
            BigDecimal.ONE,
            true,
            BigDecimal.TEN,
            null,
            null,
            null,
            null,
            null,
            null);
        break;
      default:
        throw new AssertionError("Unexpected endpoint: " + path);
    }

    verify(
        postRequestedFor(urlPathEqualTo(path))
            .withRequestBody(containing("symbol=" + symbol))
            .withRequestBody(containing("reduceOnly=true"))
            .withRequestBody(notMatching(".*positionSide.*")));
  }

  private void stubNewFuturesOrder(String path) {
    stubFor(
        post(urlPathEqualTo(path))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(NEW_ORDER_RESPONSE)));
  }

  private BinanceTradeService createFuturesExchange(BinanceProductFamily family) {
    BinanceExchange exchange = new BinanceExchange();
    ExchangeSpecification specification = exchange.getDefaultExchangeSpecification();
    specification.setHost("localhost");
    specification.setPort(wireMockRule.getPort());
    specification.setShouldLoadRemoteMetaData(false);
    specification.setHttpReadTimeout(READ_TIMEOUT_MS);
    specification.setExchangeSpecificParametersItem(BinanceConfiguration.PRODUCT_FAMILY, family);
    specification.setExchangeSpecificParametersItem(
        BinanceConfiguration.REST_BASE_URL, "http://localhost:" + wireMockRule.getPort() + "/");
    specification.setApiKey("apiKey");
    specification.setSecretKey("secretKey");
    specification.getResilience().setRetryEnabled(false);
    specification.getResilience().setRateLimiterEnabled(false);
    exchange.applySpecification(specification);
    return (BinanceTradeService) exchange.getTradeService();
  }
}
