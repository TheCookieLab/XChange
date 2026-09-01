package org.knowm.xchange.coinbase.v3.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.coinbase.CoinbaseAdapters;
import org.knowm.xchange.coinbase.v3.CoinbaseAuthenticated;
import org.knowm.xchange.coinbase.v3.CoinbaseExchange;
import org.knowm.xchange.coinbase.v3.CoinbaseProductIdentity;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseCancelOrderResult;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseCancelOrdersResponse;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseClosePositionRequest;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseCreateOrderResponse;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseEditOrderRequest;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseFill;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseListOrdersResponse;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseOrderDetailResponse;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseOrderRequest;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseOrdersResponse;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseV3OrderRequests;
import org.knowm.xchange.coinbase.v3.dto.portfolios.CoinbasePortfolio;
import org.knowm.xchange.coinbase.v3.dto.portfolios.CoinbasePortfoliosResponse;
import org.knowm.xchange.coinbase.v3.dto.trade.CoinbaseTradeHistoryParams;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.account.OpenPosition;
import org.knowm.xchange.dto.account.OpenPositions;
import org.knowm.xchange.dto.marketdata.Trades;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.dto.trade.OpenOrders;
import org.knowm.xchange.dto.trade.StopOrder;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.dto.trade.UserTrades;
import org.knowm.xchange.exceptions.NotAvailableFromExchangeException;
import org.knowm.xchange.service.trade.TradeService;
import org.knowm.xchange.service.trade.params.CancelAllOrders;
import org.knowm.xchange.service.trade.params.CancelOrderParams;
import org.knowm.xchange.service.trade.params.DefaultCancelOrderParamId;
import org.knowm.xchange.service.trade.params.TradeHistoryParams;
import org.knowm.xchange.service.trade.params.orders.OrderQueryParams;
import si.mazi.rescu.ParamsDigest;

/**
 * Trade service implementation for Coinbase Advanced Trade (v3) API.
 *
 * <p>This service provides access to trading operations including order placement, cancellation,
 * order queries, trade history, and open orders. It extends {@link CoinbaseTradeServiceRaw} to
 * provide high-level XChange DTOs mapped from Coinbase-specific responses.
 *
 * <p>All methods in this service map Coinbase API responses to standard XChange trade objects such
 * as {@link Order}, {@link OpenOrders}, {@link UserTrades}, and {@link UserTrade}.
 */
public class CoinbaseTradeService extends CoinbaseTradeServiceRaw implements TradeService {

  private final CoinbaseProductIdentity productIdentity;

  /**
   * Constructs a new trade service using the exchange's default configuration.
   *
   * @param exchange The exchange instance containing API credentials and configuration.
   */
  public CoinbaseTradeService(Exchange exchange) {
    super(exchange);
    this.productIdentity = configuredProductIdentity(exchange);
  }

  /**
   * Constructs a new trade service with a custom authenticated API client.
   *
   * @param exchange              The exchange instance containing API credentials and configuration.
   * @param coinbaseAdvancedTrade The authenticated Coinbase API client for making requests.
   */
  public CoinbaseTradeService(Exchange exchange, CoinbaseAuthenticated coinbaseAdvancedTrade) {
    super(exchange, coinbaseAdvancedTrade);
    this.productIdentity = configuredProductIdentity(exchange);
  }

  /**
   * Constructs a new trade service with a custom authenticated API client and token creator.
   *
   * @param exchange              The exchange instance containing API credentials and configuration.
   * @param coinbaseAdvancedTrade The authenticated Coinbase API client for making requests.
   * @param authTokenCreator      The parameter digest for creating authentication tokens.
   */
  public CoinbaseTradeService(
      Exchange exchange,
      CoinbaseAuthenticated coinbaseAdvancedTrade,
      ParamsDigest authTokenCreator) {
    this(exchange, coinbaseAdvancedTrade, authTokenCreator, configuredProductIdentity(exchange));
  }

  /**
   * Constructs a trade service with an explicitly supplied product identity catalog.
   *
   * @param exchange              The exchange instance containing API credentials and configuration.
   * @param coinbaseAdvancedTrade The authenticated Coinbase API client for making requests.
   * @param authTokenCreator      The parameter digest for creating authentication tokens.
   * @param productIdentity       catalog used to resolve instruments, or {@code null} to use the
   *                              standard adapter
   */
  public CoinbaseTradeService(
      Exchange exchange,
      CoinbaseAuthenticated coinbaseAdvancedTrade,
      ParamsDigest authTokenCreator,
      CoinbaseProductIdentity productIdentity) {
    super(exchange, coinbaseAdvancedTrade, authTokenCreator);
    this.productIdentity = productIdentity;
  }

  private static CoinbaseProductIdentity configuredProductIdentity(Exchange exchange) {
    if (exchange == null || exchange.getExchangeSpecification() == null) {
      return null;
    }
    Object configured = exchange.getExchangeSpecification()
        .getExchangeSpecificParametersItem(CoinbaseExchange.PARAM_PRODUCT_IDENTITY);
    return configured instanceof CoinbaseProductIdentity
        ? (CoinbaseProductIdentity) configured
        : null;
  }

  /**
   * Creates a new instance of trade history parameters for querying user trades.
   *
   * <p>The returned parameters object can be configured with filters such as currency pairs, time
   * ranges, order IDs, trade IDs, and pagination cursors before being passed to {@link
   * #getTradeHistory(TradeHistoryParams)}.
   *
   * @return A new {@link CoinbaseTradeHistoryParams} instance for configuring trade history
   *     queries.
   */
  @Override
  public TradeHistoryParams createTradeHistoryParams() {
    return new CoinbaseTradeHistoryParams();
  }

  /**
   * Retrieves one or more orders by their query parameters.
   *
   * <p>This method accepts multiple {@link OrderQueryParams} and returns a collection of orders.
   * Each parameter must contain a valid order ID via {@link OrderQueryParams#getOrderId()}.
   * Parameters with null or missing order IDs are skipped.
   *
   * @param orderQueryParams One or more order query parameters, each containing an order ID.
   * @return A collection of {@link Order} objects corresponding to the provided order IDs. Orders
   *     are adapted from Coinbase order details to XChange order objects.
   * @throws IOException If there is an error communicating with the Coinbase API.
   */
  @Override
  public Collection<Order> getOrder(OrderQueryParams... orderQueryParams) throws IOException {
    List<Order> orders = new ArrayList<>(orderQueryParams.length);
    for (OrderQueryParams param : orderQueryParams) {
      String orderId = param.getOrderId();
      if (orderId == null) continue;
      orders.add(CoinbaseAdapters.adaptOrder(getOrder(orderId).getOrder()));
    }
    return orders;
  }

  /**
   * Retrieves the user's trade history using the Coinbase Advanced Trade API.
   *
   * <p>Pagination is handled automatically via the response cursor until it is exhausted or the
   * optional {@code limit} in {@link CoinbaseTradeHistoryParams} is reached. When a limit stops
   * iteration partway through a response page, the raw request cursor and its consumed-result
   * offset are retained on the parameters so the next call skips the returned prefix and reaches
   * every remaining fill.
   *
   * @param params expected to be {@link CoinbaseTradeHistoryParams}; includes optional time span,
   *     limit and next-page cursor
   * @return the user's trades sorted by timestamp
   * @throws IOException if a network or serialization error occurs
   * @throws IllegalArgumentException if {@code params} is not an instance of {@link
   *     CoinbaseTradeHistoryParams}
   */
  @Override
  public UserTrades getTradeHistory(TradeHistoryParams params) throws IOException {
    if (!(params instanceof CoinbaseTradeHistoryParams)) {
      throw new IllegalArgumentException(
          "Expected CoinbaseTradeHistoryParams for Coinbase Advanced Trade history");
    }

    CoinbaseTradeHistoryParams v3Params = (CoinbaseTradeHistoryParams) params;
    List<UserTrade> trades = new ArrayList<>();
    Set<String> seenFillIds = new HashSet<>();
    Set<String> seenCursors = new HashSet<>();
    int page = 0;
    String cursor = v3Params.getNextPageCursor();
    int fillOffset = v3Params.getNextPageCursorFillOffset();
    do {
      final String requestCursor = cursor;
      final int requestFillOffset = fillOffset;
      CoinbaseOrdersResponse response =
          CoinbaseRetry.readWithBackoff(() -> listFills(v3Params, requestCursor));
      if (response == null || response.getFills() == null) {
        throw new org.knowm.xchange.exceptions.ExchangeException(
            "Coinbase fills response is missing the required fills collection");
      }
      if (requestFillOffset > response.getFills().size()) {
        throw new org.knowm.xchange.exceptions.ExchangeException(
            "Coinbase fills page is shorter than its saved continuation offset");
      }
      boolean responseFullyConsumed = true;
      for (int fillIndex = requestFillOffset; fillIndex < response.getFills().size(); fillIndex++) {
        CoinbaseFill fill = response.getFills().get(fillIndex);
        String fillId = fill.getEntryId();
        if (fillId == null || fillId.isBlank()) {
          fillId = fill.getTradeId();
        }
        if (fillId == null || fillId.isBlank() || seenFillIds.add(fillId)) {
          trades.add(CoinbaseAdapters.adaptFill(fill));
          if (v3Params.getLimit() != null
              && trades.size() >= v3Params.getLimit()
              && fillIndex + 1 < response.getFills().size()) {
            responseFullyConsumed = false;
            cursor = requestCursor;
            fillOffset = fillIndex + 1;
            break;
          }
        }
      }
      if (responseFullyConsumed) {
        cursor =
            advanceCursor(
                response.getCursor(), seenCursors, page, MAX_PAGINATION_PAGES, "fills");
        fillOffset = 0;
        page++;
      }
    } while (cursor != null
        && !cursor.isEmpty()
        && (v3Params.getLimit() == null || trades.size() < v3Params.getLimit()));
    v3Params.setNextPageCursorContinuation(cursor, fillOffset);

    return new UserTrades(trades, Trades.TradeSortType.SortByTimestamp);
  }

  /**
   * Retrieves all currently open orders for the authenticated user.
   *
   * <p>This method fetches historical orders from Coinbase Advanced Trade and filters them to
   * return only those in an open status. The Advanced Trade API includes current open orders in the
   * historical orders list, so this method filters accordingly to provide only active orders.
   *
   * @return An {@link OpenOrders} object containing all open limit, market, and stop orders.
   * @throws IOException If there is an error communicating with the Coinbase API.
   */
  @Override
  public OpenOrders getOpenOrders() throws IOException {
    return CoinbaseAdapters.adaptOpenOrders(listOrdersBounded(null));
  }

  /**
   * Retrieves open positions for futures and perpetuals (if available).
   *
   * @return Combined open positions.
   * @throws IOException If there is an error communicating with the Coinbase API.
   */
  @Override
  public OpenPositions getOpenPositions() throws IOException {
    if (!hasAuthentication()) {
      throw new NotAvailableFromExchangeException("Open positions require authentication");
    }

    List<OpenPosition> openPositions = new ArrayList<>();

    // Futures positions (CFM)
    openPositions.addAll(
        CoinbaseAdapters.adaptFuturesOpenPositions(listFuturesPositions().getPositions())
            .getOpenPositions());

    // Perpetuals positions (INTX) - require portfolio UUIDs
    for (CoinbasePortfolio portfolio : listPerpetualsPortfolios()) {
      openPositions.addAll(
          CoinbaseAdapters.adaptPerpetualsOpenPositions(
                  listPerpetualsPositions(portfolio.getUuid()).getPositions())
              .getOpenPositions());
    }

    return new OpenPositions(openPositions);
  }

  /** Retrieves futures open positions only. */
  public OpenPositions getFuturesOpenPositions() throws IOException {
    if (!hasAuthentication()) {
      throw new NotAvailableFromExchangeException("Open positions require authentication");
    }
    return CoinbaseAdapters.adaptFuturesOpenPositions(listFuturesPositions().getPositions());
  }

  /** Retrieves perpetuals open positions for a specific portfolio UUID. */
  public OpenPositions getPerpetualsOpenPositions(String portfolioUuid) throws IOException {
    if (!hasAuthentication()) {
      throw new NotAvailableFromExchangeException("Open positions require authentication");
    }
    return CoinbaseAdapters.adaptPerpetualsOpenPositions(
        listPerpetualsPositions(portfolioUuid).getPositions());
  }

  /**
   * Convenience method to fetch the raw list orders response from Coinbase.
   *
   * <p>This method delegates to the raw service method and returns the unmodified Coinbase
   * response. Use this when you need access to Coinbase-specific fields that are not mapped to
   * XChange DTOs.
   *
   * @return A {@link CoinbaseListOrdersResponse} containing the raw order list response from
   *     Coinbase.
   * @throws IOException If there is an error communicating with the Coinbase API.
   */
  public CoinbaseListOrdersResponse listOrders() throws IOException {
    return super.listOrders();
  }

  /**
   * Previews an order edit without modifying the live order.
   *
   * @param request current-schema edit request
   * @return provider edit-preview response
   * @throws IOException when the request cannot be transported
   * @throws org.knowm.xchange.exceptions.ExchangeException when the HTTP-200 response is absent or
   *     reports success=false
   * @since 1.0.2
   */
  public org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseEditOrderResponse previewEditOrderCurrent(
      CoinbaseEditOrderRequest request) throws IOException {
    return super.previewEditOrderCurrent(request);
  }

  /**
   * Closes an open position using the Advanced Trade close_position endpoint.
   *
   * @param request Close position request payload.
   * @return The accepted create-order response.
   * @throws IOException If there is an error communicating with the Coinbase API.
   * @throws org.knowm.xchange.exceptions.ExchangeException when Coinbase returns a successful HTTP
   *     response with success=false or no order id
   */
  public CoinbaseCreateOrderResponse closePosition(CoinbaseClosePositionRequest request)
      throws IOException {
    CoinbaseCreateOrderResponse response = super.closePosition(request);
    requireCreatedOrderId(response);
    return response;
  }

  /**
   * Retrieves a historical order by its id and adapts it to XChange {@link Order}.
   *
   * @param orderId the Coinbase Advanced Trade order id
   * @return the adapted order
   * @throws IOException if a network or serialization error occurs
   */
  public CoinbaseOrderDetailResponse getOrder(String orderId) throws IOException {
    return super.getOrder(orderId);
  }

  /**
   * Places a market order.
   *
   * @param marketOrder order containing product, side, and quantity
   * @return provider-assigned order id
   * @throws IOException when transport fails
   * @throws org.knowm.xchange.exceptions.ExchangeException when Coinbase returns a successful HTTP
   *     response with success=false or no order id
   */
  @Override
  public String placeMarketOrder(MarketOrder marketOrder) throws IOException {
    CoinbaseOrderRequest request = CoinbaseV3OrderRequests.marketOrderRequest(marketOrder, productIdentity);
    return requireCreatedOrderId(super.createOrder(request));
  }

  /**
   * Places a limit order.
   *
   * @param limitOrder order containing product, side, quantity, and limit price
   * @return provider-assigned order id
   * @throws IOException when transport fails
   * @throws org.knowm.xchange.exceptions.ExchangeException when Coinbase returns a successful HTTP
   *     response with success=false or no order id
   */
  @Override
  public String placeLimitOrder(LimitOrder limitOrder) throws IOException {
    CoinbaseOrderRequest request = CoinbaseV3OrderRequests.limitOrderRequest(limitOrder, productIdentity);
    return requireCreatedOrderId(super.createOrder(request));
  }

  /**
   * Places a stop order.
   *
   * @param stopOrder order containing product, side, quantity, and stop price
   * @return provider-assigned order id
   * @throws IOException when transport fails
   * @throws org.knowm.xchange.exceptions.ExchangeException when Coinbase returns a successful HTTP
   *     response with success=false or no order id
   */
  @Override
  public String placeStopOrder(StopOrder stopOrder) throws IOException {
    CoinbaseOrderRequest request = CoinbaseV3OrderRequests.stopOrderRequest(stopOrder, productIdentity);
    return requireCreatedOrderId(super.createOrder(request));
  }

  private static String requireCreatedOrderId(CoinbaseCreateOrderResponse response) {
    if (response == null
        || !response.isSuccess()
        || response.getOrderId() == null
        || response.getOrderId().isBlank()) {
      String details =
          response == null
              ? "null response"
              : response.getErrorResponse() == null
                  ? "missing order id"
                  : response.getErrorResponse().getError()
                      + ": " + response.getErrorResponse().getMessage();
      throw new org.knowm.xchange.exceptions.ExchangeException(
          "Coinbase order placement failed in a successful HTTP response: " + details);
    }
    return response.getOrderId();
  }

  /**
   * Verifies a limit order by previewing it without actually placing it.
   *
   * <p>This method uses the Coinbase order preview endpoint to validate the order parameters and
   * check if the order would be accepted. If the preview fails, a {@link RuntimeException} is
   * thrown with details about the failure.
   *
   * @param limitOrder The limit order to verify, containing the instrument, side, quantity, and
   *     limit price.
   * @throws RuntimeException If the order preview fails, wrapping the underlying {@link
   *     IOException}.
   */
  @Override
  public void verifyOrder(LimitOrder limitOrder) {
    try {
      CoinbaseOrderRequest request = CoinbaseV3OrderRequests.previewLimitOrderRequest(limitOrder, productIdentity);
      super.previewOrderCurrent(request);
    } catch (IOException e) {
      throw new RuntimeException("Failed to preview limit order", e);
    }
  }

  /**
   * Verifies a market order by previewing it without actually placing it.
   *
   * <p>This method uses the Coinbase order preview endpoint to validate the order parameters and
   * check if the order would be accepted. If the preview fails, a {@link RuntimeException} is
   * thrown with details about the failure.
   *
   * @param marketOrder The market order to verify, containing the instrument, side, and quantity.
   * @throws RuntimeException If the order preview fails, wrapping the underlying {@link
   *     IOException}.
   */
  @Override
  public void verifyOrder(MarketOrder marketOrder) {
    try {
      CoinbaseOrderRequest request = CoinbaseV3OrderRequests.previewMarketOrderRequest(marketOrder, productIdentity);
      super.previewOrderCurrent(request);
    } catch (IOException e) {
      throw new RuntimeException("Failed to preview market order", e);
    }
  }

  /**
   * Modifies an existing limit order.
   *
   * <p>This method allows you to update the parameters of an existing limit order, such as changing
   * the price or quantity. The order must contain a valid exchange order ID.
   *
   * @param limitOrder The limit order to modify, containing the order ID and updated parameters
   *     (price, quantity, etc.).
   * @return The order ID of the modified order (same as the input order's ID).
   * @throws IOException If there is an error communicating with the Coinbase API or if the order
   *     modification fails.
   */
  @Override
  public String changeOrder(LimitOrder limitOrder) throws IOException {
    CoinbaseEditOrderRequest request = CoinbaseV3OrderRequests.editLimitOrderRequest(limitOrder);
    super.editOrderCurrent(request);
    return limitOrder.getId();
  }

  /**
   * Cancels an order using the provided cancellation parameters.
   *
   * <p>This method supports cancellation by order ID using {@link DefaultCancelOrderParamId}. Other
   * parameter types may be supported in the future (e.g., cancellation by client order ID).
   *
   * @param orderParams Cancellation parameters. Must be an instance of {@link
   *     DefaultCancelOrderParamId} containing the order ID to cancel.
   * @return {@code true} if the order was successfully cancelled, {@code false} if the parameter
   *     type is not supported.
   * @throws IOException If there is an error communicating with the Coinbase API or if the
   *     cancellation fails.
   */
  @Override
  public boolean cancelOrder(CancelOrderParams orderParams) throws IOException {
    if (!(orderParams instanceof DefaultCancelOrderParamId)) {
      return false;
    }
    String orderId = ((DefaultCancelOrderParamId) orderParams).getOrderId();
    return successfulOrderIds(super.cancelOrderByIdCurrent(orderId)).contains(orderId);
  }

  /**
   * Cancels all open orders for the authenticated user.
   *
   * <p>This method retrieves all currently open orders and cancels them in a single batch
   * operation. If there are no open orders, an empty collection is returned without making an API
   * call.
   *
   * @param orderParams Cancellation parameters (currently unused, but required by the interface).
   * @return A collection of order IDs that were cancelled. Returns an empty collection if there
   *     were no open orders to cancel.
   * @throws IOException If there is an error communicating with the Coinbase API or if the
   *     cancellation fails.
   */
  @Override
  public Collection<String> cancelAllOrders(CancelAllOrders orderParams) throws IOException {
    OpenOrders openOrders = getOpenOrders();
    List<String> ids = new ArrayList<>();
    for (Order order : openOrders.getAllOpenOrders()) {
      if (order.getId() != null && !order.getId().isEmpty()) {
        ids.add(order.getId());
      }
    }
    if (ids.isEmpty()) {
      return Collections.emptyList();
    }
    Set<String> successfulIds = successfulOrderIds(super.cancelOrders(ids));
    return ids.stream().filter(successfulIds::contains).collect(Collectors.toList());
  }

  private static Set<String> successfulOrderIds(CoinbaseCancelOrdersResponse response) {
    if (response == null) {
      return Collections.emptySet();
    }
    return response.getResults().stream()
        .filter(CoinbaseCancelOrderResult::isSuccess)
        .map(CoinbaseCancelOrderResult::getOrderId)
        .filter(orderId -> orderId != null && !orderId.isBlank())
        .collect(Collectors.toSet());
  }

  private List<CoinbasePortfolio> listPerpetualsPortfolios() throws IOException {
    CoinbasePortfoliosResponse response =
        coinbaseAdvancedTrade.listPortfolios(authTokenCreator, null);
    if (response == null || response.getPortfolios() == null) {
      return Collections.emptyList();
    }
    return response.getPortfolios().stream()
        .filter(portfolio -> portfolio.getDeleted() == null || !portfolio.getDeleted())
        .filter(portfolio -> isPerpetualPortfolioType(portfolio.getType()))
        .collect(Collectors.toList());
  }

  private boolean isPerpetualPortfolioType(String type) {
    if (type == null) {
      return false;
    }
    String normalized = type.toUpperCase();
    return normalized.contains("INTX") || normalized.contains("PERP");
  }
}
