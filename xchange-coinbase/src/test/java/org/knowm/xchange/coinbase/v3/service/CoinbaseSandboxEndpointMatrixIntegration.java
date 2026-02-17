package org.knowm.xchange.coinbase.v3.service;

import static org.junit.Assert.assertNotNull;

import java.math.BigDecimal;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import javax.net.ssl.SSLException;
import org.junit.BeforeClass;
import org.junit.Test;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.client.ExchangeRestProxyBuilder;
import org.knowm.xchange.coinbase.v2.dto.accounts.CoinbaseV2Account;
import org.knowm.xchange.coinbase.v2.dto.accounts.CoinbaseV2AccountsResponse;
import org.knowm.xchange.coinbase.v3.Coinbase;
import org.knowm.xchange.coinbase.v3.CoinbaseAuthenticated;
import org.knowm.xchange.coinbase.v3.CoinbaseExchange;
import org.knowm.xchange.coinbase.v3.CoinbaseTestUtils;
import org.knowm.xchange.coinbase.v3.CoinbaseV3Digest;
import org.knowm.xchange.coinbase.v3.dto.accounts.CoinbaseAccount;
import org.knowm.xchange.coinbase.v3.dto.converts.CoinbaseCommitConvertTradeRequest;
import org.knowm.xchange.coinbase.v3.dto.converts.CoinbaseConvertQuoteRequest;
import org.knowm.xchange.coinbase.v3.dto.converts.CoinbaseConvertQuoteResponse;
import org.knowm.xchange.coinbase.v3.dto.futures.CoinbaseFuturesPosition;
import org.knowm.xchange.coinbase.v3.dto.futures.CoinbaseFuturesPositionsResponse;
import org.knowm.xchange.coinbase.v3.dto.futures.CoinbaseFuturesSweepRequest;
import org.knowm.xchange.coinbase.v3.dto.futures.CoinbaseIntradayMarginSetting;
import org.knowm.xchange.coinbase.v3.dto.futures.CoinbaseIntradayMarginSettingRequest;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseEditOrderRequest;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseListOrdersResponse;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseOrderRequest;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseV3OrderRequests;
import org.knowm.xchange.coinbase.v3.dto.perpetuals.CoinbaseAllocatePortfolioRequest;
import org.knowm.xchange.coinbase.v3.dto.perpetuals.CoinbasePerpetualsPosition;
import org.knowm.xchange.coinbase.v3.dto.perpetuals.CoinbasePerpetualsPositionsResponse;
import org.knowm.xchange.coinbase.v3.dto.perpetuals.CoinbaseMultiAssetCollateralRequest;
import org.knowm.xchange.coinbase.v3.dto.portfolios.CoinbaseMovePortfolioFundsRequest;
import org.knowm.xchange.coinbase.v3.dto.portfolios.CoinbasePortfolio;
import org.knowm.xchange.coinbase.v3.dto.portfolios.CoinbasePortfolioAmount;
import org.knowm.xchange.coinbase.v3.dto.portfolios.CoinbasePortfolioResponse;
import org.knowm.xchange.coinbase.v3.dto.portfolios.CoinbasePortfolioRequest;
import org.knowm.xchange.coinbase.v3.dto.portfolios.CoinbasePortfoliosResponse;
import org.knowm.xchange.coinbase.v3.dto.trade.CoinbaseTradeHistoryParams;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.dto.trade.StopOrder;
import si.mazi.rescu.HttpStatusExceptionSupport;
import si.mazi.rescu.ParamsDigest;

/**
 * Sandbox-first integration matrix for Coinbase Advanced Trade REST endpoints.
 *
 * <p>This suite prioritizes sandbox connectivity and systematically exercises public and authenticated
 * endpoint variants exposed by the XChange Coinbase module. Endpoint-level HTTP errors (for example
 * 4xx/5xx due to unsupported sandbox features or business-rule validation) are treated as reachable,
 * while transport/connectivity failures fail the test.
 */
public class CoinbaseSandboxEndpointMatrixIntegration {

  private static final String PRIMARY_PRODUCT_ID = "BTC-USD";
  private static final String SECONDARY_PRODUCT_ID = "ETH-USD";
  private static final String SYNTHETIC_ACCOUNT_ID = "sandbox-account-123";
  private static final String SYNTHETIC_ORDER_ID = "sandbox-order-456";
  private static final String SYNTHETIC_TRADE_ID = "sandbox-trade-789";
  private static final String SYNTHETIC_PORTFOLIO_ID = "sandbox-portfolio-123";
  private static final String SYNTHETIC_FUTURES_PRODUCT_ID = "BTC-PERP";
  private static final String SYNTHETIC_SYMBOL = "BTC-PERP";

  private static Coinbase publicClient;
  private static CoinbaseAuthenticated authenticatedClient;
  private static CoinbaseAccountService accountService;
  private static CoinbaseMarketDataService marketDataService;
  private static CoinbaseTradeService tradeService;
  private static ParamsDigest authDigest;

  @BeforeClass
  public static void setUp() {
    ExchangeSpecification spec = CoinbaseTestUtils.createSandboxSpecificationWithCredentials();
    authDigest = CoinbaseV3Digest.createInstance(spec.getApiKey(), spec.getSecretKey());
    assertNotNull("Sandbox auth digest must be available", authDigest);

    publicClient = ExchangeRestProxyBuilder.forInterface(Coinbase.class, spec).build();
    authenticatedClient = ExchangeRestProxyBuilder.forInterface(CoinbaseAuthenticated.class, spec).build();

    CoinbaseExchange exchange = (CoinbaseExchange) ExchangeFactory.INSTANCE.createExchange(spec);
    accountService = (CoinbaseAccountService) exchange.getAccountService();
    marketDataService = (CoinbaseMarketDataService) exchange.getMarketDataService();
    tradeService = (CoinbaseTradeService) exchange.getTradeService();
  }

  /**
   * Covers all public market endpoints with multiple argument combinations.
   */
  @Test
  public void testPublicEndpointVariations() throws Exception {
    String start = isoNowMinusDays(2);
    String end = isoNowMinusDays(1);

    assertEndpointReachable("GET /time", () -> publicClient.getTime());
    assertEndpointReachable(
        "GET /market/product_book default",
        () -> publicClient.getPublicProductBook(PRIMARY_PRODUCT_ID, null, null));
    assertEndpointReachable(
        "GET /market/product_book with limit+aggregation",
        () -> publicClient.getPublicProductBook(PRIMARY_PRODUCT_ID, 50, "0.01"));

    assertEndpointReachable(
        "GET /market/products default",
        () -> publicClient.listPublicProducts(25, null, null, null, null, null, null, null));
    assertEndpointReachable(
        "GET /market/products filtered",
        () ->
            publicClient.listPublicProducts(
                25,
                0,
                "SPOT",
                Arrays.asList(PRIMARY_PRODUCT_ID, SECONDARY_PRODUCT_ID),
                null,
                null,
                Boolean.TRUE,
                "VOLUME_24H_DESC"));

    assertEndpointReachable(
        "GET /market/products/{product_id}",
        () -> publicClient.getPublicProduct(PRIMARY_PRODUCT_ID));
    assertEndpointReachable(
        "GET /market/products/{product_id}/candles default range",
        () -> publicClient.getPublicProductCandles(PRIMARY_PRODUCT_ID, null, null, "ONE_HOUR", 10));
    assertEndpointReachable(
        "GET /market/products/{product_id}/candles explicit range",
        () -> publicClient.getPublicProductCandles(PRIMARY_PRODUCT_ID, start, end, "ONE_MINUTE", 25));
    assertEndpointReachable(
        "GET /market/products/{product_id}/ticker default range",
        () -> publicClient.getPublicMarketTrades(PRIMARY_PRODUCT_ID, 25, null, null));
    assertEndpointReachable(
        "GET /market/products/{product_id}/ticker explicit range",
        () -> publicClient.getPublicMarketTrades(PRIMARY_PRODUCT_ID, 25, start, end));
  }

  /**
   * Covers authenticated market-data endpoints and parameter combinations.
   */
  @Test
  public void testMarketDataEndpointVariations() throws Exception {
    String start = isoNowMinusDays(2);
    String end = isoNowMinusDays(1);

    assertEndpointReachable(
        "GET /best_bid_ask single product",
        () -> marketDataService.getBestBidAsk(PRIMARY_PRODUCT_ID));
    assertEndpointReachable(
        "GET /best_bid_ask all products",
        () -> marketDataService.getBestBidAsk((String) null));

    assertEndpointReachable(
        "GET /products/{product_id}",
        () -> marketDataService.getProduct(PRIMARY_PRODUCT_ID));
    assertEndpointReachable(
        "GET /products/{product_id} with tradability",
        () -> authenticatedClient.getProduct(authDigest, PRIMARY_PRODUCT_ID, Boolean.TRUE));

    assertEndpointReachable(
        "GET /products list via service",
        () -> marketDataService.listProducts("SPOT"));
    assertEndpointReachable(
        "GET /products list with full filters",
        () ->
            authenticatedClient.listProducts(
                authDigest,
                25,
                0,
                "SPOT",
                new String[] {PRIMARY_PRODUCT_ID, SECONDARY_PRODUCT_ID},
                null,
                null,
                Boolean.TRUE,
                Boolean.TRUE,
                "VOLUME_24H_DESC"));

    assertEndpointReachable(
        "GET /products/{product_id}/ticker default",
        () -> marketDataService.getMarketTrades(PRIMARY_PRODUCT_ID, 50, null, null));
    assertEndpointReachable(
        "GET /products/{product_id}/ticker explicit range",
        () -> marketDataService.getMarketTrades(PRIMARY_PRODUCT_ID, 50, start, end));

    assertEndpointReachable(
        "GET /products/{product_id}/candles default",
        () -> marketDataService.getProductCandles(PRIMARY_PRODUCT_ID, "ONE_HOUR", 25, null, null));
    assertEndpointReachable(
        "GET /products/{product_id}/candles explicit range",
        () -> marketDataService.getProductCandles(PRIMARY_PRODUCT_ID, "ONE_MINUTE", 25, start, end));

    assertEndpointReachable(
        "GET /product_book default",
        () -> marketDataService.getProductBook(PRIMARY_PRODUCT_ID, null, null));
    assertEndpointReachable(
        "GET /product_book with params",
        () -> marketDataService.getProductBook(PRIMARY_PRODUCT_ID, 50, 0.01d));
  }

  /**
   * Covers authenticated account/portfolio/futures/perpetual/v2 account endpoints and variants.
   */
  @Test
  public void testAccountEndpointVariations() throws Exception {
    List<CoinbaseAccount> accounts =
        invokeEndpoint("GET /accounts discovery", () -> accountService.getCoinbaseAccounts());
    String accountId = firstAccountId(accounts);

    assertEndpointReachable(
        "GET /accounts default",
        () -> authenticatedClient.listAccounts(authDigest, 250, null));
    assertEndpointReachable(
        "GET /accounts with cursor",
        () -> authenticatedClient.listAccounts(authDigest, 25, "cursor-placeholder"));
    assertEndpointReachable(
        "GET /accounts/{account_id}",
        () -> accountService.getCoinbaseAccount(nonEmpty(accountId, SYNTHETIC_ACCOUNT_ID)));

    invokeEndpoint("GET /payment_methods discovery", () -> accountService.getCoinbasePaymentMethods());

    assertEndpointReachable(
        "GET /payment_methods",
        () -> accountService.getCoinbasePaymentMethods());
    assertEndpointReachable(
        "GET /payment_methods/{payment_method_id}",
        () -> accountService.getCoinbasePaymentMethod("sandbox-payment-method"));
    assertEndpointReachable("GET /key_permissions", () -> accountService.getKeyPermissions());

    CoinbasePortfoliosResponse portfolios =
        invokeEndpoint("GET /portfolios discovery", () -> accountService.listPortfolios(null));
    String portfolioUuid = firstPortfolioUuid(portfolios);
    String perpetualPortfolioUuid = firstPerpetualPortfolioUuid(portfolios);

    assertEndpointReachable("GET /portfolios default", () -> accountService.listPortfolios(null));
    assertEndpointReachable("GET /portfolios type filter", () -> accountService.listPortfolios("CONSUMER"));
    assertEndpointReachable(
        "GET /portfolios/{portfolio_uuid}",
        () -> accountService.getPortfolioBreakdown(nonEmpty(portfolioUuid, SYNTHETIC_PORTFOLIO_ID)));

    String createName = "xchange-it-" + System.currentTimeMillis();
    CoinbasePortfolioResponse createdPortfolio =
        invokeEndpoint(
            "POST /portfolios",
            () -> accountService.createPortfolio(new CoinbasePortfolioRequest(createName)));
    String createdPortfolioUuid =
        createdPortfolio != null && createdPortfolio.getPortfolio() != null
            ? createdPortfolio.getPortfolio().getUuid()
            : null;

    String editTarget = nonEmpty(createdPortfolioUuid, nonEmpty(portfolioUuid, SYNTHETIC_PORTFOLIO_ID));
    assertEndpointReachable(
        "PUT /portfolios/{portfolio_uuid}",
        () -> accountService.editPortfolio(editTarget, new CoinbasePortfolioRequest(createName + "-edited")));
    assertEndpointReachable(
        "DELETE /portfolios/{portfolio_uuid}",
        () -> accountService.deletePortfolio(editTarget));

    String sourcePortfolio = nonEmpty(portfolioUuid, SYNTHETIC_PORTFOLIO_ID);
    String targetPortfolio = nonEmpty(createdPortfolioUuid, sourcePortfolio);
    assertEndpointReachable(
        "POST /portfolios/move_funds",
        () ->
            accountService.movePortfolioFunds(
                new CoinbaseMovePortfolioFundsRequest(
                    new CoinbasePortfolioAmount(new BigDecimal("1"), "USD"),
                    sourcePortfolio,
                    targetPortfolio)));

    assertEndpointReachable("GET /transaction_summary default", () -> accountService.getTransactionSummary());
    assertEndpointReachable(
        "GET /transaction_summary filtered",
        () -> accountService.getTransactionSummary("SPOT", null, "neptune"));

    CoinbaseV2AccountsResponse v2Accounts =
        invokeEndpoint("GET /v2/accounts discovery", () -> accountService.listV2Accounts(25, null, null, "desc"));
    String v2AccountId = firstV2AccountId(v2Accounts);

    assertEndpointReachable(
        "GET /v2/accounts basic",
        () -> accountService.listV2Accounts(10, null, null, null));
    assertEndpointReachable(
        "GET /v2/accounts/{account_id}/transactions",
        () ->
            accountService.listV2AccountTransactions(
                nonEmpty(v2AccountId, SYNTHETIC_ACCOUNT_ID),
                10,
                null,
                null,
                "desc"));

    assertEndpointReachable("GET /cfm/balance_summary", () -> accountService.getFuturesBalanceSummary());
    assertEndpointReachable(
        "POST /cfm/sweeps/schedule",
        () -> accountService.scheduleFuturesSweep(new CoinbaseFuturesSweepRequest(new BigDecimal("10"))));
    assertEndpointReachable("GET /cfm/sweeps", () -> accountService.listFuturesSweeps());
    assertEndpointReachable("DELETE /cfm/sweeps", () -> accountService.cancelFuturesSweep());

    assertEndpointReachable("GET /cfm/intraday/margin_setting", () -> accountService.getIntradayMarginSetting());
    assertEndpointReachable(
        "POST /cfm/intraday/margin_setting",
        () ->
            accountService.setIntradayMarginSetting(
                new CoinbaseIntradayMarginSettingRequest(
                    CoinbaseIntradayMarginSetting.INTRADAY_MARGIN_SETTING_STANDARD)));
    assertEndpointReachable(
        "GET /cfm/intraday/current_margin_window",
        () -> accountService.getCurrentMarginWindow());

    String perpetualPortfolio = nonEmpty(perpetualPortfolioUuid, nonEmpty(portfolioUuid, SYNTHETIC_PORTFOLIO_ID));
    assertEndpointReachable(
        "GET /intx/portfolio/{portfolio_uuid}",
        () -> accountService.getPerpetualsPortfolioSummary(perpetualPortfolio));
    assertEndpointReachable(
        "GET /intx/balances/{portfolio_uuid}",
        () -> accountService.getPerpetualsPortfolioBalances(perpetualPortfolio));
    assertEndpointReachable(
        "POST /intx/multi_asset_collateral",
        () ->
            accountService.optInMultiAssetCollateral(
                new CoinbaseMultiAssetCollateralRequest(perpetualPortfolio, Boolean.TRUE)));
    assertEndpointReachable(
        "POST /intx/allocate",
        () ->
            accountService.allocatePortfolio(
                new CoinbaseAllocatePortfolioRequest(
                    perpetualPortfolio, SYNTHETIC_SYMBOL, new BigDecimal("1"), "USD")));
  }

  /**
   * Covers authenticated trade/order/fills/positions/convert endpoints and variants.
   */
  @Test
  public void testTradeEndpointVariations() throws Exception {
    String start = isoNowMinusDays(2);
    String end = isoNowMinusDays(1);

    CoinbaseTradeHistoryParams historyParams = new CoinbaseTradeHistoryParams();
    historyParams.setLimit(25);
    historyParams.addCurrencyPair(CurrencyPair.BTC_USD);
    historyParams.setStartTime(Date.from(Instant.parse(start)));
    historyParams.setEndTime(Date.from(Instant.parse(end)));
    assertEndpointReachable("GET /orders/historical/fills basic", () -> tradeService.listFills(historyParams));

    CoinbaseTradeHistoryParams filteredHistory = new CoinbaseTradeHistoryParams();
    filteredHistory.addProductId(PRIMARY_PRODUCT_ID);
    filteredHistory.setOrderId(SYNTHETIC_ORDER_ID);
    filteredHistory.setTransactionId(SYNTHETIC_TRADE_ID);
    filteredHistory.setLimit(10);
    assertEndpointReachable(
        "GET /orders/historical/fills filtered",
        () -> tradeService.listFills(filteredHistory));

    CoinbaseListOrdersResponse orders =
        invokeEndpoint("GET /orders/historical/batch discovery", () -> tradeService.listOrders());
    String orderId = firstOrderId(orders);

    assertEndpointReachable("GET /orders/historical/batch default", () -> tradeService.listOrders());
    assertEndpointReachable(
        "GET /orders/historical/batch filtered",
        () ->
            tradeService.listOrders(
                Collections.singletonList(nonEmpty(orderId, SYNTHETIC_ORDER_ID)),
                Arrays.asList(PRIMARY_PRODUCT_ID, SECONDARY_PRODUCT_ID),
                "SPOT",
                Arrays.asList("OPEN", "FILLED"),
                Collections.singletonList("GOOD_UNTIL_CANCELLED"),
                Arrays.asList("LIMIT", "MARKET"),
                "BUY",
                start,
                end,
                null,
                null,
                Arrays.asList("BTC", "ETH"),
                null,
                25,
                null,
                "LIMIT_PRICE",
                "USD",
                Boolean.TRUE));

    assertEndpointReachable(
        "GET /orders/historical/{order_id}",
        () -> tradeService.getOrder(nonEmpty(orderId, SYNTHETIC_ORDER_ID)));

    MarketOrder marketOrder =
        new MarketOrder.Builder(Order.OrderType.BID, CurrencyPair.BTC_USD)
            .originalAmount(new BigDecimal("5"))
            .userReference("xchange-it-" + System.currentTimeMillis())
            .build();
    LimitOrder limitOrder =
        new LimitOrder.Builder(Order.OrderType.BID, CurrencyPair.BTC_USD)
            .originalAmount(new BigDecimal("0.001"))
            .limitPrice(new BigDecimal("10000"))
            .id(nonEmpty(orderId, SYNTHETIC_ORDER_ID))
            .userReference("xchange-it-limit-" + System.currentTimeMillis())
            .build();
    StopOrder stopOrder =
        new StopOrder.Builder(Order.OrderType.BID, CurrencyPair.BTC_USD)
            .originalAmount(new BigDecimal("0.001"))
            .limitPrice(new BigDecimal("9000"))
            .stopPrice(new BigDecimal("9500"))
            .userReference("xchange-it-stop-" + System.currentTimeMillis())
            .build();

    CoinbaseOrderRequest previewMarketRequest = CoinbaseV3OrderRequests.previewMarketOrderRequest(marketOrder);
    CoinbaseOrderRequest previewLimitRequest = CoinbaseV3OrderRequests.previewLimitOrderRequest(limitOrder);
    CoinbaseOrderRequest previewStopRequest = CoinbaseV3OrderRequests.previewStopOrderRequest(stopOrder);
    CoinbaseEditOrderRequest editRequest = CoinbaseV3OrderRequests.editLimitOrderRequest(limitOrder);

    assertEndpointReachable("POST /orders/preview market", () -> tradeService.previewOrder(previewMarketRequest));
    assertEndpointReachable("POST /orders/preview limit", () -> tradeService.previewOrder(previewLimitRequest));
    assertEndpointReachable("POST /orders/preview stop", () -> tradeService.previewOrder(previewStopRequest));
    assertEndpointReachable("POST /orders/edit_preview", () -> tradeService.previewEditOrder(editRequest));

    assertEndpointReachable(
        "POST /orders create",
        () -> tradeService.createOrder(CoinbaseV3OrderRequests.limitOrderRequest(limitOrder)));
    assertEndpointReachable("POST /orders/edit", () -> tradeService.editOrder(editRequest));
    assertEndpointReachable(
        "POST /orders/batch_cancel",
        () -> tradeService.cancelOrders(Collections.singletonList(nonEmpty(orderId, SYNTHETIC_ORDER_ID)), null));
    assertEndpointReachable(
        "POST /orders/batch_cancel single id",
        () -> tradeService.cancelOrderById(nonEmpty(orderId, SYNTHETIC_ORDER_ID)));

    assertEndpointReachable(
        "POST /orders/close_position",
        () ->
            tradeService.closePosition(
                new org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseClosePositionRequest(
                    "xchange-close-" + System.currentTimeMillis(), SYNTHETIC_FUTURES_PRODUCT_ID, new BigDecimal("1"))));

    CoinbaseFuturesPositionsResponse futuresPositions =
        invokeEndpoint("GET /cfm/positions discovery", () -> tradeService.listFuturesPositions());
    String futuresProductId = firstFuturesProductId(futuresPositions);
    assertEndpointReachable("GET /cfm/positions", () -> tradeService.listFuturesPositions());
    assertEndpointReachable(
        "GET /cfm/positions/{product_id}",
        () -> tradeService.getFuturesPosition(nonEmpty(futuresProductId, SYNTHETIC_FUTURES_PRODUCT_ID)));

    String perpetualPortfolioId = nonEmpty(firstPerpetualPortfolioUuid(
        invokeEndpoint("GET /portfolios for perpetual discovery", () -> accountService.listPortfolios(null))),
        SYNTHETIC_PORTFOLIO_ID);
    CoinbasePerpetualsPositionsResponse perpetualsPositions =
        invokeEndpoint(
            "GET /intx/positions/{portfolio_uuid} discovery",
            () -> tradeService.listPerpetualsPositions(perpetualPortfolioId));
    String symbol = firstPerpetualSymbol(perpetualsPositions);
    assertEndpointReachable(
        "GET /intx/positions/{portfolio_uuid}",
        () -> tradeService.listPerpetualsPositions(perpetualPortfolioId));
    assertEndpointReachable(
        "GET /intx/positions/{portfolio_uuid}/{symbol}",
        () -> tradeService.getPerpetualsPosition(perpetualPortfolioId, nonEmpty(symbol, SYNTHETIC_SYMBOL)));

    String fromAccount = nonEmpty(firstAccountId(invokeEndpoint(
        "GET /accounts for convert discovery", () -> accountService.getCoinbaseAccounts())), SYNTHETIC_ACCOUNT_ID);
    String toAccount = fromAccount;
    CoinbaseConvertQuoteResponse quote =
        invokeEndpoint(
            "POST /convert/quote",
            () ->
                tradeService.createConvertQuote(
                    new CoinbaseConvertQuoteRequest(fromAccount, toAccount, new BigDecimal("1"), null)));
    String tradeId = quote != null ? quote.getTradeId() : null;
    assertEndpointReachable(
        "POST /convert/trade/{trade_id}",
        () ->
            tradeService.commitConvertTrade(
                nonEmpty(tradeId, SYNTHETIC_TRADE_ID),
                new CoinbaseCommitConvertTradeRequest(fromAccount, toAccount)));
    assertEndpointReachable(
        "GET /convert/trade/{trade_id}",
        () -> tradeService.getConvertTrade(nonEmpty(tradeId, SYNTHETIC_TRADE_ID)));
  }

  private static String isoNowMinusDays(long days) {
    return Instant.now().minus(days, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS).toString();
  }

  private static String firstAccountId(List<CoinbaseAccount> accounts) {
    return accounts != null && !accounts.isEmpty() ? accounts.get(0).getUuid() : null;
  }

  private static String firstPortfolioUuid(CoinbasePortfoliosResponse response) {
    List<CoinbasePortfolio> portfolios = response == null ? null : response.getPortfolios();
    return portfolios != null && !portfolios.isEmpty() ? portfolios.get(0).getUuid() : null;
  }

  private static String firstPerpetualPortfolioUuid(CoinbasePortfoliosResponse response) {
    List<CoinbasePortfolio> portfolios = response == null ? null : response.getPortfolios();
    if (portfolios == null) {
      return null;
    }
    for (CoinbasePortfolio portfolio : portfolios) {
      if (portfolio.getType() == null) {
        continue;
      }
      String upper = portfolio.getType().toUpperCase(Locale.ROOT);
      if (upper.contains("INTX") || upper.contains("PERP")) {
        return portfolio.getUuid();
      }
    }
    return null;
  }

  private static String firstV2AccountId(CoinbaseV2AccountsResponse response) {
    List<CoinbaseV2Account> accounts = response == null ? null : response.getData();
    return accounts != null && !accounts.isEmpty() ? accounts.get(0).getId() : null;
  }

  private static String firstOrderId(CoinbaseListOrdersResponse response) {
    return response != null
            && response.getOrders() != null
            && !response.getOrders().isEmpty()
            && response.getOrders().get(0) != null
        ? response.getOrders().get(0).getOrderId()
        : null;
  }

  private static String firstFuturesProductId(CoinbaseFuturesPositionsResponse response) {
    List<CoinbaseFuturesPosition> positions = response == null ? null : response.getPositions();
    return positions != null && !positions.isEmpty() ? positions.get(0).getProductId() : null;
  }

  private static String firstPerpetualSymbol(CoinbasePerpetualsPositionsResponse response) {
    List<CoinbasePerpetualsPosition> positions = response == null ? null : response.getPositions();
    if (positions == null || positions.isEmpty()) {
      return null;
    }
    CoinbasePerpetualsPosition first = positions.get(0);
    return first.getSymbol() != null ? first.getSymbol() : first.getProductId();
  }

  private static String nonEmpty(String value, String fallback) {
    return value == null || value.trim().isEmpty() ? fallback : value;
  }

  private static void assertEndpointReachable(String endpoint, EndpointCall call) throws Exception {
    invokeEndpoint(
        endpoint,
        () -> {
          call.run();
          return Boolean.TRUE;
        });
  }

  private static <T> T invokeEndpoint(String endpoint, EndpointSupplier<T> supplier) throws Exception {
    try {
      return supplier.run();
    } catch (Exception e) {
      if (isConnectivityFailure(e)) {
        throw new AssertionError("Connectivity failure while calling " + endpoint, e);
      }
      if (isHttpResponseException(e)) {
        return null;
      }
      throw e;
    }
  }

  private static boolean isHttpResponseException(Throwable throwable) {
    for (Throwable current = throwable; current != null; current = current.getCause()) {
      if (current instanceof HttpStatusExceptionSupport) {
        return true;
      }
      String className = current.getClass().getName();
      if (className.contains("HttpStatus")) {
        return true;
      }
    }
    return false;
  }

  private static boolean isConnectivityFailure(Throwable throwable) {
    for (Throwable current = throwable; current != null; current = current.getCause()) {
      if (current instanceof UnknownHostException
          || current instanceof ConnectException
          || current instanceof NoRouteToHostException
          || current instanceof SocketTimeoutException
          || current instanceof SSLException) {
        return true;
      }
      if (current instanceof SocketException) {
        String message = current.getMessage();
        if (message != null
            && (message.contains("Connection reset")
                || message.contains("Network is unreachable")
                || message.contains("Broken pipe"))) {
          return true;
        }
      }
    }
    return false;
  }

  @FunctionalInterface
  private interface EndpointSupplier<T> {
    T run() throws Exception;
  }

  @FunctionalInterface
  private interface EndpointCall {
    void run() throws Exception;
  }
}
