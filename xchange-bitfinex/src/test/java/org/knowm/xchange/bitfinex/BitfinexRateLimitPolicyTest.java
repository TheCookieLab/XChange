package org.knowm.xchange.bitfinex;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.client.ratelimit.RateLimitBudget;
import org.knowm.xchange.client.ratelimit.RateLimitFeedback;
import org.knowm.xchange.client.ratelimit.RateLimitBudget.ScopeKind;
import org.knowm.xchange.client.ratelimit.RateLimitOperation;
import org.knowm.xchange.client.ratelimit.RateLimitPolicy;
import org.knowm.xchange.client.ratelimit.RateLimitPriority;
import org.knowm.xchange.client.ratelimit.RateLimitRequest;
import si.mazi.rescu.ParamsDigest;

/** Offline checks of the Bitfinex rate-limit policy: classification, costs, scope, enablement. */
class BitfinexRateLimitPolicyTest {

  private static final Class<?>[] INTERFACES = {
    org.knowm.xchange.bitfinex.v1.BitfinexAuthenticated.class,
    org.knowm.xchange.bitfinex.v2.BitfinexAuthenticated.class
  };

  private static final RateLimitPolicy POLICY = BitfinexRateLimitPolicy.defaultPolicy();

  /** Every reachable method, inherited ones included, of both proxied interfaces classifies. */
  @Test
  void everyRescuMethodIsClassified() {
    int checked = 0;
    for (Class<?> iface : INTERFACES) {
      for (Method method : iface.getMethods()) {
        if (httpMethod(method) == null) {
          continue;
        }
        String key = operationKey(iface, method);
        RateLimitOperation operation =
            POLICY.classify(new RateLimitRequest(key, isAuthenticated(method)));
        assertThat(operation).as("%s.%s -> %s", iface.getName(), method.getName(), key).isNotNull();
        assertThat(operation.getRequirements()).isNotEmpty();
        for (Map.Entry<String, Long> cost : operation.getRequirements().entrySet()) {
          assertThat(POLICY.getBudget(cost.getKey())).as(key).isNotNull();
          assertThat(cost.getValue()).as(key).isEqualTo(1L);
        }
        checked++;
      }
    }
    assertThat(checked).isGreaterThanOrEqualTo(60);
  }

  @Test
  void authenticatedMethodsAreExecutionAndPublicOnesMarketData() {
    for (Class<?> iface : INTERFACES) {
      for (Method method : iface.getMethods()) {
        if (httpMethod(method) == null) {
          continue;
        }
        boolean authenticated = isAuthenticated(method);
        RateLimitOperation operation =
            POLICY.classify(new RateLimitRequest(operationKey(iface, method), authenticated));
        assertThat(operation.getPriority())
            .as(operation.getName())
            .isEqualTo(authenticated ? RateLimitPriority.EXECUTION : RateLimitPriority.MARKET_DATA);
        if (!authenticated) {
          assertThat(operation.isReplayOnRateLimit()).as(operation.getName()).isTrue();
        }
      }
    }
  }

  @Test
  void everyBudgetIsAPerIpRollingMinute() {
    assertThat(POLICY.getBudgets()).isNotEmpty();
    for (RateLimitBudget budget : POLICY.getBudgets()) {
      assertThat(budget.getScopeKind()).as(budget.getId()).isEqualTo(ScopeKind.EGRESS);
      assertThat(budget.getLaw()).as(budget.getId()).isEqualTo(RateLimitBudget.Law.ROLLING_WINDOW);
      assertThat(budget.getPeriod()).as(budget.getId()).isEqualTo(Duration.ofMinutes(1));
    }
  }

  @Test
  void documentedV2LimitsAreCarried() {
    assertThat(capacity(BitfinexRateLimitPolicy.V2_PUBLIC_TRADES)).isEqualTo(15);
    assertThat(capacity(BitfinexRateLimitPolicy.V2_STATS)).isEqualTo(15);
    assertThat(capacity(BitfinexRateLimitPolicy.V2_CANDLES)).isEqualTo(30);
    assertThat(capacity(BitfinexRateLimitPolicy.V2_TICKERS)).isEqualTo(30);
    assertThat(capacity(BitfinexRateLimitPolicy.V2_PLATFORM_STATUS)).isEqualTo(30);
    assertThat(capacity(BitfinexRateLimitPolicy.V2_BOOK)).isEqualTo(240);
    assertThat(capacity(BitfinexRateLimitPolicy.V2_CONF)).isEqualTo(90);
    assertThat(capacity(BitfinexRateLimitPolicy.V2_WALLETS)).isEqualTo(90);
    assertThat(capacity(BitfinexRateLimitPolicy.DEPOSIT_ADDRESS)).isEqualTo(10);
  }

  @Test
  void costsFollowTheEndpointFamily() {
    assertThat(requirements("GET v2/trades/{symbol}/hist", false))
        .isEqualTo(Map.of(BitfinexRateLimitPolicy.V2_PUBLIC_TRADES, 1L));
    assertThat(requirements("GET v2/book/{symbol}/{precision}", false))
        .isEqualTo(Map.of(BitfinexRateLimitPolicy.V2_BOOK, 1L));
    assertThat(requirements("GET v2/book/{symbol}/R0", false))
        .isEqualTo(Map.of(BitfinexRateLimitPolicy.V2_BOOK, 1L));
    assertThat(requirements("GET v2/candles/trade:{candlePeriod}:{symbol}/hist", false))
        .isEqualTo(Map.of(BitfinexRateLimitPolicy.V2_CANDLES, 1L));
    assertThat(requirements("POST v2/auth/r/orders/hist", true))
        .isEqualTo(Map.of(BitfinexRateLimitPolicy.V2_ORDERS_HIST, 1L));
    assertThat(requirements("POST v2/auth/r/orders/{symbol}/hist", true))
        .isEqualTo(Map.of(BitfinexRateLimitPolicy.V2_ORDERS_HIST, 1L));
    assertThat(requirements("POST v2/auth/r/orders/{symbol}", true))
        .isEqualTo(Map.of(BitfinexRateLimitPolicy.V2_ORDERS, 1L));
    assertThat(requirements("POST v1/deposit/new", true))
        .isEqualTo(
            Map.of(
                BitfinexRateLimitPolicy.V1_AUTH,
                1L,
                BitfinexRateLimitPolicy.DEPOSIT_ADDRESS,
                1L));
    assertThat(requirements("GET v1/pubticker/{symbol}", false))
        .isEqualTo(Map.of(BitfinexRateLimitPolicy.V1_PUBLIC, 1L));
  }

  @Test
  void onlyPublicReadsAndV2AuthenticatedReadsAreReplayed() {
    for (Class<?> iface : INTERFACES) {
      for (Method method : iface.getMethods()) {
        String http = httpMethod(method);
        if (http == null) {
          continue;
        }
        String key = operationKey(iface, method);
        RateLimitOperation operation =
            POLICY.classify(new RateLimitRequest(key, isAuthenticated(method)));
        boolean v2AuthenticatedRead = key.startsWith("POST v2/auth/r/");
        boolean expected = "GET".equals(http) || v2AuthenticatedRead;
        assertThat(operation.isReplayOnRateLimit()).as(key).isEqualTo(expected);
      }
    }
    for (String mutation :
        new String[] {
          "POST v1/order/new",
          "POST v1/order/new/multi",
          "POST v1/offer/new",
          "POST v1/order/cancel",
          "POST v1/order/cancel/all",
          "POST v1/order/cancel/multi",
          "POST v1/order/cancel/replace",
          "POST v1/offer/cancel",
          "POST v1/withdraw",
          "POST v1/deposit/new",
          "POST v2/auth/w/transfer",
          "POST v2/auth/w/deriv/collateral/set"
        }) {
      assertThat(POLICY.classify(new RateLimitRequest(mutation, true)).isReplayOnRateLimit())
          .as(mutation)
          .isFalse();
    }
  }

  @Test
  void unknownOperationsAreUnclassified() {
    for (String key :
        new String[] {
          "GET v3/anything",
          "GET v1/unknown",
          "POST v1/unknown",
          "DELETE v1/orders",
          "GET v2/unknown",
          "POST v2/auth/r/unknown",
          "POST v2/auth/w/unknown",
          "PUT v2/tickers",
          "GET api/v3/depth",
          "malformed"
        }) {
      assertThat(POLICY.classify(new RateLimitRequest(key, true))).as(key).isNull();
    }
  }

  @Test
  void rejectionStatusIsDeclaredAndBlockIsLongerThanSixtySeconds() {
    assertThat(
            POLICY
                .getFeedbackInterpreter()
                .interpret(429, header -> null, Instant.EPOCH)
                .getKind())
        .isEqualTo(RateLimitFeedback.Kind.RATE_REJECTED);
    assertThat(
            POLICY
                .getFeedbackInterpreter()
                .interpret(200, header -> null, Instant.EPOCH)
                .getKind())
        .isEqualTo(RateLimitFeedback.Kind.NONE);
    assertThat(POLICY.getFallbackBackoffBase()).isGreaterThanOrEqualTo(Duration.ofSeconds(60));
    assertThat(POLICY.maxWait(RateLimitPriority.MARKET_DATA))
        .isGreaterThan(POLICY.getFallbackBackoffBase());
  }

  @Test
  void defaultSpecificationEnablesThePolicy() {
    ExchangeSpecification specification = new BitfinexExchange().getDefaultExchangeSpecification();
    assertThat(specification.getResilience().isRateLimiterEnabled()).isTrue();
    assertThat(specification.getResilience().getRateLimitPolicy())
        .isSameAs(BitfinexRateLimitPolicy.defaultPolicy());
    assertThat(POLICY.getNamespace()).isEqualTo(BitfinexRateLimitPolicy.NAMESPACE);
    assertThat(POLICY.getSource()).contains("https://docs.bitfinex.com/").contains("2026-10-06");
  }

  private static long capacity(String budgetId) {
    return POLICY.getBudget(budgetId).getCapacity();
  }

  private static Map<String, Long> requirements(String key, boolean authenticated) {
    RateLimitOperation operation = POLICY.classify(new RateLimitRequest(key, authenticated));
    assertThat(operation).as(key).isNotNull();
    return operation.getRequirements();
  }

  private static String httpMethod(Method method) {
    if (method.isAnnotationPresent(GET.class)) {
      return "GET";
    }
    if (method.isAnnotationPresent(POST.class)) {
      return "POST";
    }
    return null;
  }

  private static boolean isAuthenticated(Method method) {
    for (Class<?> type : method.getParameterTypes()) {
      if (ParamsDigest.class.isAssignableFrom(type)) {
        return true;
      }
    }
    return false;
  }

  /** {@code "<METHOD> <interface @Path>/<method @Path>"}, empty segments removed. */
  private static String operationKey(Class<?> iface, Method method) {
    List<String> segments = new ArrayList<>();
    for (String path : new String[] {iface.getAnnotation(Path.class).value(), pathOf(method)}) {
      for (String segment : path.split("/")) {
        if (!segment.isEmpty()) {
          segments.add(segment);
        }
      }
    }
    return httpMethod(method) + " " + String.join("/", segments);
  }

  private static String pathOf(Method method) {
    Path path = method.getAnnotation(Path.class);
    return path == null ? "" : path.value();
  }
}
