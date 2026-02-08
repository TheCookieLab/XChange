package org.knowm.xchange.coinbase.v3.service;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.coinbase.CoinbaseAdapters;
import org.knowm.xchange.coinbase.v3.CoinbaseAuthenticated;
import org.knowm.xchange.coinbase.v3.dto.converts.CoinbaseCommitConvertTradeRequest;
import org.knowm.xchange.coinbase.v3.dto.converts.CoinbaseConvertQuoteRequest;
import org.knowm.xchange.coinbase.v3.dto.converts.CoinbaseConvertQuoteResponse;
import org.knowm.xchange.coinbase.v3.dto.converts.CoinbaseConvertTradeResponse;
import org.knowm.xchange.coinbase.v3.dto.futures.CoinbaseFuturesPositionResponse;
import org.knowm.xchange.coinbase.v3.dto.futures.CoinbaseFuturesPositionsResponse;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseCreateOrderResponse;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseClosePositionRequest;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseEditOrderRequest;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseListOrdersResponse;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseOrderDetailResponse;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseOrdersResponse;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseOrderRequest;
import org.knowm.xchange.coinbase.v3.dto.perpetuals.CoinbasePerpetualsPositionResponse;
import org.knowm.xchange.coinbase.v3.dto.perpetuals.CoinbasePerpetualsPositionsResponse;
import org.knowm.xchange.coinbase.v3.dto.trade.CoinbaseTradeHistoryParams;
import si.mazi.rescu.ParamsDigest;

public class CoinbaseTradeServiceRaw extends CoinbaseBaseService {

  public CoinbaseTradeServiceRaw(Exchange exchange) {
    super(exchange);
  }

  public CoinbaseTradeServiceRaw(Exchange exchange, CoinbaseAuthenticated coinbaseAdvancedTrade) {
    super(exchange, coinbaseAdvancedTrade);
  }

  public CoinbaseTradeServiceRaw(Exchange exchange, CoinbaseAuthenticated coinbaseAdvancedTrade,
      ParamsDigest authTokenCreator) {
    super(exchange, coinbaseAdvancedTrade, authTokenCreator);
  }

  /**
   * Lists fills for the authenticated user using Coinbase Advanced Trade.
   *
   * @param params trade history parameters including optional product/order/trade filters,
   *               pagination cursor, time span, and limit
   * @return a {@link CoinbaseOrdersResponse} containing fills and a cursor for pagination
   * @throws IOException if a network or serialization error occurs
   */
  public CoinbaseOrdersResponse listFills(CoinbaseTradeHistoryParams params) throws IOException {
    List<String> productIds = null;
    if (params.getProductIds() != null && !params.getProductIds().isEmpty()) {
      productIds = params.getProductIds().stream()
          .filter(id -> id != null && !id.trim().isEmpty())
          .map(String::trim)
          .collect(Collectors.toList());
    } else if (params.getCurrencyPairs() != null && !params.getCurrencyPairs().isEmpty()) {
      productIds = params.getCurrencyPairs().stream()
          .map(CoinbaseAdapters::adaptProductId)
          .collect(Collectors.toList());
    }

    List<String> orderIds =
        params.getOrderId() == null ? null : Collections.singletonList(params.getOrderId());
    List<String> tradeIds = params.getTransactionId() == null ? null
        : Collections.singletonList(params.getTransactionId());

    String startTs =
        params.getStartTime() == null ? null : params.getStartTime().toInstant().toString();
    String endTs = params.getEndTime() == null ? null : params.getEndTime().toInstant().toString();

    Integer limit = params.getLimit();
    String cursor = params.getNextPageCursor();
    String retailPortfolioId = params.getRetailPortfolioId();

    return coinbaseAdvancedTrade.listFills(authTokenCreator, orderIds, tradeIds, productIds,
        startTs, endTs, retailPortfolioId, limit, cursor, null);
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
   * Lists historical orders and returns the raw Coinbase response for further mapping.
   * Note: this endpoint returns historical orders; open orders can be derived by filtering status.
   * 
   * @return response containing orders and pagination cursor
   * @throws IOException if a network or serialization error occurs
   */
  public CoinbaseListOrdersResponse listOrders() throws IOException {
    return listOrders(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
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
   * @throws IOException if a network or serialization error occurs
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
      Boolean useSimplifiedTotalValueCalculation) throws IOException {
    return coinbaseAdvancedTrade.listOrders(authTokenCreator, orderIds, productIds, productType,
        orderStatus, timeInForces, orderTypes, orderSide, startDate, endDate, orderPlacementSource,
        contractExpiryType, assetFilters, retailPortfolioId, limit, cursor, sortBy,
        userNativeCurrency, useSimplifiedTotalValueCalculation);
  }

  /**
   * Creates an order (market/limit/stop) by forwarding the request as-is to Coinbase.
   * Caller is responsible for constructing the correct request per Coinbase Advanced Trade.
   */
  public CoinbaseCreateOrderResponse createOrder(CoinbaseOrderRequest request) throws IOException {
    return coinbaseAdvancedTrade.createOrder(authTokenCreator, request);
  }

  /**
   * Edit an existing order natively via Advanced Trade.
   */
  public CoinbaseOrdersResponse editOrder(CoinbaseEditOrderRequest request) throws IOException {
    return coinbaseAdvancedTrade.editOrder(authTokenCreator, request);
  }

  /**
   * Preview an order request without placing it.
   */
  public CoinbaseOrdersResponse previewOrder(CoinbaseOrderRequest request) throws IOException {
    return coinbaseAdvancedTrade.previewOrder(authTokenCreator, request);
  }

  /**
   * Preview an order edit request without modifying the live order.
   */
  public CoinbaseOrdersResponse previewEditOrder(CoinbaseEditOrderRequest request) throws IOException {
    return coinbaseAdvancedTrade.previewEditOrder(authTokenCreator, request);
  }

  /**
   * Cancels orders by id and/or client order id via Advanced Trade batch_cancel endpoint.
   */
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

  /** Convenience overload to cancel a single order id. */
  public CoinbaseOrdersResponse cancelOrderById(String orderId) throws IOException {
    return cancelOrders(Collections.singletonList(orderId), null);
  }

  /**
   * Closes an open position using the Advanced Trade close_position endpoint.
   */
  public CoinbaseCreateOrderResponse closePosition(CoinbaseClosePositionRequest request)
      throws IOException {
    return coinbaseAdvancedTrade.closePosition(authTokenCreator, request);
  }

  /**
   * Lists futures positions for the authenticated user.
   */
  public CoinbaseFuturesPositionsResponse listFuturesPositions() throws IOException {
    return coinbaseAdvancedTrade.listFuturesPositions(authTokenCreator);
  }

  /**
   * Retrieves a futures position by product id.
   */
  public CoinbaseFuturesPositionResponse getFuturesPosition(String productId) throws IOException {
    return coinbaseAdvancedTrade.getFuturesPosition(authTokenCreator, productId);
  }

  /**
   * Lists perpetuals positions for the specified portfolio.
   */
  public CoinbasePerpetualsPositionsResponse listPerpetualsPositions(String portfolioUuid)
      throws IOException {
    return coinbaseAdvancedTrade.listPerpetualsPositions(authTokenCreator, portfolioUuid);
  }

  /**
   * Retrieves a perpetuals position by portfolio and symbol.
   */
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
  public CoinbaseConvertTradeResponse commitConvertTrade(String tradeId,
      CoinbaseCommitConvertTradeRequest request) throws IOException {
    return coinbaseAdvancedTrade.commitConvertTrade(authTokenCreator, tradeId, request);
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
