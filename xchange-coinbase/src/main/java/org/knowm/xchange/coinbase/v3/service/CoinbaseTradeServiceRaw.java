package org.knowm.xchange.coinbase.v3.service;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.coinbase.CoinbaseAdapters;
import org.knowm.xchange.coinbase.v3.CoinbaseAuthenticated;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseOrderDetailResponse;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseListOrdersResponse;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseOrdersResponse;
import org.knowm.xchange.coinbase.v3.dto.trade.CoinbaseTradeHistoryParams;
import si.mazi.rescu.ParamsDigest;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseCreateOrderResponse;
import java.util.HashMap;
import java.util.Map;

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
   * <p>Gotcha: Although the Coinbase endpoint accepts multiple values for filters like product,
   * order, or trade IDs, this implementation forwards at most one value per filter. If
   * {@link CoinbaseTradeHistoryParams} contains multiple currency pairs, only the first is used to
   * derive the {@code product_id}. Likewise, only a single {@code order_id} and a single
   * {@code trade_id} are forwarded if present. This means the returned fills reflect only the first
   * provided values.
   *
   * @param params trade history parameters including optional product/order/trade filters,
   *               pagination cursor, time span, and limit
   * @return a {@link CoinbaseOrdersResponse} containing fills and a cursor for pagination
   * @throws IOException if a network or serialization error occurs
   */
  public CoinbaseOrdersResponse listFills(CoinbaseTradeHistoryParams params) throws IOException {
    List<String> productIds = null;
    if (params.getCurrencyPairs() != null && !params.getCurrencyPairs().isEmpty()) {
      List<String> pids = params.getCurrencyPairs().stream().map(CoinbaseAdapters::adaptProductId)
          .collect(Collectors.toList());
      // Only allow at most one product_id
      productIds = Collections.singletonList(pids.get(0));
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

    return coinbaseAdvancedTrade.listFills(authTokenCreator, orderIds, tradeIds, productIds,
        startTs, endTs, null, limit, cursor, null);
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
   * Creates an order (market/limit/stop) by forwarding the payload as-is to Coinbase.
   * Caller is responsible for constructing the correct payload per Coinbase Advanced Trade.
   */
  public CoinbaseCreateOrderResponse createOrder(Object payload) throws IOException {
    return coinbaseAdvancedTrade.createOrder(authTokenCreator, payload);
  }

  /**
   * Edit an existing order natively via Advanced Trade.
   */
  public CoinbaseOrdersResponse editOrder(Object payload) throws IOException {
    return coinbaseAdvancedTrade.editOrder(authTokenCreator, payload);
  }

  /**
   * Preview an order request without placing it.
   */
  public CoinbaseOrdersResponse previewOrder(Object payload) throws IOException {
    return coinbaseAdvancedTrade.previewOrder(authTokenCreator, payload);
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

}
