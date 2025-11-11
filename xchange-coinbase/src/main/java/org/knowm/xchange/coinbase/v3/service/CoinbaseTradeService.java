package org.knowm.xchange.coinbase.v3.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.coinbase.v3.CoinbaseAuthenticated;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseFill;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseListOrdersResponse;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseOrderDetailResponse;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseOrdersResponse;
import org.knowm.xchange.coinbase.v3.dto.orders.CoinbaseV3OrderRequests;
import org.knowm.xchange.coinbase.v3.dto.trade.CoinbaseTradeHistoryParams;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.coinbase.CoinbaseAdapters;
import org.knowm.xchange.dto.marketdata.Trades;
import org.knowm.xchange.dto.trade.OpenOrders;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.dto.trade.StopOrder;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.dto.trade.UserTrades;
import org.knowm.xchange.service.trade.TradeService;
import org.knowm.xchange.service.trade.params.TradeHistoryParams;

import org.knowm.xchange.service.trade.params.orders.OrderQueryParams;
import org.knowm.xchange.service.trade.params.CancelOrderParams;
import org.knowm.xchange.service.trade.params.CancelAllOrders;
import org.knowm.xchange.service.trade.params.DefaultCancelOrderParamId;
import si.mazi.rescu.ParamsDigest;

/**
 * Trade service implementation for Coinbase Advanced Trade (v3) API.
 * <p>
 * This service provides access to trading operations including order placement, cancellation,
 * order queries, trade history, and open orders. It extends {@link CoinbaseTradeServiceRaw} to
 * provide high-level XChange DTOs mapped from Coinbase-specific responses.
 * </p>
 * <p>
 * All methods in this service map Coinbase API responses to standard XChange trade objects
 * such as {@link Order}, {@link OpenOrders}, {@link UserTrades}, and {@link UserTrade}.
 * </p>
 */
public class CoinbaseTradeService extends CoinbaseTradeServiceRaw implements TradeService {

  /**
   * Constructs a new trade service using the exchange's default configuration.
   *
   * @param exchange The exchange instance containing API credentials and configuration.
   */
  public CoinbaseTradeService(Exchange exchange) {
    super(exchange);
  }

  /**
   * Constructs a new trade service with a custom authenticated API client.
   *
   * @param exchange              The exchange instance containing API credentials and configuration.
   * @param coinbaseAdvancedTrade The authenticated Coinbase API client for making requests.
   */
  public CoinbaseTradeService(Exchange exchange, CoinbaseAuthenticated coinbaseAdvancedTrade) {
    super(exchange, coinbaseAdvancedTrade);
  }

  /**
   * Constructs a new trade service with a custom authenticated API client and token creator.
   *
   * @param exchange              The exchange instance containing API credentials and configuration.
   * @param coinbaseAdvancedTrade The authenticated Coinbase API client for making requests.
   * @param authTokenCreator      The parameter digest for creating authentication tokens.
   */
  public CoinbaseTradeService(Exchange exchange, CoinbaseAuthenticated coinbaseAdvancedTrade,
      ParamsDigest authTokenCreator) {
    super(exchange, coinbaseAdvancedTrade, authTokenCreator);
  }

  /**
   * Creates a new instance of trade history parameters for querying user trades.
   * <p>
   * The returned parameters object can be configured with filters such as currency pairs,
   * time ranges, order IDs, trade IDs, and pagination cursors before being passed to
   * {@link #getTradeHistory(TradeHistoryParams)}.
   * </p>
   *
   * @return A new {@link CoinbaseTradeHistoryParams} instance for configuring trade history queries.
   */
  @Override
  public TradeHistoryParams createTradeHistoryParams() {
    return new CoinbaseTradeHistoryParams();
  }

  /**
   * Retrieves one or more orders by their query parameters.
   * <p>
   * This method accepts multiple {@link OrderQueryParams} and returns a collection of orders.
   * Each parameter must contain a valid order ID via {@link OrderQueryParams#getOrderId()}.
   * Parameters with null or missing order IDs are skipped.
   * </p>
   *
   * @param orderQueryParams One or more order query parameters, each containing an order ID.
   * @return A collection of {@link Order} objects corresponding to the provided order IDs.
   *         Orders are adapted from Coinbase order details to XChange order objects.
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
   * <p>Gotcha: If {@code params} contains multiple {@link org.knowm.xchange.currency.CurrencyPair}
   * entries, only the first pair is forwarded to the underlying {@code listFills} call. While the
   * Coinbase REST endpoint supports multi-value filters, this implementation currently forwards at
   * most one value per filter to avoid repeated parameter encoding. The resulting history therefore
   * reflects only the first currency pair (and at most one order/trade id) provided.
   *
   * <p>Pagination is handled automatically via the response cursor until it is exhausted or the
   * optional {@code limit} in {@link CoinbaseTradeHistoryParams} is reached.
   *
   * @param params expected to be {@link CoinbaseTradeHistoryParams}; includes optional time span,
   *     limit and next-page cursor
   * @return the user's trades sorted by timestamp
   * @throws IOException if a network or serialization error occurs
   * @throws IllegalArgumentException if {@code params} is not an instance of
   *     {@link CoinbaseTradeHistoryParams}
   */
  @Override
  public UserTrades getTradeHistory(TradeHistoryParams params) throws IOException {
    if (!(params instanceof CoinbaseTradeHistoryParams)) {
      throw new IllegalArgumentException(
          "Expected CoinbaseTradeHistoryParams for Coinbase Advanced Trade history");
    }

    CoinbaseTradeHistoryParams v3Params =
        (CoinbaseTradeHistoryParams) params;

    List<UserTrade> trades = new ArrayList<>();
    String cursor;
    do {
      CoinbaseOrdersResponse response = listFills(
          v3Params);
      for (CoinbaseFill fill : response.getFills()) {
        UserTrade trade = UserTrade.builder()
            .type(fill.getOrderType()).originalAmount(fill.getSize()).instrument(fill.getInstrument())
            .price(fill.getPrice()).timestamp(fill.getTradeTime()).id(fill.getTradeId())
            .orderId(fill.getOrderId()).feeAmount(fill.getCommission())
            .feeCurrency(fill.getFeeCurrency()).build();
        trades.add(trade);
      }
      cursor = response.getCursor();
      v3Params.setNextPageCursor(cursor);
    } while (cursor != null && !cursor.isEmpty() && (v3Params.getLimit() == null
        || trades.size() < v3Params.getLimit()));

    return new UserTrades(trades,
        Trades.TradeSortType.SortByTimestamp);
  }

  /**
   * Retrieves all currently open orders for the authenticated user.
   * <p>
   * This method fetches historical orders from Coinbase Advanced Trade and filters them to
   * return only those in an open status. The Advanced Trade API includes current open orders
   * in the historical orders list, so this method filters accordingly to provide only active orders.
   * </p>
   *
   * @return An {@link OpenOrders} object containing all open limit, market, and stop orders.
   * @throws IOException If there is an error communicating with the Coinbase API.
   */
  @Override
  public OpenOrders getOpenOrders() throws IOException {
    return CoinbaseAdapters.adaptOpenOrders(super.listOrders());
  }

  /**
   * Convenience method to fetch the raw list orders response from Coinbase.
   * <p>
   * This method delegates to the raw service method and returns the unmodified Coinbase response.
   * Use this when you need access to Coinbase-specific fields that are not mapped to XChange DTOs.
   * </p>
   *
   * @return A {@link CoinbaseListOrdersResponse} containing the raw order list response from Coinbase.
   * @throws IOException If there is an error communicating with the Coinbase API.
   */
  public CoinbaseListOrdersResponse listOrders()
      throws IOException {
    return super.listOrders();
  }
  /**
   * Retrieves a historical order by its id and adapts it to XChange {@link Order}.
   *
   * @param orderId the Coinbase Advanced Trade order id
   * @return the adapted order
   * @throws IOException if a network or serialization error occurs
   */
  public CoinbaseOrderDetailResponse getOrder(String orderId)
      throws IOException {
    return super.getOrder(orderId);
  }

  /**
   * Places a market order on the exchange.
   * <p>
   * A market order is executed immediately at the current market price. The order will be filled
   * as soon as possible, potentially across multiple price levels in the order book.
   * </p>
   *
   * @param marketOrder The market order to place, containing the instrument, side (buy/sell),
   *                    and quantity.
   * @return The order ID assigned by Coinbase Advanced Trade as a string.
   * @throws IOException If there is an error communicating with the Coinbase API or if the order
   *                     placement fails.
   */
  @Override
  public String placeMarketOrder(MarketOrder marketOrder) throws IOException {
    Object request = CoinbaseV3OrderRequests.marketOrderRequest(marketOrder);
    return CoinbaseAdapters.adaptCreatedOrderId(super.createOrder(request));
  }

  /**
   * Places a limit order on the exchange.
   * <p>
   * A limit order specifies a maximum price (for buys) or minimum price (for sells) at which
   * the order should be executed. The order will only be filled if the market price reaches
   * the specified limit price or better.
   * </p>
   *
   * @param limitOrder The limit order to place, containing the instrument, side (buy/sell),
   *                   quantity, and limit price.
   * @return The order ID assigned by Coinbase Advanced Trade as a string.
   * @throws IOException If there is an error communicating with the Coinbase API or if the order
   *                     placement fails.
   */
  @Override
  public String placeLimitOrder(LimitOrder limitOrder) throws IOException {
    Object request = CoinbaseV3OrderRequests.limitOrderRequest(limitOrder);
    return CoinbaseAdapters.adaptCreatedOrderId(super.createOrder(request));
  }

  /**
   * Places a stop order on the exchange.
   * <p>
   * A stop order becomes active when the market price reaches a specified stop price. Once
   * triggered, it behaves like a market order and is executed at the current market price.
   * Stop orders are commonly used for stop-loss or stop-entry strategies.
   * </p>
   *
   * @param stopOrder The stop order to place, containing the instrument, side (buy/sell),
   *                  quantity, and stop price.
   * @return The order ID assigned by Coinbase Advanced Trade as a string.
   * @throws IOException If there is an error communicating with the Coinbase API or if the order
   *                     placement fails.
   */
  @Override
  public String placeStopOrder(StopOrder stopOrder) throws IOException {
    Object request = CoinbaseV3OrderRequests.stopOrderRequest(stopOrder);
    return CoinbaseAdapters.adaptCreatedOrderId(super.createOrder(request));
  }

  /**
   * Verifies a limit order by previewing it without actually placing it.
   * <p>
   * This method uses the Coinbase order preview endpoint to validate the order parameters
   * and check if the order would be accepted. If the preview fails, a {@link RuntimeException}
   * is thrown with details about the failure.
   * </p>
   *
   * @param limitOrder The limit order to verify, containing the instrument, side, quantity,
   *                   and limit price.
   * @throws RuntimeException If the order preview fails, wrapping the underlying {@link IOException}.
   */
  @Override
  public void verifyOrder(LimitOrder limitOrder) {
    try {
      Object request = CoinbaseV3OrderRequests.limitOrderRequest(limitOrder);
      super.previewOrder(request);
    } catch (IOException e) {
      throw new RuntimeException("Failed to preview limit order", e);
    }
  }

  /**
   * Verifies a market order by previewing it without actually placing it.
   * <p>
   * This method uses the Coinbase order preview endpoint to validate the order parameters
   * and check if the order would be accepted. If the preview fails, a {@link RuntimeException}
   * is thrown with details about the failure.
   * </p>
   *
   * @param marketOrder The market order to verify, containing the instrument, side, and quantity.
   * @throws RuntimeException If the order preview fails, wrapping the underlying {@link IOException}.
   */
  @Override
  public void verifyOrder(MarketOrder marketOrder) {
    try {
      Object request = CoinbaseV3OrderRequests.marketOrderRequest(marketOrder);
      super.previewOrder(request);
    } catch (IOException e) {
      throw new RuntimeException("Failed to preview market order", e);
    }
  }

  /**
   * Modifies an existing limit order.
   * <p>
   * This method allows you to update the parameters of an existing limit order, such as
   * changing the price or quantity. The order must have a valid ID set via
   * {@link LimitOrder#setId(String)}.
   * </p>
   *
   * @param limitOrder The limit order to modify, containing the order ID and updated parameters
   *                   (price, quantity, etc.).
   * @return The order ID of the modified order (same as the input order's ID).
   * @throws IOException If there is an error communicating with the Coinbase API or if the order
   *                     modification fails.
   */
  @Override
  public String changeOrder(LimitOrder limitOrder) throws IOException {
    Object request = CoinbaseV3OrderRequests.editLimitOrderRequest(limitOrder);
    super.editOrder(request);
    return limitOrder.getId();
  }

  /**
   * Cancels an order using the provided cancellation parameters.
   * <p>
   * This method supports cancellation by order ID using {@link DefaultCancelOrderParamId}.
   * Other parameter types may be supported in the future (e.g., cancellation by client order ID).
   * </p>
   *
   * @param orderParams Cancellation parameters. Must be an instance of
   *                    {@link DefaultCancelOrderParamId} containing the order ID to cancel.
   * @return {@code true} if the order was successfully cancelled, {@code false} if the parameter
   *         type is not supported.
   * @throws IOException If there is an error communicating with the Coinbase API or if the
   *                     cancellation fails.
   */
  @Override
  public boolean cancelOrder(CancelOrderParams orderParams) throws IOException {
    if (orderParams instanceof DefaultCancelOrderParamId) {
      DefaultCancelOrderParamId byId = (DefaultCancelOrderParamId) orderParams;
      super.cancelOrderById(byId.getOrderId());
      return true;
    }
    // If other param types are added later (e.g., by clientOrderId), extend here
    return false;
  }

  /**
   * Cancels all open orders for the authenticated user.
   * <p>
   * This method retrieves all currently open orders and cancels them in a single batch operation.
   * If there are no open orders, an empty collection is returned without making an API call.
   * </p>
   *
   * @param orderParams Cancellation parameters (currently unused, but required by the interface).
   * @return A collection of order IDs that were cancelled. Returns an empty collection if there
   *         were no open orders to cancel.
   * @throws IOException If there is an error communicating with the Coinbase API or if the
   *                     cancellation fails.
   */
  @Override
  public Collection<String> cancelAllOrders(CancelAllOrders orderParams)
      throws IOException {
    OpenOrders openOrders = getOpenOrders();
    List<String> ids = new ArrayList<>();
    for (Order o : openOrders.getAllOpenOrders()) {
      if (o.getId() != null && !o.getId().isEmpty()) ids.add(o.getId());
    }
    if (ids.isEmpty()) return Collections.emptyList();
    super.cancelOrders(ids, null);
    return ids;
  }

}
