package org.knowm.xchange.coinbase.v3.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.coinbase.CoinbaseAdapters;
import org.knowm.xchange.coinbase.v3.CoinbaseAuthenticated;
import org.knowm.xchange.coinbase.v3.CoinbaseUnknownOutcomeException;
import org.knowm.xchange.coinbase.v3.dto.CoinbaseException;
import org.knowm.xchange.coinbase.v3.dto.converts.CoinbaseCommitConvertTradeRequest;
import org.knowm.xchange.coinbase.v3.dto.converts.CoinbaseConvertQuoteRequest;
import org.knowm.xchange.coinbase.v3.dto.converts.CoinbaseConvertQuoteResponse;
import org.knowm.xchange.coinbase.v3.dto.converts.CoinbaseConvertTradeResponse;
import org.knowm.xchange.coinbase.v3.dto.futures.CoinbaseFuturesPositionResponse;
import org.knowm.xchange.coinbase.v3.dto.futures.CoinbaseFuturesPositionsResponse;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseCancelOrdersResponse;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseClosePositionRequest;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseCreateOrderResponse;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseEditOrderResponse;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseEditOrderRequest;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseListOrdersResponse;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseOrderDetail;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseOrderDetailResponse;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseOrderRequest;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseOrdersResponse;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbasePreviewOrderResponse;
import org.knowm.xchange.coinbase.v3.dto.perpetuals.CoinbasePerpetualsPositionResponse;
import org.knowm.xchange.coinbase.v3.dto.perpetuals.CoinbasePerpetualsPositionsResponse;
import org.knowm.xchange.coinbase.v3.dto.trade.CoinbaseTradeHistoryParams;
import org.knowm.xchange.exceptions.ExchangeException;
import si.mazi.rescu.ParamsDigest;

public class CoinbaseTradeServiceRaw extends CoinbaseBaseService {

  public CoinbaseTradeServiceRaw(Exchange exchange) {
    super(exchange);
  }

  public CoinbaseTradeServiceRaw(Exchange exchange, CoinbaseAuthenticated coinbaseAdvancedTrade) {
    super(exchange, coinbaseAdvancedTrade);
  }

  public CoinbaseTradeServiceRaw(
      Exchange exchange,
      CoinbaseAuthenticated coinbaseAdvancedTrade,
      ParamsDigest authTokenCreator) {
    super(exchange, coinbaseAdvancedTrade, authTokenCreator);
  }

  /**
   * Lists fills for the authenticated user using Coinbase Advanced Trade.
   *
   * @param params trade history parameters including optional product/order/trade filters,
   *     pagination cursor, time span, and limit
   * @return a {@link CoinbaseOrdersResponse} containing fills and a cursor for pagination
   * @throws IOException if a network or serialization error occurs
   */
  public CoinbaseOrdersResponse listFills(CoinbaseTradeHistoryParams params) throws IOException {
    return listFills(params, params.getNextPageCursor());
  }

  /**
   * Lists one fills page using the supplied cursor without mutating {@code params}.
   *
   * @param params immutable-for-request trade history filters
   * @param cursor explicit cursor for this request, or null for the first page
   * @return a response containing fills and its continuation cursor
   * @throws IOException if a network or serialization error occurs
   */
  public CoinbaseOrdersResponse listFills(CoinbaseTradeHistoryParams params, String cursor)
      throws IOException {
    List<String> productIds = null;
    if (params.getProductIds() != null && !params.getProductIds().isEmpty()) {
      productIds =
          params.getProductIds().stream()
              .filter(id -> id != null && !id.trim().isEmpty())
              .map(String::trim)
              .collect(Collectors.toList());
    } else if (params.getCurrencyPairs() != null && !params.getCurrencyPairs().isEmpty()) {
      productIds =
          params.getCurrencyPairs().stream()
              .map(CoinbaseAdapters::adaptProductId)
              .collect(Collectors.toList());
    }

    List<String> orderIds =
        params.getOrderId() == null ? null : Collections.singletonList(params.getOrderId());
    List<String> tradeIds =
        params.getTransactionId() == null
            ? null
            : Collections.singletonList(params.getTransactionId());

    String startTs =
        params.getStartTime() == null ? null : params.getStartTime().toInstant().toString();
    String endTs = params.getEndTime() == null ? null : params.getEndTime().toInstant().toString();

    Integer limit = params.getLimit();
    String retailPortfolioId = params.getRetailPortfolioId();
    List<String> assetFilters = toList(params.getAssetFilters());
    List<String> orderTypes = toList(params.getOrderTypes());
    List<String> productTypes = toList(params.getProductTypes());

    return coinbaseAdvancedTrade.listFills(
        authTokenCreator,
        orderIds,
        tradeIds,
        productIds,
        startTs,
        endTs,
        retailPortfolioId,
        limit,
        cursor,
        params.getSortBy(),
        assetFilters,
        orderTypes,
        params.getOrderSide(),
        productTypes);
  }

  private static List<String> toList(java.util.Collection<String> values) {
    return values == null || values.isEmpty() ? null : new ArrayList<>(values);
  }

  /**
   * Retrieves a historical order by its id using Coinbase Advanced Trade.
   *
   * @param orderId the Coinbase Advanced Trade order id
   * @return the detailed order response from Coinbase
   * @throws IOException if a network or serialization error occurs
   */
  public CoinbaseOrderDetailResponse getOrder(String orderId) throws IOException {
    return coinbaseAdvancedTrade.getOrder(authTokenCreator, orderId);
  }

  /**
   * Lists historical orders and returns the raw Coinbase response for further mapping. Note: this
   * endpoint returns historical orders; open orders can be derived by filtering status.
   *
   * @return response containing orders and pagination cursor
   * @throws IOException if a network or serialization error occurs
   */
  public CoinbaseListOrdersResponse listOrders() throws IOException {
    return listOrders(
        null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
        null, null, null);
  }

  /**
   * Lists historical orders with optional filters and returns the raw Coinbase response.
   *
   * @param orderIds optional list of order IDs to filter by
   * @param productIds optional list of product IDs to filter by
   * @param productType optional product type filter (e.g., "SPOT", "FUTURE")
   * @param orderStatus optional list of order statuses to filter by (e.g., "OPEN", "FILLED")
   * @param timeInForces optional list of time in force values
   * @param orderTypes optional list of order types (e.g., "LIMIT", "MARKET")
   * @param orderSide optional order side filter ("BUY" or "SELL")
   * @param startDate optional start date for filtering (ISO 8601 format)
   * @param endDate optional end date for filtering (ISO 8601 format)
   * @param orderPlacementSource optional placement source filter
   * @param contractExpiryType optional contract expiry type for futures
   * @param assetFilters optional list of assets to filter by
   * @param retailPortfolioId optional portfolio ID filter (deprecated for CDP keys)
   * @param limit optional limit on number of results to return
   * @param cursor optional pagination cursor
   * @param sortBy optional sort field
   * @param userNativeCurrency optional native currency (deprecated, defaults to USD)
   * @param useSimplifiedTotalValueCalculation optional flag for simplified calculation
   * @return response containing filtered orders and pagination cursor
   * @throws IOException if there is an error communicating with the Coinbase API
   */
  public CoinbaseListOrdersResponse listOrders(
      List<String> orderIds,
      List<String> productIds,
      String productType,
      List<String> orderStatus,
      List<String> timeInForces,
      List<String> orderTypes,
      String orderSide,
      String startDate,
      String endDate,
      String orderPlacementSource,
      String contractExpiryType,
      List<String> assetFilters,
      String retailPortfolioId,
      Integer limit,
      String cursor,
      String sortBy,
      String userNativeCurrency,
      Boolean useSimplifiedTotalValueCalculation)
      throws IOException {
    return coinbaseAdvancedTrade.listOrders(
        authTokenCreator,
        orderIds,
        productIds,
        productType,
        orderStatus,
        timeInForces,
        orderTypes,
        orderSide,
        startDate,
        endDate,
        orderPlacementSource,
        contractExpiryType,
        assetFilters,
        retailPortfolioId,
        limit,
        cursor,
        sortBy,
        userNativeCurrency,
        useSimplifiedTotalValueCalculation);
  }

  /**
   * Iterates order history across pages with a bounded, loop-safe cursor loop.
   *
   * <p>Stops when the caller-provided limit is reached, the server stops returning cursors, a
   * repeated cursor is detected, or the hard page bound is exceeded. A response claiming another
   * page without a non-blank continuation cursor is rejected as malformed. Filters mirror {@link
   * #listOrders(List, List, String, List, List, List, String, String, String, String, String, List,
   * String, Integer, String, String, String, Boolean)}.
   *
   * @param limit optional maximum number of orders to collect; null collects all pages
   * @return all collected orders
   * @throws IOException on transport failure
   * @throws ExchangeException when pagination does not advance or returns a malformed continuation
   */
  public List<CoinbaseOrderDetail> listOrdersBounded(Integer limit) throws IOException {
    return listOrdersBounded(null, limit);
  }

  /**
   * Iterates filtered order history across pages with a bounded, loop-safe cursor loop.
   *
   * @param orderStatuses optional Coinbase order-status filter
   * @param limit optional maximum number of orders to collect; null collects all pages
   * @return all collected orders
   * @throws IOException on transport failure
   * @throws ExchangeException when pagination does not advance or returns a malformed continuation
   */
  public List<CoinbaseOrderDetail> listOrdersBounded(
      List<String> orderStatuses, Integer limit) throws IOException {
    List<CoinbaseOrderDetail> orders = new ArrayList<>();
    Set<String> seenOrderIds = new HashSet<>();
    Set<String> seenCursors = new HashSet<>();
    int page = 0;
    String cursor = null;
    do {
      final String requestCursor = cursor;
      CoinbaseListOrdersResponse response =
          CoinbaseRetry.readWithBackoff(
              () ->
                  listOrders(
                      null,
                      null,
                      null,
                      orderStatuses,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      limit,
                      requestCursor,
                      null,
                      null,
                      null));
      if (response == null || response.getOrders() == null) {
        throw new ExchangeException(
            "Coinbase orders response is missing the required orders collection");
      }
      for (CoinbaseOrderDetail order : response.getOrders()) {
        String orderId = order == null ? null : order.getOrderId();
        if ((orderId == null || orderId.isBlank() || seenOrderIds.add(orderId))) {
          orders.add(order);
          if (limit != null && orders.size() >= limit) {
            break;
          }
        }
      }
      if (Boolean.TRUE.equals(response.getHasNext())
          && (response.getCursor() == null || response.getCursor().isBlank())) {
        throw new ExchangeException(
            "Coinbase orders pagination returned has_next=true without a "
                + "non-blank continuation cursor");
      }
      cursor =
          Boolean.TRUE.equals(response.getHasNext())
              ? advanceCursor(
                  response.getCursor(), seenCursors, page, MAX_PAGINATION_PAGES, "orders")
              : null;
      page++;
    } while (cursor != null && !cursor.isEmpty() && (limit == null || orders.size() < limit));
    return orders;
  }

  /**
   * Creates an order (market/limit/stop) by forwarding the request as-is to Coinbase. Caller is
   * responsible for constructing the correct request per Coinbase Advanced Trade.
   */
  public CoinbaseCreateOrderResponse createOrder(CoinbaseOrderRequest request) throws IOException {
    try {
      return coinbaseAdvancedTrade.createOrder(authTokenCreator, request);
    } catch (CoinbaseException providerFailure) {
      throw providerFailure;
    } catch (IOException transportFailure) {
      throw new CoinbaseUnknownOutcomeException(
          "createOrder", request.getClientOrderId(), transportFailure);
    }
  }

  /**
   * Legacy edit-order contract retained for source and binary compatibility.
   *
   * @deprecated use {@link #editOrderCurrent(CoinbaseEditOrderRequest)}
   */
  @Deprecated
  public CoinbaseOrdersResponse editOrder(CoinbaseEditOrderRequest request) throws IOException {
    try {
      return coinbaseAdvancedTrade.editOrder(authTokenCreator, request);
    } catch (CoinbaseException providerFailure) {
      throw providerFailure;
    } catch (IOException transportFailure) {
      throw new CoinbaseUnknownOutcomeException(
          "editOrder", Collections.singletonList(request.getOrderId()), null, transportFailure);
    }
  }

  /**
   * Edits an existing order using the current Advanced Trade response schema.
   *
   * @param request edit request
   * @return validated current edit response
   * @throws IOException when the request or response cannot be processed
   * @since 1.0.2
   */
  public CoinbaseEditOrderResponse editOrderCurrent(CoinbaseEditOrderRequest request)
      throws IOException {
    try {
      return requireSuccessfulEditResponse(
          coinbaseAdvancedTrade.editOrderCurrent(authTokenCreator, request), "editOrder");
    } catch (CoinbaseException providerFailure) {
      throw providerFailure;
    } catch (IOException transportFailure) {
      throw new CoinbaseUnknownOutcomeException(
          "editOrder", Collections.singletonList(request.getOrderId()), null, transportFailure);
    }
  }

  /**
   * Legacy order-preview contract retained for source and binary compatibility.
   *
   * @deprecated use {@link #previewOrderCurrent(CoinbaseOrderRequest)}
   */
  @Deprecated
  public CoinbaseOrdersResponse previewOrder(CoinbaseOrderRequest request) throws IOException {
    return coinbaseAdvancedTrade.previewOrder(authTokenCreator, request);
  }

  /**
   * Previews an order request using the current Advanced Trade response schema.
   *
   * @param request order request
   * @return validated current preview response
   * @throws IOException when the request or response cannot be processed
   * @since 1.0.2
   */
  public CoinbasePreviewOrderResponse previewOrderCurrent(CoinbaseOrderRequest request)
      throws IOException {
    CoinbasePreviewOrderResponse response =
        coinbaseAdvancedTrade.previewOrderCurrent(authTokenCreator, request);
    if (response == null) {
      throw new ExchangeException(
          "Coinbase previewOrder failed in a successful HTTP response: null response");
    }
    if (response.getErrs() == null) {
      throw new ExchangeException(
          "Coinbase previewOrder failed in a successful HTTP response: missing errs");
    }
    if (!response.getErrs().isEmpty()) {
      throw new ExchangeException(
          "Coinbase previewOrder rejected order: " + String.join(", ", response.getErrs()));
    }
    return response;
  }

  /**
   * Legacy edit-preview contract retained for source and binary compatibility.
   *
   * @deprecated use {@link #previewEditOrderCurrent(CoinbaseEditOrderRequest)}
   */
  @Deprecated
  public CoinbaseOrdersResponse previewEditOrder(CoinbaseEditOrderRequest request)
      throws IOException {
    return coinbaseAdvancedTrade.previewEditOrder(authTokenCreator, request);
  }

  /**
   * Previews an edit request using the current Advanced Trade response schema.
   *
   * @param request edit request
   * @return validated current edit-preview response
   * @throws IOException when the request or response cannot be processed
   * @since 1.0.2
   */
  public CoinbaseEditOrderResponse previewEditOrderCurrent(CoinbaseEditOrderRequest request)
      throws IOException {
    return requireSuccessfulEditResponse(
        coinbaseAdvancedTrade.previewEditOrderCurrent(authTokenCreator, request),
        "previewEditOrder");
  }

  private static CoinbaseEditOrderResponse requireSuccessfulEditResponse(
      CoinbaseEditOrderResponse response, String operation) {
    if (response == null || !response.isSuccess()) {
      String details =
          response == null
              ? "null response"
              : response.getErrors().stream().map(Object::toString).collect(Collectors.joining(", "));
      throw new ExchangeException(
          "Coinbase " + operation + " failed in a successful HTTP response: " + details);
    }
    return response;
  }

  /** Cancels provider order ids via the Advanced Trade {@code batch_cancel} endpoint. */
  public CoinbaseCancelOrdersResponse cancelOrders(List<String> orderIds) throws IOException {
    if (orderIds == null
        || orderIds.isEmpty()
        || orderIds.stream().anyMatch(orderId -> orderId == null || orderId.isBlank())) {
      throw new IllegalArgumentException("orderIds must contain at least one non-blank order id");
    }
    List<String> requestedOrderIds = List.copyOf(orderIds);
    Map<String, Object> payload = Collections.singletonMap("order_ids", requestedOrderIds);
    try {
      return coinbaseAdvancedTrade.batchCancelOrders(authTokenCreator, payload);
    } catch (CoinbaseException providerFailure) {
      throw providerFailure;
    } catch (IOException transportFailure) {
      throw new CoinbaseUnknownOutcomeException(
          "cancelOrders", requestedOrderIds, null, transportFailure);
    }
  }


  /**
   * Legacy cancellation ABI retaining the provider's historical response shape.
   *
   * @param orderIds provider order ids (optional)
   * @param clientOrderIds client order ids (optional)
   * @return the legacy provider response
   * @throws IOException if the request cannot be sent
   */
  @Deprecated
  public CoinbaseOrdersResponse cancelOrders(List<String> orderIds, List<String> clientOrderIds)
      throws IOException {
    Map<String, Object> payload = new HashMap<>();
    if (orderIds != null && !orderIds.isEmpty()) {
      payload.put("order_ids", orderIds);
    }
    if (clientOrderIds != null && !clientOrderIds.isEmpty()) {
      payload.put("client_order_ids", clientOrderIds);
    }
    return coinbaseAdvancedTrade.cancelOrders(authTokenCreator, payload);
  }
  /**
   * Legacy single-order cancellation contract retained for source and binary compatibility.
   *
   * @deprecated use {@link #cancelOrderByIdCurrent(String)}
   */
  @Deprecated
  public CoinbaseOrdersResponse cancelOrderById(String orderId) throws IOException {
    return cancelOrders(Collections.singletonList(orderId), null);
  }

  /**
   * Cancels one provider order id using the current batch-cancel response schema.
   *
   * @param orderId provider order id
   * @return current batch-cancel response
   * @throws IOException when the request or response cannot be processed
   * @since 1.0.2
   */
  public CoinbaseCancelOrdersResponse cancelOrderByIdCurrent(String orderId) throws IOException {
    return cancelOrders(Collections.singletonList(orderId));
  }

  /** Closes an open position using the Advanced Trade close_position endpoint. */
  public CoinbaseCreateOrderResponse closePosition(CoinbaseClosePositionRequest request)
      throws IOException {
    try {
      return coinbaseAdvancedTrade.closePosition(authTokenCreator, request);
    } catch (CoinbaseException providerFailure) {
      throw providerFailure;
    } catch (IOException transportFailure) {
      throw new CoinbaseUnknownOutcomeException(
          "closePosition", request.getClientOrderId(), transportFailure);
    }
  }

  /** Lists futures positions for the authenticated user. */
  public CoinbaseFuturesPositionsResponse listFuturesPositions() throws IOException {
    CoinbaseFuturesPositionsResponse response =
        coinbaseAdvancedTrade.listFuturesPositions(authTokenCreator);
    if (response == null || response.getPositions() == null) {
      throw new ExchangeException(
          "Coinbase futures positions response is missing the required positions collection");
    }
    return response;
  }

  /** Retrieves a futures position by product id. */
  public CoinbaseFuturesPositionResponse getFuturesPosition(String productId) throws IOException {
    return coinbaseAdvancedTrade.getFuturesPosition(authTokenCreator, productId);
  }

  /** Lists perpetuals positions for the specified portfolio. */
  public CoinbasePerpetualsPositionsResponse listPerpetualsPositions(String portfolioUuid)
      throws IOException {
    return coinbaseAdvancedTrade.listPerpetualsPositions(authTokenCreator, portfolioUuid);
  }
  /** Retrieves a perpetuals position by portfolio and symbol. */
  public CoinbasePerpetualsPositionResponse getPerpetualsPosition(
      String portfolioUuid, String symbol) throws IOException {
    return coinbaseAdvancedTrade.getPerpetualsPosition(authTokenCreator, portfolioUuid, symbol);
  }

  /**
   * Creates a convert quote.
   *
   * @param request Convert quote request payload.
   * @return The convert quote response.
   * @throws IOException if a network or serialization error occurs.
   */
  public CoinbaseConvertQuoteResponse createConvertQuote(CoinbaseConvertQuoteRequest request)
      throws IOException {
    return coinbaseAdvancedTrade.createConvertQuote(authTokenCreator, request);
  }

  /**
   * Commits a convert trade.
   *
   * @param tradeId Convert trade id returned from the quote request.
   * @param request Commit request payload.
   * @return The convert trade response.
   * @throws IOException if a network or serialization error occurs.
   */
  public CoinbaseConvertTradeResponse commitConvertTrade(
      String tradeId, CoinbaseCommitConvertTradeRequest request) throws IOException {
    try {
      return coinbaseAdvancedTrade.commitConvertTrade(authTokenCreator, tradeId, request);
    } catch (CoinbaseException providerFailure) {
      throw providerFailure;
    } catch (IOException transportFailure) {
      throw new CoinbaseUnknownOutcomeException(
          "commitConvertTrade", "trade_id", tradeId, transportFailure);
    }
  }

  /**
   * Retrieves a convert trade by id.
   *
   * @param tradeId Convert trade id.
   * @return The convert trade response.
   * @throws IOException if a network or serialization error occurs.
   */
  public CoinbaseConvertTradeResponse getConvertTrade(String tradeId) throws IOException {
    return coinbaseAdvancedTrade.getConvertTrade(authTokenCreator, tradeId);
  }
}
