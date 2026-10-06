package org.knowm.xchange.bybit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.junit.Test;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.client.ratelimit.RateLimitBudget;
import org.knowm.xchange.client.ratelimit.RateLimitBudget.ScopeKind;
import org.knowm.xchange.client.ratelimit.RateLimitContext;
import org.knowm.xchange.client.ratelimit.RateLimitFeedback;
import org.knowm.xchange.client.ratelimit.RateLimitOperation;
import org.knowm.xchange.client.ratelimit.RateLimitPolicy;
import org.knowm.xchange.client.ratelimit.RateLimitPriority;
import org.knowm.xchange.client.ratelimit.RateLimitRequest;
import si.mazi.rescu.ParamsDigest;

/** Offline checks of the Bybit v5 rate-limit policy: classification, bounds, enablement. */
public class BybitRateLimitPolicyTest {

  private static final Class<?>[] INTERFACES = {Bybit.class, BybitAuthenticated.class};

  private static final RateLimitPolicy POLICY = BybitRateLimitPolicy.defaultPolicy();

  // ---- classification ------------------------------------------------------------------------

  /** Every declared method of every rescu interface is classified, with consistent costs. */
  @Test
  public void everyRescuMethodIsClassified() {
    int checked = 0;
    Set<String> keys = new HashSet<>();
    for (Class<?> iface : INTERFACES) {
      for (Method method : iface.getDeclaredMethods()) {
        if (method.isSynthetic() || httpMethod(method) == null) {
          continue;
        }
        boolean authenticated = isAuthenticated(method);
        String key = operationKey(iface, method);
        RateLimitOperation operation = POLICY.classify(new RateLimitRequest(key, authenticated));
        assertNotNull("unclassified " + iface.getSimpleName() + "." + method.getName() + ": " + key, operation);
        assertEquals(key, operation.getName());
        assertTrue(
            "every request consumes the IP budget: " + key,
            operation.getRequirements().containsKey(BybitRateLimitPolicy.IP));
        assertEquals(
            "IP cost is one unit: " + key,
            Long.valueOf(1L),
            operation.getRequirements().get(BybitRateLimitPolicy.IP));
        if (authenticated) {
          assertEquals(key, RateLimitPriority.EXECUTION, operation.getPriority());
          assertEquals(
              "one IP unit plus one endpoint unit: " + key, 2, operation.getRequirements().size());
        } else {
          assertEquals(key, RateLimitPriority.MARKET_DATA, operation.getPriority());
          assertEquals(
              "public requests only consume the IP budget: " + key,
              Map.of(BybitRateLimitPolicy.IP, 1L),
              operation.getRequirements());
          assertTrue("public reads are replay-safe: " + key, operation.isReplayOnRateLimit());
        }
        for (String budgetId : operation.getRequirements().keySet()) {
          assertNotNull("budget declared for " + key + ": " + budgetId, POLICY.getBudget(budgetId));
        }
        keys.add(key);
        checked++;
      }
    }
    assertTrue("reflection found the rescu methods: " + checked, checked >= 40);
    assertEquals("distinct operation keys classified", 40, keys.size());
  }

  /** Reads replay on a confirmed rate rejection; economic mutations never do. */
  @Test
  public void replayIsAllowedForReadsAndForbiddenForEconomicMutations() {
    for (Class<?> iface : INTERFACES) {
      for (Method method : iface.getDeclaredMethods()) {
        if (httpMethod(method) == null) {
          continue;
        }
        String key = operationKey(iface, method);
        RateLimitOperation operation =
            POLICY.classify(new RateLimitRequest(key, isAuthenticated(method)));
        if ("GET".equals(httpMethod(method))) {
          assertTrue("read replays: " + key, operation.isReplayOnRateLimit());
        }
      }
    }
    String[] neverReplayed = {
      "POST v5/order/create",
      "POST v5/order/amend",
      "POST v5/order/cancel",
      "POST v5/order/cancel-all",
      "POST v5/order/create-batch",
      "POST v5/order/amend-batch",
      "POST v5/order/cancel-batch",
      "POST v5/position/trading-stop",
      "POST v5/position/set-risk-limit",
      "POST v5/position/add-margin",
      "POST v5/position/set-auto-add-margin",
      "POST v5/asset/transfer/inter-transfer"
    };
    for (String key : neverReplayed) {
      RateLimitOperation operation = POLICY.classify(new RateLimitRequest(key, true));
      assertNotNull(key, operation);
      assertFalse("mutation must not replay: " + key, operation.isReplayOnRateLimit());
    }
    String[] idempotent = {
      "POST v5/order/pre-check", "POST v5/position/set-leverage", "POST v5/position/switch-mode"
    };
    for (String key : idempotent) {
      assertTrue(key, POLICY.classify(new RateLimitRequest(key, true)).isReplayOnRateLimit());
    }
  }

  @Test
  public void authenticatedOperationsConsumeTheirOwnUserBudget() {
    RateLimitOperation create = POLICY.classify(new RateLimitRequest("POST v5/order/create", true));
    assertEquals(
        Map.of(BybitRateLimitPolicy.IP, 1L, BybitRateLimitPolicy.ORDER_CREATE, 1L),
        create.getRequirements());
    assertEquals(
        RateLimitBudget.rollingWindow(
            BybitRateLimitPolicy.ORDER_CREATE, ScopeKind.USER, 10, Duration.ofSeconds(1)),
        POLICY.getBudget(BybitRateLimitPolicy.ORDER_CREATE));

    RateLimitOperation realtime =
        POLICY.classify(new RateLimitRequest("GET v5/order/realtime", true));
    assertEquals(
        Map.of(BybitRateLimitPolicy.IP, 1L, BybitRateLimitPolicy.ORDER_REALTIME, 1L),
        realtime.getRequirements());
    assertEquals(
        RateLimitBudget.rollingWindow(
            BybitRateLimitPolicy.ORDER_REALTIME, ScopeKind.USER, 50, Duration.ofSeconds(1)),
        POLICY.getBudget(BybitRateLimitPolicy.ORDER_REALTIME));

    RateLimitOperation transfer =
        POLICY.classify(new RateLimitRequest("POST v5/asset/transfer/inter-transfer", true));
    assertEquals(
        RateLimitBudget.rollingWindow(
            BybitRateLimitPolicy.ASSET_INTER_TRANSFER, ScopeKind.USER, 60, Duration.ofMinutes(1)),
        POLICY.getBudget(BybitRateLimitPolicy.ASSET_INTER_TRANSFER));
    assertTrue(transfer.getRequirements().containsKey(BybitRateLimitPolicy.ASSET_INTER_TRANSFER));

    RateLimitOperation feeRate = POLICY.classify(new RateLimitRequest("GET v5/account/fee-rate", true));
    assertEquals(
        5, POLICY.getBudget(BybitRateLimitPolicy.ACCOUNT_FEE_RATE).getCapacity());
    assertTrue(feeRate.getRequirements().containsKey(BybitRateLimitPolicy.ACCOUNT_FEE_RATE));
  }

  @Test
  public void publicMarketDataIsOnlyIpLimitedAndUsesMarketDataPriority() {
    RateLimitOperation ticker = POLICY.classify(new RateLimitRequest("GET v5/market/tickers", false));
    assertEquals(RateLimitPriority.MARKET_DATA, ticker.getPriority());
    assertEquals(Map.of(BybitRateLimitPolicy.IP, 1L), ticker.getRequirements());
    assertEquals(
        RateLimitBudget.rollingWindow(
            BybitRateLimitPolicy.IP, ScopeKind.EGRESS, 600, Duration.ofSeconds(5)),
        POLICY.getBudget(BybitRateLimitPolicy.IP));
  }

  @Test
  public void unknownOperationsAreUnclassified() {
    String[] keys = {
      "GET v5/unknown/thing",
      "GET",
      "",
      "GET ",
      "TRACE v5/order/create",
      "GET v5/order/create",
      "POST v5/order/realtime",
      "DELETE v5/market/time",
      "GET v5/market/new-endpoint",
      "GET /v5/market/time"
    };
    for (String key : keys) {
      assertNull(key, POLICY.classify(new RateLimitRequest(key, true)));
    }
  }

  // ---- feedback --------------------------------------------------------------------------------

  @Test
  public void status429IsRejectedWithRetryAfter() {
    RateLimitFeedback feedback =
        BybitRateLimitPolicy.interpret(
            429, header("Retry-After", "3"), Instant.ofEpochMilli(1_000_000L));
    assertEquals(RateLimitFeedback.Kind.RATE_REJECTED, feedback.getKind());
    assertEquals(Duration.ofSeconds(3), feedback.getRetryAfter());
  }

  @Test
  public void status429FallsBackToTheResetTimestampHeader() {
    RateLimitFeedback feedback =
        BybitRateLimitPolicy.interpret(
            429,
            header("X-Bapi-Limit-Reset-Timestamp", "1002500"),
            Instant.ofEpochMilli(1_000_000L));
    assertEquals(RateLimitFeedback.Kind.RATE_REJECTED, feedback.getKind());
    assertEquals(Duration.ofMillis(2_500), feedback.getRetryAfter());
  }

  @Test
  public void status429WithoutAnyHintHasNoDelay() {
    RateLimitFeedback feedback =
        BybitRateLimitPolicy.interpret(429, name -> null, Instant.ofEpochMilli(1_000_000L));
    assertEquals(RateLimitFeedback.Kind.RATE_REJECTED, feedback.getKind());
    assertNull(feedback.getRetryAfter());
  }

  @Test
  public void malformedResetTimestampIsIgnored() {
    RateLimitFeedback feedback =
        BybitRateLimitPolicy.interpret(
            429, header("X-Bapi-Limit-Reset-Timestamp", "soon"), Instant.ofEpochMilli(1_000_000L));
    assertEquals(RateLimitFeedback.Kind.RATE_REJECTED, feedback.getKind());
    assertNull(feedback.getRetryAfter());
  }

  @Test
  public void status403IsTheIpBanAndSuccessIsNeutral() {
    assertEquals(
        RateLimitFeedback.Kind.BANNED,
        BybitRateLimitPolicy.interpret(403, name -> null, Instant.EPOCH).getKind());
    assertEquals(
        RateLimitFeedback.Kind.NONE,
        BybitRateLimitPolicy.interpret(200, name -> null, Instant.EPOCH).getKind());
  }

  // ---- bounds and budgets ------------------------------------------------------------------

  @Test
  public void boundsMatchTheDeclaredPolicy() {
    assertEquals(BybitRateLimitPolicy.NAMESPACE, POLICY.getNamespace());
    assertEquals(BybitRateLimitPolicy.VERSION, POLICY.getVersion());
    assertEquals(512, POLICY.pendingLimit(RateLimitPriority.MARKET_DATA));
    assertEquals(64, POLICY.pendingLimit(RateLimitPriority.EXECUTION));
    assertEquals(Duration.ofSeconds(60), POLICY.maxWait(RateLimitPriority.MARKET_DATA));
    assertEquals(Duration.ofSeconds(5), POLICY.maxWait(RateLimitPriority.EXECUTION));
    assertEquals(3, POLICY.getMaxAttempts());
    assertEquals(Duration.ofSeconds(1), POLICY.getFallbackBackoffBase());
    assertEquals(Duration.ofMinutes(10), POLICY.getFallbackBackoffCap());
    assertEquals(0.25, POLICY.getFallbackBackoffJitter(), 0.0);
    assertTrue(POLICY.getSource().contains("https://bybit-exchange.github.io/docs/v5/rate-limit"));
    assertTrue(POLICY.getSource().contains("2026-10-06"));
    assertTrue(POLICY.getSource().contains("Client choices"));
  }

  // ---- enablement ------------------------------------------------------------------------------

  @Test
  public void defaultSpecificationEnablesTheLimiterWithThePolicy() {
    ExchangeSpecification specification = new BybitExchange().getDefaultExchangeSpecification();
    assertTrue(specification.getResilience().isRateLimiterEnabled());
    assertSame(POLICY, specification.getResilience().getRateLimitPolicy());
    specification.setShouldLoadRemoteMetaData(false);

    BybitExchange exchange = new BybitExchange();
    exchange.applySpecification(specification);
    RateLimitContext context = specification.getResilience().getRateLimitContext();
    try {
      assertNotNull("an enabled policy gets an owned context", context);
      assertEquals(
          BybitRateLimitPolicy.VERSION,
          context.diagnostics().getPolicyVersions().get(BybitRateLimitPolicy.NAMESPACE));
    } finally {
      context.close();
    }
  }

  @Test
  public void explicitDisableIsHonoured() {
    ExchangeSpecification specification = new BybitExchange().getDefaultExchangeSpecification();
    specification.getResilience().setRateLimiterEnabled(false);
    specification.setShouldLoadRemoteMetaData(false);

    BybitExchange exchange = new BybitExchange();
    exchange.applySpecification(specification);

    assertFalse(specification.getResilience().isRateLimiterEnabled());
    assertNull(
        "a disabled policy creates no enforcing context",
        specification.getResilience().getRateLimitContext());
  }

  // ---- reflection helpers --------------------------------------------------------------------

  private static Function<String, String> header(String name, String value) {
    return requested -> name.equalsIgnoreCase(requested) ? value : null;
  }

  private static String httpMethod(Method method) {
    if (method.isAnnotationPresent(GET.class)) {
      return "GET";
    }
    if (method.isAnnotationPresent(POST.class)) {
      return "POST";
    }
    if (method.isAnnotationPresent(PUT.class)) {
      return "PUT";
    }
    if (method.isAnnotationPresent(DELETE.class)) {
      return "DELETE";
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

  /** {@code "<METHOD> <interface path>/<method path>"}, no leading slash, slashes collapsed. */
  private static String operationKey(Class<?> iface, Method method) {
    List<String> parts = new ArrayList<>();
    Path ifacePath = iface.getAnnotation(Path.class);
    if (ifacePath != null) {
      parts.add(ifacePath.value());
    }
    Path methodPath = method.getAnnotation(Path.class);
    if (methodPath != null) {
      parts.add(methodPath.value());
    }
    String path = String.join("/", parts).replaceAll("/+", "/").replaceAll("^/", "");
    return httpMethod(method) + " " + path;
  }
}
