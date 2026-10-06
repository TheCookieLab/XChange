package org.knowm.xchange.binance;

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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.Test;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.binance.coinm.BinanceCoinmAuthApi;
import org.knowm.xchange.binance.portfoliomargin.BinancePortfolioMarginApi;
import org.knowm.xchange.binance.spot.BinanceSpotApi;
import org.knowm.xchange.binance.spot.BinanceSpotAuthApi;
import org.knowm.xchange.binance.usdm.BinanceUsdmApi;
import org.knowm.xchange.binance.usdm.BinanceUsdmAuthApi;
import org.knowm.xchange.binance.wallet.BinanceWalletApi;
import org.knowm.xchange.client.ratelimit.RateLimitBudget.ScopeKind;
import org.knowm.xchange.client.ratelimit.RateLimitOperation;
import org.knowm.xchange.client.ratelimit.RateLimitPolicy;
import org.knowm.xchange.client.ratelimit.RateLimitPriority;
import org.knowm.xchange.client.ratelimit.RateLimitRequest;
import si.mazi.rescu.ParamsDigest;

/** Offline checks of the Binance rate-limit policy: classification, weights, scopes, enablement. */
public class BinanceRateLimitPolicyTest {

  private static final Class<?>[] INTERFACES = {
    Binance.class,
    BinanceAuthenticated.class,
    BinanceFutures.class,
    BinanceFuturesAuthenticated.class,
    BinanceSpotApi.class,
    BinanceSpotAuthApi.class,
    BinanceWalletApi.class,
    BinanceUsdmApi.class,
    BinanceUsdmAuthApi.class,
    BinanceCoinmAuthApi.class,
    BinancePortfolioMarginApi.class
  };

  private static final RateLimitPolicy POLICY = BinanceRateLimitPolicy.defaultPolicy();
  private static final String SPOT_WEIGHT = "binance.spot.weight";
  private static final String SPOT_RAW = "binance.spot.raw";
  private static final String USDM_WEIGHT = "binance.usdm.weight";

  // ---- classification ------------------------------------------------------------------------

  /** Every REST method of every rescu interface classifies, for the real request shape. */
  @Test
  public void everyRestMethodOfEveryInterfaceIsClassified() {
    Map<String, Boolean> keys = new TreeMap<>();
    for (Class<?> iface : INTERFACES) {
      for (Method method : iface.getMethods()) {
        if (method.isSynthetic() || httpMethod(method) == null) {
          continue;
        }
        keys.put(operationKey(iface, method), isAuthenticated(method));
      }
    }
    assertTrue("reflection found the interface methods: " + keys.size(), keys.size() >= 70);
    for (Map.Entry<String, Boolean> entry : keys.entrySet()) {
      RateLimitOperation operation =
          POLICY.classify(new RateLimitRequest(entry.getKey(), entry.getValue()));
      assertNotNull("unclassified: " + entry.getKey(), operation);
      assertFalse("no free operation: " + entry.getKey(), operation.getRequirements().isEmpty());
      assertTrue(
          "every cost refers to a declared budget: " + entry.getKey(),
          operation.getRequirements().keySet().stream()
              .allMatch(id -> POLICY.getBudget(id) != null));
      boolean mutation = isPlacementOrModification(entry.getKey());
      assertEquals(
          "replay only for idempotent operations: " + entry.getKey(),
          !mutation,
          operation.isReplayOnRateLimit());
    }
  }

  @Test
  public void everyRestMethodOfEveryInterfaceClassifiesWithBothQuerySets() {
    for (Class<?> iface : INTERFACES) {
      for (Method method : iface.getMethods()) {
        if (httpMethod(method) == null) {
          continue;
        }
        String key = operationKey(iface, method);
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("symbol", "BTCUSDT");
        parameters.put("limit", "5000");
        parameters.put("orderId", "1");
        assertNotNull(
            key, POLICY.classify(new RateLimitRequest(key, isAuthenticated(method), parameters)));
        assertNotNull(key, POLICY.classify(new RateLimitRequest(key, isAuthenticated(method))));
      }
    }
  }

  @Test
  public void placementModificationAndWithdrawalAreNeverReplayed() {
    String[] keys = {
      "POST api/v3/order",
      "POST fapi/v1/order",
      "PUT fapi/v1/order",
      "POST dapi/v1/order",
      "POST papi/v1/um/order",
      "POST papi/v1/cm/order",
      "POST sapi/v1/capital/withdraw/apply"
    };
    for (String key : keys) {
      RateLimitOperation operation = POLICY.classify(new RateLimitRequest(key, true));
      assertNotNull(key, operation);
      assertEquals(key, RateLimitPriority.EXECUTION, operation.getPriority());
      assertFalse("never blind-replayed: " + key, operation.isReplayOnRateLimit());
    }
  }

  @Test
  public void cancellationsAndReadsMayBeReplayed() {
    String[] keys = {
      "DELETE api/v3/order",
      "DELETE api/v3/openOrders",
      "GET api/v3/order",
      "GET api/v3/account",
      "DELETE fapi/v1/order",
      "DELETE fapi/v1/allOpenOrders",
      "GET fapi/v1/depth",
      "DELETE dapi/v1/order",
      "DELETE papi/v1/um/order"
    };
    for (String key : keys) {
      RateLimitOperation operation = POLICY.classify(new RateLimitRequest(key, true));
      assertNotNull(key, operation);
      assertTrue(key, operation.isReplayOnRateLimit());
    }
  }

  // ---- weights ---------------------------------------------------------------------------------

  @Test
  public void spotDepthWeightGrowsWithTheLimitParameter() {
    assertEquals(5L, spotWeight("GET api/v3/depth", false, Map.of()));
    assertEquals(5L, spotWeight("GET api/v3/depth", false, Map.of("limit", "100")));
    assertEquals(25L, spotWeight("GET api/v3/depth", false, Map.of("limit", "101")));
    assertEquals(25L, spotWeight("GET api/v3/depth", false, Map.of("limit", "500")));
    assertEquals(50L, spotWeight("GET api/v3/depth", false, Map.of("limit", "1000")));
    assertEquals(250L, spotWeight("GET api/v3/depth", false, Map.of("limit", "5000")));
  }

  @Test
  public void futuresDepthAndKlinesWeightGrowWithTheLimitParameter() {
    assertEquals(2L, usdmWeight("GET fapi/v1/depth", Map.of("limit", "50")));
    assertEquals(5L, usdmWeight("GET fapi/v1/depth", Map.of("limit", "100")));
    assertEquals(10L, usdmWeight("GET fapi/v1/depth", Map.of("limit", "500")));
    assertEquals(20L, usdmWeight("GET fapi/v1/depth", Map.of("limit", "1000")));
    assertEquals(1L, usdmWeight("GET fapi/v1/klines", Map.of("limit", "99")));
    assertEquals(2L, usdmWeight("GET fapi/v1/klines", Map.of("limit", "499")));
    assertEquals(5L, usdmWeight("GET fapi/v1/klines", Map.of("limit", "1000")));
    assertEquals(10L, usdmWeight("GET fapi/v1/klines", Map.of("limit", "1500")));
  }

  @Test
  public void symbolScopedReadsAreCheaperThanAllSymbolReads() {
    assertEquals(2L, spotWeight("GET api/v3/ticker/24hr", false, Map.of("symbol", "BTCUSDT")));
    assertEquals(80L, spotWeight("GET api/v3/ticker/24hr", false, Map.of()));
    assertEquals(6L, spotWeight("GET api/v3/openOrders", true, Map.of("symbol", "BTCUSDT")));
    assertEquals(80L, spotWeight("GET api/v3/openOrders", true, Map.of()));
    assertEquals(1L, usdmWeight("GET fapi/v1/ticker/24hr", Map.of("symbol", "BTCUSDT")));
    assertEquals(40L, usdmWeight("GET fapi/v1/ticker/24hr", Map.of()));
    assertEquals(1L, usdmWeight("GET fapi/v1/openOrders", Map.of("symbol", "BTCUSDT")));
    assertEquals(40L, usdmWeight("GET fapi/v1/openOrders", Map.of()));
    assertEquals(5L, spotWeight("GET api/v3/myTrades", true, Map.of("orderId", "7")));
    assertEquals(20L, spotWeight("GET api/v3/myTrades", true, Map.of()));
  }

  @Test
  public void cheapAndExpensiveFixedWeights() {
    assertEquals(1L, spotWeight("GET api/v3/ping", false, Map.of()));
    assertEquals(20L, spotWeight("GET api/v3/exchangeInfo", false, Map.of()));
    assertEquals(4L, spotWeight("GET api/v3/aggTrades", false, Map.of()));
    assertEquals(20L, spotWeight("GET api/v3/account", true, Map.of()));
    assertEquals(20L, usdmWeight("GET fapi/v1/aggTrades", Map.of()));
    assertEquals(5L, usdmWeight("GET fapi/v2/account", Map.of()));
  }

  @Test
  public void spotOrderPlacementConsumesOrderCountBudgetsAndRawRequests() {
    RateLimitOperation order = POLICY.classify(new RateLimitRequest("POST api/v3/order", true));
    assertEquals(1L, (long) order.getRequirements().get("binance.spot.orders.10s"));
    assertEquals(1L, (long) order.getRequirements().get("binance.spot.orders.12h"));
    assertEquals(1L, (long) order.getRequirements().get(SPOT_RAW));
    assertEquals(1L, (long) order.getRequirements().get(SPOT_WEIGHT));
    assertEquals(ScopeKind.USER, POLICY.getBudget("binance.spot.orders.10s").getScopeKind());
  }

  @Test
  public void futuresOrderPlacementConsumesOrderCountButNoIpWeight() {
    RateLimitOperation order = POLICY.classify(new RateLimitRequest("POST fapi/v1/order", true));
    assertFalse(order.getRequirements().containsKey(USDM_WEIGHT));
    assertEquals(1L, (long) order.getRequirements().get("binance.usdm.orders.1m"));
    assertEquals(1L, (long) order.getRequirements().get("binance.usdm.orders.10s"));
    RateLimitOperation coinm = POLICY.classify(new RateLimitRequest("POST dapi/v1/order", true));
    assertEquals(Map.of("binance.coinm.orders.1m", 1L), coinm.getRequirements());
  }

  @Test
  public void fundingEndpointsShareOneBudget() {
    RateLimitOperation rate = POLICY.classify(new RateLimitRequest("GET fapi/v1/fundingRate", false));
    RateLimitOperation info = POLICY.classify(new RateLimitRequest("GET fapi/v1/fundingInfo", false));
    assertEquals(Map.of("binance.usdm.funding", 1L), rate.getRequirements());
    assertEquals(rate.getRequirements(), info.getRequirements());
  }

  @Test
  public void walletEndpointsOwnIndependentBudgets() {
    RateLimitOperation config =
        POLICY.classify(new RateLimitRequest("GET sapi/v1/capital/config/getall", true));
    RateLimitOperation tradeFee =
        POLICY.classify(new RateLimitRequest("GET sapi/v1/asset/tradeFee", true));
    assertEquals(1, config.getRequirements().size());
    assertEquals(1, tradeFee.getRequirements().size());
    assertFalse(
        config.getRequirements().keySet().iterator().next()
            .equals(tradeFee.getRequirements().keySet().iterator().next()));
    assertEquals(10L, (long) config.getRequirements().values().iterator().next());
    RateLimitOperation withdraw =
        POLICY.classify(new RateLimitRequest("POST sapi/v1/capital/withdraw/apply", true));
    String withdrawBudget = withdraw.getRequirements().keySet().iterator().next();
    assertEquals(ScopeKind.USER, POLICY.getBudget(withdrawBudget).getScopeKind());
    assertEquals(900L, (long) withdraw.getRequirements().get(withdrawBudget));
  }

  // ---- scope and priority ---------------------------------------------------------------------

  @Test
  public void publicMarketDataIsMarketDataPriorityAndEgressScoped() {
    for (String key :
        new String[] {"GET api/v3/depth", "GET api/v3/ticker/24hr", "GET fapi/v1/depth"}) {
      RateLimitOperation operation = POLICY.classify(new RateLimitRequest(key, false));
      assertEquals(key, RateLimitPriority.MARKET_DATA, operation.getPriority());
      for (String budgetId : operation.getRequirements().keySet()) {
        assertEquals(key, ScopeKind.EGRESS, POLICY.getBudget(budgetId).getScopeKind());
      }
    }
  }

  @Test
  public void accountAndOrderOperationsAreExecutionPriority() {
    for (String key :
        new String[] {
          "GET api/v3/account", "POST api/v3/order", "GET api/v3/openOrders", "GET fapi/v2/account"
        }) {
      assertEquals(
          key,
          RateLimitPriority.EXECUTION,
          POLICY.classify(new RateLimitRequest(key, true)).getPriority());
    }
  }

  // ---- structure ---------------------------------------------------------------------------------

  @Test
  public void concreteAndTemplatedKeysClassifyAlike() {
    RateLimitOperation plain = POLICY.classify(new RateLimitRequest("GET api/v3/depth", false));
    RateLimitOperation slashed = POLICY.classify(new RateLimitRequest("GET /api/v3/depth", false));
    RateLimitOperation query =
        POLICY.classify(new RateLimitRequest("GET api/v3/depth?symbol={symbol}", false));
    assertEquals(plain.getRequirements(), slashed.getRequirements());
    assertEquals(plain.getRequirements(), query.getRequirements());
  }

  @Test
  public void unknownOperationsAreUnclassified() {
    for (String key :
        new String[] {
          "GET api/v3/unknown", "", "GET", "TRACE api/v3/depth", "POST api/v3/depth", "GET fapi/v1/x"
        }) {
      assertNull(key, POLICY.classify(new RateLimitRequest(key, true)));
    }
  }

  @Test
  public void usPolicyUsesItsOwnNamespaceWithTheSameWeights() {
    RateLimitPolicy us = BinanceRateLimitPolicy.usPolicy();
    assertEquals("binanceus", us.getNamespace());
    assertEquals("binance", POLICY.getNamespace());
    RateLimitOperation operation =
        us.classify(new RateLimitRequest("GET api/v3/depth", false, Map.of("limit", "5000")));
    assertEquals(Map.of("binanceus.spot.weight", 250L, "binanceus.spot.raw", 1L), operation.getRequirements());
    for (String budgetId : operation.getRequirements().keySet()) {
      assertNotNull(us.getBudget(budgetId));
    }
  }

  @Test
  public void sourceRecordsProviderDocumentsAndProvenance() {
    String source = POLICY.getSource();
    assertTrue(source.contains("2026-10-06"));
    assertTrue(source.contains("https://developers.binance.com/"));
    assertTrue(source.contains("preserved"));
  }

  // ---- enablement ---------------------------------------------------------------------------------

  @Test
  public void defaultSpecificationsEnableTheLimiterWithTheirPolicy() {
    ExchangeSpecification binance = new BinanceExchange().getDefaultExchangeSpecification();
    assertTrue(binance.getResilience().isRateLimiterEnabled());
    assertSame(POLICY, binance.getResilience().getRateLimitPolicy());

    ExchangeSpecification us = new BinanceUsExchange().getDefaultExchangeSpecification();
    assertTrue(us.getResilience().isRateLimiterEnabled());
    assertSame(BinanceRateLimitPolicy.usPolicy(), us.getResilience().getRateLimitPolicy());
  }

  // ---- helpers -------------------------------------------------------------------------------------

  private static long spotWeight(
      String key, boolean authenticated, Map<String, String> parameters) {
    RateLimitOperation operation =
        POLICY.classify(new RateLimitRequest(key, authenticated, parameters));
    assertNotNull(key, operation);
    assertEquals(key, 1L, (long) operation.getRequirements().get(SPOT_RAW));
    return operation.getRequirements().get(SPOT_WEIGHT);
  }

  private static long usdmWeight(String key, Map<String, String> parameters) {
    RateLimitOperation operation = POLICY.classify(new RateLimitRequest(key, false, parameters));
    assertNotNull(key, operation);
    return operation.getRequirements().get(USDM_WEIGHT);
  }

  private static boolean isPlacementOrModification(String key) {
    return key.equals("POST api/v3/order")
        || key.equals("POST fapi/v1/order")
        || key.equals("PUT fapi/v1/order")
        || key.equals("POST dapi/v1/order")
        || key.equals("POST papi/v1/um/order")
        || key.equals("POST papi/v1/cm/order")
        || key.equals("POST sapi/v1/capital/withdraw/apply");
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
    Path interfacePath = iface.getAnnotation(Path.class);
    if (interfacePath != null) {
      parts.add(interfacePath.value());
    }
    Path methodPath = method.getAnnotation(Path.class);
    if (methodPath != null) {
      parts.add(methodPath.value());
    }
    String path = String.join("/", parts).replaceAll("/+", "/").replaceAll("^/", "");
    return httpMethod(method) + " " + path;
  }
}
